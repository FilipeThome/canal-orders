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
import com.canals.orders.service.IdempotencyService
import com.canals.orders.service.OrderService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Optional
import java.util.UUID

class IdempotencyServiceTest {
    private val keyRepo = mockk<IdempotencyKeyRepository>()
    private val orderRepo = mockk<OrderRepository>()
    private val orderService = mockk<OrderService>()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    // Synchronous transaction manager: runs callbacks inline without real transactions
    private val txManager =
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?) = SimpleTransactionStatus()

            override fun commit(status: TransactionStatus) {}

            override fun rollback(status: TransactionStatus) {}
        }

    private val service =
        IdempotencyService(
            idempotencyKeyRepository = keyRepo,
            orderRepository = orderRepo,
            orderService = orderService,
            objectMapper = objectMapper,
            transactionManager = txManager,
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
    fun `createOrder without key bypasses idempotency and returns 201`() {
        val order = newOrder()
        every { orderService.create(any()) } returns order

        val result = service.createOrder(null, request())

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
        verify(exactly = 0) { keyRepo.findById(any<String>()) }
    }

    @Test
    fun `createOrder with blank key bypasses idempotency`() {
        every { orderService.create(any()) } returns newOrder()

        service.createOrder("   ", request())

        verify(exactly = 0) { keyRepo.findById(any<String>()) }
    }

    @Test
    fun `createOrder with key and empty cache creates the order and returns 201`() {
        val key = "key-abc"
        val req = request()
        val order = newOrder()
        val record = IdempotencyKey(key = key, requestHash = hashOf(req), responseStatus = 0, responseBody = "")
        every { keyRepo.findById(key) } returns Optional.empty()
        every { keyRepo.saveAndFlush(any()) } returns record
        every { orderService.create(req) } returns order

        val result = service.createOrder(key, req)

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
        verify(exactly = 1) { orderService.create(req) }
    }

    @Test
    fun `createOrder with key and cached response returns cached order without calling orderService`() {
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
        verify(exactly = 0) { orderService.create(any()) }
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
        every { keyRepo.saveAndFlush(any()) } throws DataIntegrityViolationException("unique constraint")
        every { orderRepo.findByIdWithItems(order.id) } returns order

        val result = service.createOrder(key, req)

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
        verify(exactly = 0) { orderService.create(any()) }
    }
}
