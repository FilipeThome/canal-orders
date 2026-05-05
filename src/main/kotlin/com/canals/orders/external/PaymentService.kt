package com.canals.orders.external

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

data class PaymentRequest(
    val cardNumber: String,
    val amount: BigDecimal,
    val currency: String,
    val description: String,
)

sealed class PaymentResult {
    data class Approved(val paymentId: String, val cardLast4: String) : PaymentResult()

    data class Declined(val reason: String, val cardLast4: String) : PaymentResult()
}

/**
 * Charge a credit card. In production this is Stripe / Adyen / Braintree.
 *
 * IMPORTANT: We never log the full PAN. Only mask-safe data crosses the
 * logging boundary.
 */
interface PaymentService {
    fun charge(request: PaymentRequest): PaymentResult
}

@Component
class MockPaymentService(
    @Value("\${external.payment.failure-rate:0.0}") private val failureRate: Double,
    @Value("\${external.payment.latency-ms:0}") private val latencyMs: Long,
) : PaymentService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun charge(request: PaymentRequest): PaymentResult {
        val cardLast4 = request.cardNumber.takeLast(4)
        val masked = "****-****-****-$cardLast4"
        log.info(
            "Charging card {} amount={} {} description='{}'",
            masked,
            request.amount,
            request.currency,
            request.description,
        )

        if (latencyMs > 0) {
            // Simulate gateway round-trip; non-blocking would use WebClient,
            // but this mock stays synchronous to mirror a typical sync API.
            Thread.sleep(latencyMs)
        }

        // Test card "0000 0000 0000 0002" always declines (mirrors Stripe's
        // standard test cards). Otherwise, optionally fail by configured rate.
        val forcedDecline = request.cardNumber.endsWith("0002")
        val randomDecline = failureRate > 0 && Math.random() < failureRate

        return when {
            forcedDecline -> PaymentResult.Declined("Card declined by issuer", cardLast4)
            randomDecline -> PaymentResult.Declined("Insufficient funds", cardLast4)
            else -> PaymentResult.Approved("pay_${UUID.randomUUID()}", cardLast4)
        }
    }
}
