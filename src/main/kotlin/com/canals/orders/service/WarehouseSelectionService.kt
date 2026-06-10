package com.canals.orders.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.repository.WarehouseRepository
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class WarehouseSelectionService(
    private val warehouseRepo: WarehouseRepository,
) {
    /**
     * Returns warehouses that can fulfil every item in [productIdsToQuantities],
     * sorted by Haversine distance from the shipping coordinates.
     *
     * Complexity: O(1) DB round-trips (single batch query), O(W log W) sort
     * where W = eligible warehouses.  Previous implementation fired one query
     * per product (O(P) round-trips) and intersected results in memory.
     */
    fun findEligibleOrderedByDistance(
        productIdsToQuantities: Map<UUID, Int>,
        shipLat: Double,
        shipLng: Double,
    ): List<Warehouse> {
        if (productIdsToQuantities.isEmpty()) return emptyList()

        val productIds = productIdsToQuantities.keys.toList()
        val quantities = productIds.map { productIdsToQuantities.getValue(it) }

        return warehouseRepo
            .findWarehousesWithSufficientStock(
                productIds = productIds.joinToString(","),
                quantities = quantities.joinToString(","),
                productCount = productIds.size.toLong(),
            )
            .sortedBy { w -> distanceKm(shipLat, shipLng, w.latitude.toDouble(), w.longitude.toDouble()) }
    }

    private fun distanceKm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val fromLatRad = Math.toRadians(fromLat)
        val toLatRad = Math.toRadians(toLat)
        val a = sin(dLat / 2).pow(2) + cos(fromLatRad) * cos(toLatRad) * sin(dLng / 2).pow(2)
        return earthRadiusKm * 2 * asin(sqrt(a))
    }
}
