package com.canals.orders.repository

import com.canals.orders.domain.WarehouseStock
import com.canals.orders.domain.WarehouseStockId
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WarehouseStockRepository : JpaRepository<WarehouseStock, WarehouseStockId> {
    /**
     * Acquire a row-level write lock on stock rows for `(warehouse, products)`,
     * ordered deterministically by product_id to avoid deadlocks when two
     * concurrent transactions lock the same set of rows in different orders.
     *
     * The "innodb_lock_wait_timeout"-style hint isn't relevant on Postgres,
     * but we set a reasonable JPA timeout in case the WAL is contended.
     *
     * Use this immediately before decrementing stock, inside the same Tx
     * that creates the order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query(
        """
        SELECT s FROM WarehouseStock s
        WHERE s.id.warehouseId = :warehouseId
          AND s.id.productId IN :productIds
        ORDER BY s.id.productId
        """,
    )
    fun lockStockForUpdate(
        @Param("warehouseId") warehouseId: UUID,
        @Param("productIds") productIds: Collection<UUID>,
    ): List<WarehouseStock>
}
