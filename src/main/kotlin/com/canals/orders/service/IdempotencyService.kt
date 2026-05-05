package com.canals.orders.service

import com.canals.orders.domain.IdempotencyKey
import com.canals.orders.domain.Order
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.exception.IdempotencyConflictException
import com.canals.orders.repository.IdempotencyKeyRepository
import com.canals.orders.repository.OrderRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest

/**
 * Idempotency for POST /orders.
 *
 * Contract (Stripe/AWS-style):
 *   * Header `Idempotency-Key: <opaque string up to 120 chars>`.
 *   * Same key + same body → return the cached response.
 *   * Same key + different body → 422 idempotency-conflict.
 *   * Missing key → request is processed without idempotency guarantees.
 *
 * Concurrency:
 *   * We insert and flush the idempotency row before creating the order. The
 *     unique PK on `idempotency_keys.key` then serialises racing requests
 *     before payment is charged or stock is decremented.
 *   * A loser of the unique-key race rolls back and reads the winner's cached
 *     response after the winner commits.
 */
@Service
class IdempotencyService(
    private val repo: IdempotencyKeyRepository,
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    data class IdempotentResult(val statusCode: Int, val body: Order)

    fun createOrder(
        idempotencyKey: String?,
        request: CreateOrderRequest,
    ): IdempotentResult {
        if (idempotencyKey.isNullOrBlank()) {
            // No key — process directly. The caller accepts that a retry
            // could double-charge.
            return IdempotentResult(201, orderService.create(request))
        }

        val requestHash = sha256(canonicaliseRequest(request))

        // Fast path: cached response exists.
        readCache(idempotencyKey, requestHash)?.let { return it }

        try {
            return transactionTemplate.execute {
                // Claim the key first. saveAndFlush forces the unique-key
                // conflict to happen before order creation or payment.
                val record =
                    repo.saveAndFlush(
                        IdempotencyKey(
                            key = idempotencyKey,
                            requestHash = requestHash,
                            responseStatus = 0,
                            responseBody = "",
                        ),
                    )

                val order = orderService.create(request)
                record.responseStatus = 201
                record.responseBody = order.id.toString()
                record.orderId = order.id

                IdempotentResult(201, order)
            }!!
        } catch (ex: DataIntegrityViolationException) {
            // Lost the unique-key race before creating an order. Return the
            // cached canonical response from the winner.
            log.warn(
                "Idempotency-Key {} hit a concurrent commit race; falling back to cache.",
                idempotencyKey,
            )
            return readCache(idempotencyKey, requestHash) ?: throw ex
        }
    }

    private fun readCache(
        key: String,
        requestHash: String,
    ): IdempotentResult? {
        val record = repo.findById(key).orElse(null) ?: return null
        if (record.requestHash != requestHash) {
            throw IdempotencyConflictException(key)
        }
        val orderId = record.orderId ?: return null
        val order =
            orderRepo.findByIdWithItems(orderId)
                ?: throw IllegalStateException("Cached order ${record.orderId} no longer exists")
        log.debug("Idempotency cache hit for key={}", key)
        return IdempotentResult(record.responseStatus, order)
    }

    /**
     * Stable serialisation for request hashing. Same logical request →
     * same bytes regardless of JSON field ordering or item order.
     */
    private fun canonicaliseRequest(request: CreateOrderRequest): String {
        val canonical =
            mapOf(
                "customerId" to request.customerId.toString(),
                "shippingAddress" to mapOf("line" to request.shippingAddress.line.trim()),
                "items" to
                    request.items
                        .sortedBy { it.productId.toString() }
                        .map { mapOf("productId" to it.productId.toString(), "quantity" to it.quantity) },
                "payment" to mapOf("cardNumber" to request.payment.normalizedCardNumber),
            )
        return objectMapper.writeValueAsString(canonical)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
