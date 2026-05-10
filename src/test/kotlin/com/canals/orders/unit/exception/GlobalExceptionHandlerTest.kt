package com.canals.orders.unit.exception

import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.exception.DuplicateProductInOrderException
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.exception.IdempotencyConflictException
import com.canals.orders.exception.NoEligibleWarehouseException
import com.canals.orders.exception.OrderNotFoundException
import com.canals.orders.exception.PaymentFailedException
import com.canals.orders.exception.ProductNotFoundException
import com.canals.orders.exception.ProductsNotFoundException
import com.canals.orders.exception.WarehouseNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `customer not found maps to 404`() {
        val ex = CustomerNotFoundException(UUID.randomUUID())
        val response = handler.onNotFound(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.type.toString()).contains("not-found")
        assertThat(response.body?.detail).contains("not found")
    }

    @Test
    fun `products not found maps to 404`() {
        val ex = ProductsNotFoundException(listOf(UUID.randomUUID(), UUID.randomUUID()))
        val response = handler.onNotFound(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `product not found maps to 404`() {
        val id = UUID.randomUUID()
        val ex = ProductNotFoundException(id)
        val response = handler.onNotFound(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.detail).contains(id.toString())
    }

    @Test
    fun `warehouse not found maps to 404`() {
        val id = UUID.randomUUID()
        val ex = WarehouseNotFoundException(id)
        val response = handler.onNotFound(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.detail).contains(id.toString())
    }

    @Test
    fun `order not found maps to 404`() {
        val id = UUID.randomUUID()
        val ex = OrderNotFoundException(id)
        val response = handler.onNotFound(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.detail).contains(id.toString())
    }

    @Test
    fun `no eligible warehouse maps to 422`() {
        val ex = NoEligibleWarehouseException("No warehouse can fulfil this order")
        val response = handler.onNoWarehouse(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(response.body?.type.toString()).contains("no-eligible-warehouse")
    }

    @Test
    fun `payment failed maps to 402`() {
        val ex = PaymentFailedException("Card declined")
        val response = handler.onPaymentFailed(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYMENT_REQUIRED)
        assertThat(response.body?.type.toString()).contains("payment-failed")
        assertThat(response.body?.detail).contains("Card declined")
    }

    @Test
    fun `idempotency conflict maps to 422`() {
        val ex = IdempotencyConflictException("my-key")
        val response = handler.onIdempotencyConflict(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(response.body?.type.toString()).contains("idempotency-conflict")
    }

    @Test
    fun `duplicate products maps to 400`() {
        val ex = DuplicateProductInOrderException(listOf(UUID.randomUUID()))
        val response = handler.onDuplicateProducts(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `unexpected exception maps to 500 without leaking internal details`() {
        val ex = RuntimeException("internal db error with sensitive info")
        val response = handler.onUnexpected(ex)
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.detail).doesNotContain("db error")
        assertThat(response.body?.detail).doesNotContain("sensitive")
    }

    @Test
    fun `all problem details include a typed URI`() {
        val handlers =
            listOf(
                handler.onNotFound(CustomerNotFoundException(UUID.randomUUID())),
                handler.onNoWarehouse(NoEligibleWarehouseException("x")),
                handler.onPaymentFailed(PaymentFailedException("y")),
                handler.onIdempotencyConflict(IdempotencyConflictException("z")),
            )
        handlers.forEach { response ->
            assertThat(response.body?.type.toString()).startsWith("https://canals.example/errors/")
        }
    }
}
