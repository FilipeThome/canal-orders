package com.canals.orders.unit.service

import com.canals.orders.domain.IdempotencyKey
import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderStatus
import com.canals.orders.dto.AddressDto
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.dto.OrderItemDto
import com.canals.orders.dto.PaymentDto
import com.canals.orders.exception.IdempotencyConflictException
import com.canals.orders.repository.IdempotencyKeyRepository
import com.canals.orders.repository.OrderRepository
import com.canals.orders.service.IdempotencyOrderCreatorService
import com.canals.orders.service.IdempotencyService
import com.canals.orders.service.IdempotentResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Optional
import java.util.UUID

class IdempotencyServiceTest {
    private val keyRepo = mockk<IdempotencyKeyRepository>()
    private val orderRepo = mockk<OrderRepository>()
    private val idempotencyOrderCreatorService = mockk<IdempotencyOrderCreatorService>()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val service =
        IdempotencyService(
            idempotencyKeyRepository = keyRepo,
            orderRepository = orderRepo,
            idempotencyOrderCreatorService = idempotencyOrderCreatorService,
            objectMapper = objectMapper,
        )

    private val customerId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private fun request() =
        CreateOrderRequest(
            customerId = customerId,
            shippingAddress = AddressDto("123 Main St"),
            items = listOf(OrderItemDto(productId = productId, quantity = 1)),
            payment = PaymentDto("4111111111111111"),
        )

    private fun newOrder() =
        Order(
            id = UUID.randomUUID(),
            customerId = customerId,
            warehouseId = UUID.randomUUID(),
            status = OrderStatus.PAID,
            shipAddressLine = "123 Main St",
            shipLatitude = BigDecimal("40.000000"),
            shipLongitude = BigDecimal("-74.000000"),
            totalAmount = BigDecimal("99.99"),
        )

    // Mirrors the private hash logic in IdempotencyService for white-box testing
    private fun hashOf(req: CreateOrderRequest): String {
        val canonical =
            mapOf(
                "customerId" to req.customerId.toString(),
                "shippingAddress" to mapOf("line" to req.shippingAddress.addressLine.trim()),
                "items" to
                    req.items
                        .sortedBy { it.productId.toString() }
                        .map { mapOf("productId" to it.productId.toString(), "quantity" to it.quantity) },
                "payment" to mapOf("cardNumber" to req.payment.normalizedCardNumber),
            )
        val json = objectMapper.writeValueAsString(canonical)
        val bytes = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `createOrder with key and empty cache delegates to IdempotencyOrderCreator`() {
        val key = "key-abc"
        val req = request()
        val order = newOrder()
        val expected = IdempotentResult(201, order)
        every { keyRepo.findById(key) } returns Optional.empty()
        every { idempotencyOrderCreatorService.create(key, any(), req) } returns expected

        val result = service.createOrder(key, req)

        assertThat(result).isEqualTo(expected)
        verify(exactly = 1) { idempotencyOrderCreatorService.create(key, any(), req) }
    }

    @Test
    fun `createOrder with key and cached response returns cached order without calling creator`() {
        val key = "key-cached"
        val req = request()
        val order = newOrder()
        val record =
            IdempotencyKey(
                key = key,
                requestHash = hashOf(req),
                responseStatus = 201,
                responseBody = order.id.toString(),
                orderId = order.id,
            )
        every { keyRepo.findById(key) } returns Optional.of(record)
        every { orderRepo.findByIdWithItems(order.id) } returns order

        val result = service.createOrder(key, req)

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
        verify(exactly = 0) { idempotencyOrderCreatorService.create(any(), any(), any()) }
    }

    @Test
    fun `createOrder with key and mismatched hash throws IdempotencyConflictException`() {
        val key = "key-conflict"
        val record =
            IdempotencyKey(
                key = key,
                requestHash = "completely-different-hash",
                responseStatus = 201,
                responseBody = "...",
                orderId = UUID.randomUUID(),
            )
        every { keyRepo.findById(key) } returns Optional.of(record)

        assertThatThrownBy { service.createOrder(key, request()) }
            .isInstanceOf(IdempotencyConflictException::class.java)
            .hasMessageContaining(key)
    }

    @Test
    fun `createOrder falls back to cache after concurrent unique-key violation`() {
        val key = "key-race"
        val req = request()
        val order = newOrder()
        val record =
            IdempotencyKey(
                key = key,
                requestHash = hashOf(req),
                responseStatus = 201,
                responseBody = order.id.toString(),
                orderId = order.id,
            )
        every { keyRepo.findById(key) } returnsMany
            listOf(
                Optional.empty(), // first read-cache call: no entry yet
                Optional.of(record), // second read-cache call in catch block: winner has committed
            )
        every { idempotencyOrderCreatorService.create(key, any(), req) } throws
            DataIntegrityViolationException("unique constraint")
        every { orderRepo.findByIdWithItems(order.id) } returns order

        val result = service.createOrder(key, req)

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
        verify(exactly = 1) { idempotencyOrderCreatorService.create(key, any(), req) }
    }

    @Test
    fun `createOrder rethrows DataIntegrityViolationException when race fallback cache is still empty`() {
        val key = "key-race-no-winner"
        val req = request()
        val ex = DataIntegrityViolationException("unique constraint")
        every { keyRepo.findById(key) } returns Optional.empty()
        every { idempotencyOrderCreatorService.create(key, any(), req) } throws ex

        assertThatThrownBy { service.createOrder(key, req) }
            .isSameAs(ex)
    }

    @Test
    fun `createOrder returns null from cache when record exists but orderId is not yet set`() {
        val key = "key-in-flight"
        val req = request()
        val order = newOrder()
        val pendingRecord =
            IdempotencyKey(
                key = key,
                requestHash = hashOf(req),
                responseStatus = 0,
                responseBody = "",
                orderId = null,
            )
        val completedRecord =
            IdempotencyKey(
                key = key,
                requestHash = hashOf(req),
                responseStatus = 201,
                responseBody = order.id.toString(),
                orderId = order.id,
            )
        every { keyRepo.findById(key) } returnsMany
            listOf(
                Optional.of(pendingRecord), // first read-cache: in-flight, orderId=null → null
                Optional.of(completedRecord), // not reached in this test path
            )
        every { idempotencyOrderCreatorService.create(key, any(), req) } returns IdempotentResult(201, order)

        val result = service.createOrder(key, req)

        assertThat(result.statusCode).isEqualTo(201)
        verify(exactly = 1) { idempotencyOrderCreatorService.create(key, any(), req) }
    }

    @Test
    fun `readCache throws IllegalStateException when cached orderId references a deleted order`() {
        val key = "key-orphan"
        val req = request()
        val orphanId = UUID.randomUUID()
        val record =
            IdempotencyKey(
                key = key,
                requestHash = hashOf(req),
                responseStatus = 201,
                responseBody = orphanId.toString(),
                orderId = orphanId,
            )
        every { keyRepo.findById(key) } returns Optional.of(record)
        every { orderRepo.findByIdWithItems(orphanId) } returns null

        assertThatThrownBy { service.createOrder(key, req) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(orphanId.toString())
    }
}
