package com.canals.orders.unit.service

import com.canals.orders.domain.IdempotencyKey
import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderStatus
import com.canals.orders.dto.AddressDto
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.dto.OrderItemDto
import com.canals.orders.dto.PaymentDto
import com.canals.orders.repository.IdempotencyKeyRepository
import com.canals.orders.service.IdempotencyOrderCreatorService
import com.canals.orders.service.OrderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class IdempotencyOrderCreatorServiceTest {
    private val keyRepo = mockk<IdempotencyKeyRepository>()
    private val orderService = mockk<OrderService>()

    private val service = IdempotencyOrderCreatorService(keyRepo, orderService)

    private val customerId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val idempotencyKey = "test-key-123"
    private val requestHash = "abc123hash"

    private fun request() =
        CreateOrderRequest(
            customerId = customerId,
            shippingAddress = AddressDto("123 Main St"),
            items = listOf(OrderItemDto(productId = productId, quantity = 2)),
            payment = PaymentDto("4111111111111111"),
        )

    private fun order(id: UUID = UUID.randomUUID()) =
        Order(
            id = id,
            customerId = customerId,
            warehouseId = UUID.randomUUID(),
            status = OrderStatus.PAID,
            shipAddressLine = "123 Main St",
            shipLatitude = BigDecimal("40.000000"),
            shipLongitude = BigDecimal("-74.000000"),
            totalAmount = BigDecimal("99.99"),
        )

    @Test
    fun `create returns IdempotentResult with 201 and the created order`() {
        val order = order()
        val record = IdempotencyKey(key = idempotencyKey, requestHash = requestHash, responseStatus = 0, responseBody = "")
        every { keyRepo.saveAndFlush(any()) } returns record
        every { orderService.create(any()) } returns order

        val result = service.create(idempotencyKey, requestHash, request())

        assertThat(result.statusCode).isEqualTo(201)
        assertThat(result.body).isEqualTo(order)
    }

    @Test
    fun `create saves idempotency key with initial pending state before order creation`() {
        val order = order()
        val keySlot = slot<IdempotencyKey>()
        val record = IdempotencyKey(key = idempotencyKey, requestHash = requestHash, responseStatus = 0, responseBody = "")
        every { keyRepo.saveAndFlush(capture(keySlot)) } returns record
        every { orderService.create(any()) } returns order

        service.create(idempotencyKey, requestHash, request())

        with(keySlot.captured) {
            assertThat(key).isEqualTo(idempotencyKey)
            assertThat(this.requestHash).isEqualTo(requestHash)
            assertThat(responseStatus).isEqualTo(0)
            assertThat(responseBody).isEqualTo("")
            assertThat(orderId).isNull()
        }
    }

    @Test
    fun `create updates record with order id and response status after order creation`() {
        val order = order()
        val record = IdempotencyKey(key = idempotencyKey, requestHash = requestHash, responseStatus = 0, responseBody = "")
        every { keyRepo.saveAndFlush(any()) } returns record
        every { orderService.create(any()) } returns order

        service.create(idempotencyKey, requestHash, request())

        assertThat(record.responseStatus).isEqualTo(201)
        assertThat(record.responseBody).isEqualTo(order.id.toString())
        assertThat(record.orderId).isEqualTo(order.id)
    }

    @Test
    fun `create propagates exception from orderService without updating record`() {
        val record = IdempotencyKey(key = idempotencyKey, requestHash = requestHash, responseStatus = 0, responseBody = "")
        every { keyRepo.saveAndFlush(any()) } returns record
        every { orderService.create(any()) } throws RuntimeException("payment failed")

        assertThatThrownBy { service.create(idempotencyKey, requestHash, request()) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("payment failed")

        assertThat(record.responseStatus).isEqualTo(0)
        assertThat(record.orderId).isNull()
    }

    @Test
    fun `create passes the request to orderService unchanged`() {
        val req = request()
        val order = order()
        val record = IdempotencyKey(key = idempotencyKey, requestHash = requestHash, responseStatus = 0, responseBody = "")
        every { keyRepo.saveAndFlush(any()) } returns record
        every { orderService.create(req) } returns order

        service.create(idempotencyKey, requestHash, req)

        verify(exactly = 1) { orderService.create(req) }
    }
}
