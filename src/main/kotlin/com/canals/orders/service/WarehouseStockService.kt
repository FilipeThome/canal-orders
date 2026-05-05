package com.canals.orders.service

import com.canals.orders.domain.WarehouseStock
import com.canals.orders.repository.WarehouseStockRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class WarehouseStockService(
    private val warehouseStockRepo: WarehouseStockRepository,
) {
    fun tryLock(
        warehouseId: UUID,
        productToQty: Map<UUID, Int>,
    ): Map<UUID, WarehouseStock>? {
        val locked =
            warehouseStockRepo
                .lockStockForUpdate(warehouseId, productToQty.keys)
                .associateBy { it.id.productId }
        val feasible = productToQty.all { (productId, qty) -> (locked[productId]?.quantity ?: 0) >= qty }
        return if (feasible) locked else null
    }

    fun decrement(
        locked: Map<UUID, WarehouseStock>,
        productToQty: Map<UUID, Int>,
    ) {
        val now = OffsetDateTime.now()
        productToQty.forEach { (productId, qty) ->
            val stock = locked.getValue(productId)
            stock.quantity -= qty
            stock.updatedAt = now
        }
    }
}
