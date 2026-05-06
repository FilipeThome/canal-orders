package com.canals.orders.unit.external

import com.canals.orders.external.MockPaymentService
import com.canals.orders.external.PaymentRequest
import com.canals.orders.external.PaymentResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MockPaymentServiceTest {
    private val service = MockPaymentService(failureRate = 0.0, latencyMs = 0L)

    @Test
    fun `card ending 0002 is always declined`() {
        val request = PaymentRequest("4111111111110002", BigDecimal("99.99"), "USD", "test")

        val result = service.charge(request)

        assertThat(result).isInstanceOf(PaymentResult.Declined::class.java)
        result as PaymentResult.Declined
        assertThat(result.reason).isEqualTo("Card declined by issuer")
        assertThat(result.cardLast4).isEqualTo("0002")
    }

    @Test
    fun `normal card with zero failure rate is approved`() {
        val request = PaymentRequest("4111111111111111", BigDecimal("49.99"), "USD", "test")

        val result = service.charge(request)

        assertThat(result).isInstanceOf(PaymentResult.Approved::class.java)
        result as PaymentResult.Approved
        assertThat(result.cardLast4).isEqualTo("1111")
        assertThat(result.paymentId).startsWith("pay_")
    }

    @Test
    fun `failure rate of 1 declines every charge`() {
        val declining = MockPaymentService(failureRate = 1.0, latencyMs = 0L)
        val request = PaymentRequest("4111111111111111", BigDecimal("10.00"), "USD", "test")

        repeat(5) {
            assertThat(declining.charge(request)).isInstanceOf(PaymentResult.Declined::class.java)
        }
    }

    @Test
    fun `card last 4 digits are reflected in approved result`() {
        val request = PaymentRequest("5500005555555559", BigDecimal("200.00"), "USD", "order test")

        val result = service.charge(request) as PaymentResult.Approved

        assertThat(result.cardLast4).isEqualTo("5559")
    }

    @Test
    fun `approved result contains a non-blank payment id`() {
        val request = PaymentRequest("4111111111111111", BigDecimal("1.00"), "USD", "test")

        val result = service.charge(request) as PaymentResult.Approved

        assertThat(result.paymentId).isNotBlank()
    }
}
