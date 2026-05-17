package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Embeddable
data class WarehouseStockId(
    @Column(name = "warehouse_id", nullable = false)
    val warehouseId: UUID,
    @Column(name = "product_id", nullable = false)
    val productId: UUID,
) : Serializable

@Entity
@Table(name = "warehouse_stock")
class WarehouseStock(
    @EmbeddedId
    val id: WarehouseStockId,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    fun decrement(qty: Int) {
        check(qty <= quantity) { "Cannot decrement $qty from available $quantity for product ${id.productId}" }
        quantity -= qty
        updatedAt = OffsetDateTime.now()
    }
}
