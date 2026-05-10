package com.canals.orders.service

import com.canals.orders.domain.IdempotencyKey
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.repository.IdempotencyKeyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IdempotencyOrderCreatorService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val orderService: OrderService,
) {
    @Transactional
    fun create(
        idempotencyKey: String,
        requestHash: String,
        request: CreateOrderRequest,
    ): IdempotentResult {
        val record =
            idempotencyKeyRepository.saveAndFlush(
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

        return IdempotentResult(201, order)
    }
}
