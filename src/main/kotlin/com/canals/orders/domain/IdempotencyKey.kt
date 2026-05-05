package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKey(
    @Id
    @Column(name = "key", nullable = false, length = 120)
    val key: String,
    @Column(name = "request_hash", nullable = false, length = 64)
    val requestHash: String,
    @Column(name = "response_status", nullable = false)
    var responseStatus: Int,
    // Schema declares TEXT; we hint a generous length so JPA validation passes.
    @Column(name = "response_body", nullable = false, length = 10_000_000)
    var responseBody: String,
    @Column(name = "order_id")
    var orderId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
