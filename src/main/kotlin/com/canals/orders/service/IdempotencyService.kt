package com.canals.orders.service

import com.canals.orders.domain.Order
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.exception.IdempotencyConflictException
import com.canals.orders.repository.IdempotencyKeyRepository
import com.canals.orders.repository.OrderRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.security.MessageDigest

data class IdempotentResult(val statusCode: Int, val body: Order)

/**
 * Idempotency for POST /orders (Stripe/AWS-style).
 *
 * Same key + same body  → return cached response.
 * Same key + different body → 422 idempotency-conflict.
 * Missing key → 400 Bad Request.
 *
 * Concurrency: idempotency row is inserted and flushed before order creation.
 * The unique PK serialises racing requests. The loser catches the constraint
 * violation and reads the winner's cached response after commit.
 */
@Service
class IdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val orderRepository: OrderRepository,
    private val idempotencyOrderCreatorService: IdempotencyOrderCreatorService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createOrder(
        idempotencyKey: String,
        request: CreateOrderRequest,
    ): IdempotentResult {
        val requestHash = sha256(canonicaliseRequest(request))

        readCache(idempotencyKey, requestHash)?.let { return it }

        return try {
            idempotencyOrderCreatorService.create(idempotencyKey, requestHash, request)
        } catch (ex: DataIntegrityViolationException) {
            log.warn("Idempotency-Key {} hit a concurrent commit race; falling back to cache.", idempotencyKey)
            readCache(idempotencyKey, requestHash) ?: throw ex
        }
    }

    private fun readCache(
        key: String,
        requestHash: String,
    ): IdempotentResult? {
        val record = idempotencyKeyRepository.findById(key).orElse(null) ?: return null
        if (record.requestHash != requestHash) throw IdempotencyConflictException(key)
        val orderId = record.orderId ?: return null
        val order =
            orderRepository.findByIdWithItems(orderId)
                ?: throw IllegalStateException("Cached order $orderId no longer exists")
        log.debug("Idempotency cache hit for key={}", key)
        return IdempotentResult(record.responseStatus, order)
    }

    private fun canonicaliseRequest(request: CreateOrderRequest): String {
        val canonical =
            buildMap {
                put("customerId", request.customerId.toString())
                put("shippingAddress", mapOf("line" to request.shippingAddress.addressLine.trim()))
                put(
                    "items",
                    request.items
                        .sortedBy { it.productId.toString() }
                        .map { mapOf("productId" to it.productId.toString(), "quantity" to it.quantity) },
                )
                put("payment", mapOf("cardNumber" to request.payment.normalizedCardNumber))
            }
        return objectMapper.writeValueAsString(canonical)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}
