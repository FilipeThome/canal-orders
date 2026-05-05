package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "order_items")
class OrderItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    val order: Order,
    @Column(name = "product_id", nullable = false)
    val productId: UUID,
    @Column(name = "product_name", nullable = false, length = 200)
    val productName: String,
    @Column(name = "quantity", nullable = false)
    val quantity: Int,
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    val unitPrice: BigDecimal,
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
)
