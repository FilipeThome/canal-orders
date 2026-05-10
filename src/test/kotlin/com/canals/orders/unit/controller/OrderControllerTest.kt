package com.canals.orders.unit.controller

import com.canals.orders.controller.OrderController
import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderStatus
import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.exception.NoEligibleWarehouseException
import com.canals.orders.exception.PaymentFailedException
import com.canals.orders.service.IdempotencyService
import com.canals.orders.service.IdempotentResult
import com.canals.orders.service.OrderService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(OrderController::class)
@Import(GlobalExceptionHandler::class)
class OrderControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var idempotencyService: IdempotencyService

    @MockkBean
    lateinit var orderService: OrderService

    private fun mockOrder(id: UUID = UUID.randomUUID()) =
        Order(
            id = id,
            customerId = UUID.randomUUID(),
            warehouseId = UUID.randomUUID(),
            status = OrderStatus.PAID,
            shipAddressLine = "123 Main St",
            shipLatitude = BigDecimal("40.712776"),
            shipLongitude = BigDecimal("-74.005974"),
            totalAmount = BigDecimal("99.99"),
            currency = "USD",
            paymentId = "pay_test123",
            cardLast4 = "1111",
        )

    private fun validBody(
        customerId: UUID = UUID.randomUUID(),
        productId: UUID = UUID.randomUUID(),
    ) = mapOf(
        "customerId" to customerId.toString(),
        "shippingAddress" to mapOf("addressLine" to "123 Main St, New York, NY"),
        "items" to listOf(mapOf("productId" to productId.toString(), "quantity" to 2)),
        "payment" to mapOf("cardNumber" to "4111111111111111"),
    )

    @Test
    fun `GET orders returns 200 with empty list`() {
        every { orderService.listAll() } returns emptyList()

        mockMvc.get("/orders").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `GET orders returns mapped order responses`() {
        val order = mockOrder()
        every { orderService.listAll() } returns listOf(order)

        mockMvc.get("/orders").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(order.id.toString()) }
            jsonPath("$[0].status") { value("PAID") }
        }
    }

    @Test
    fun `POST orders without Idempotency-Key header returns 400`() {
        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST orders with Idempotency-Key header forwards key to service`() {
        val order = mockOrder()
        every { idempotencyService.createOrder("my-key-123", any()) } returns IdempotentResult(201, order)

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
            header("Idempotency-Key", "my-key-123")
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `POST orders with missing customerId returns 400`() {
        val body =
            mapOf(
                "shippingAddress" to mapOf("addressLine" to "123 Main St"),
                "items" to listOf(mapOf("productId" to UUID.randomUUID().toString(), "quantity" to 1)),
                "payment" to mapOf("cardNumber" to "4111111111111111"),
            )

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST orders with empty items list returns 400`() {
        val body =
            mapOf(
                "customerId" to UUID.randomUUID().toString(),
                "shippingAddress" to mapOf("addressLine" to "123 Main St"),
                "items" to emptyList<Any>(),
                "payment" to mapOf("cardNumber" to "4111111111111111"),
            )

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST orders with invalid card number returns 400`() {
        val body =
            validBody().toMutableMap().also {
                it["payment"] = mapOf("cardNumber" to "not-a-card")
            }

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST orders with malformed JSON returns 400`() {
        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = "{ not valid json }"
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST orders returns 404 when customer does not exist`() {
        every { idempotencyService.createOrder(any(), any()) } throws CustomerNotFoundException(UUID.randomUUID())

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST orders returns 402 when payment is declined`() {
        every { idempotencyService.createOrder(any(), any()) } throws PaymentFailedException("Insufficient funds")

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isPaymentRequired() }
        }
    }

    @Test
    fun `POST orders returns 422 when no warehouse can fulfil the order`() {
        every { idempotencyService.createOrder(any(), any()) } throws NoEligibleWarehouseException("No stock")

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `POST orders error response follows RFC 7807 problem detail structure`() {
        every { idempotencyService.createOrder(any(), any()) } throws PaymentFailedException("declined")

        mockMvc.post("/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
            header("Idempotency-Key", "test-key")
        }.andExpect {
            status { isPaymentRequired() }
            jsonPath("$.type") { isString() }
            jsonPath("$.title") { isString() }
            jsonPath("$.detail") { isString() }
        }
    }
}
