package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.NamedQuery
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "warehouses")
@NamedQuery(
    name = "Warehouse.findWithStock",
    query = """
        SELECT w FROM Warehouse w
        WHERE EXISTS (
            SELECT s FROM WarehouseStock s
            WHERE s.id.warehouseId = w.id
              AND s.id.productId = :productId
              AND s.quantity >= :qty
        )
    """,
)
class Warehouse(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
    @Column(name = "code", nullable = false, unique = true, length = 32)
    val code: String,
    @Column(name = "name", nullable = false, length = 120)
    val name: String,
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    val latitude: BigDecimal,
    @Column(name = "longitude", nullable = false, precision = 10, scale = 6)
    val longitude: BigDecimal,
    @Column(name = "address", nullable = false, length = 300)
    val address: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
