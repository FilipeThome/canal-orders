package com.canals.orders.repository

import com.canals.orders.domain.Warehouse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WarehouseRepository : JpaRepository<Warehouse, UUID> {
    /**
     * Returns every warehouse that carries ALL requested products with at least
     * the required quantity each — in a single round-trip.
     *
     * Before: O(P) queries (one per product) + in-memory set-intersection.
     * After : O(1) query — the DB does the intersection via GROUP BY / HAVING.
     *
     * Postgres-specific: uses parallel UNNEST to turn two comma-separated
     * strings into a (product_id, min_qty) relation without a temp table.
     */
    @Query(
        nativeQuery = true,
        value = """
            WITH required AS (
                SELECT CAST(unnest(string_to_array(:productIds, ',')) AS uuid)    AS product_id,
                       CAST(unnest(string_to_array(:quantities,  ',')) AS integer) AS min_qty
            )
            SELECT w.id, w.code, w.name, w.latitude, w.longitude, w.address, w.created_at
            FROM   warehouses w
            WHERE  w.id IN (
                SELECT ws.warehouse_id
                FROM   warehouse_stock ws
                JOIN   required r
                       ON ws.product_id  = r.product_id
                      AND ws.quantity   >= r.min_qty
                GROUP  BY ws.warehouse_id
                HAVING COUNT(DISTINCT ws.product_id) = :productCount
            )
        """,
    )
    fun findWarehousesWithSufficientStock(
        @Param("productIds") productIds: String,
        @Param("quantities") quantities: String,
        @Param("productCount") productCount: Long,
    ): List<Warehouse>
}
