package com.canals.orders.repository

import com.canals.orders.domain.Warehouse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WarehouseRepository : JpaRepository<Warehouse, UUID> {
    @Query(name = "Warehouse.findWithStock")
    fun findWithStock(
        @Param("productId") productId: UUID,
        @Param("qty") qty: Int,
    ): List<Warehouse>
}
