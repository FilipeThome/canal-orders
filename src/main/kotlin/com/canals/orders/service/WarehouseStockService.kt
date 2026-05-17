package com.canals.orders.service

import com.canals.orders.domain.WarehouseStock
import com.canals.orders.repository.WarehouseStockRepository
import org.springframework.stereotype.Service
import java.util.UUID

sealed interface StockReservation {
    data class Acquired(val stockByProduct: Map<UUID, WarehouseStock>) : StockReservation

    data object Unavailable : StockReservation
}

@Service
class WarehouseStockService(
    private val warehouseStockRepo: WarehouseStockRepository,
) {
    fun tryLock(
        warehouseId: UUID,
        productToQty: Map<UUID, Int>,
    ): StockReservation {
        val locked =
            warehouseStockRepo
                .lockStockForUpdate(warehouseId, productToQty.keys)
                .associateBy { it.id.productId }
        val feasible = productToQty.all { (productId, qty) -> (locked[productId]?.quantity ?: 0) >= qty }
        return if (feasible) StockReservation.Acquired(locked) else StockReservation.Unavailable
    }

    fun decrement(
        reservation: StockReservation.Acquired,
        productToQty: Map<UUID, Int>,
    ) {
        productToQty.forEach { (productId, qty) ->
            reservation.stockByProduct.getValue(productId).decrement(qty)
        }
    }
}
