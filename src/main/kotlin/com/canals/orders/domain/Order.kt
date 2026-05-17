package com.canals.orders.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Order aggregate root.
 *
 * Shipping address is denormalised — orders are immutable historical records.
 * Card details are NEVER stored; only the last 4 digits for customer-support.
 * Postgres `order_status` enum is mapped via @JdbcTypeCode(NAMED_ENUM).
 */
@Entity
@Table(name = "orders")
class Order(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(name = "warehouse_id", nullable = false)
    val warehouseId: UUID,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "order_status")
    var status: OrderStatus,
    @Column(name = "ship_address_line", nullable = false, length = 300)
    val shipAddressLine: String,
    @Column(name = "ship_latitude", nullable = false, precision = 9, scale = 6)
    val shipLatitude: BigDecimal,
    @Column(name = "ship_longitude", nullable = false, precision = 10, scale = 6)
    val shipLongitude: BigDecimal,
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    val totalAmount: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "USD",
    @Column(name = "payment_id", length = 80)
    var paymentId: String? = null,
    @Column(name = "card_last4", length = 4)
    var cardLast4: String? = null,
    @Column(name = "paid_at")
    var paidAt: OffsetDateTime? = null,
    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    @OneToMany(
        mappedBy = "order",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    val items: MutableList<OrderItem> = mutableListOf(),
) {
    fun markPaid(
        paymentId: String,
        cardLast4: String,
    ) {
        val now = OffsetDateTime.now()
        status = OrderStatus.PAID
        this.paymentId = paymentId
        this.cardLast4 = cardLast4
        paidAt = now
        updatedAt = now
    }

    fun markFailed(reason: String) {
        status = OrderStatus.PAYMENT_FAILED
        failureReason = reason
        updatedAt = OffsetDateTime.now()
    }

    fun transitionTo(newStatus: OrderStatus) {
        status = newStatus
        updatedAt = OffsetDateTime.now()
    }
}
