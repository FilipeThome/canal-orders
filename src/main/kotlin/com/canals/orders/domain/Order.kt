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
 * Aggregate root for an order.
 *
 * Implementation notes:
 *   * The shipping address is intentionally denormalised onto the order — an
 *     order is an immutable historical record. If the customer changes
 *     their address book later, past orders MUST keep the address that was
 *     in effect when the order was placed.
 *   * Card details are NEVER stored. Only the masked last 4 digits, kept
 *     here so customer support can reference "the order paid with card
 *     ending in 1234" without ever touching PCI data.
 *   * The Postgres `order_status` enum is mapped via @JdbcTypeCode(NAMED_ENUM).
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
)
