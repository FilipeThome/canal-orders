package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "products")
class Product(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
    @Column(name = "sku", nullable = false, unique = true, length = 64)
    val sku: String,
    @Column(name = "name", nullable = false, length = 200)
    val name: String,
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    val unitPrice: BigDecimal,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
