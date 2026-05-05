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
    fun findEligibleOrderedByDistance(
        productIdsToQuantities: Map<UUID, Int>,
        shipLat: Double,
        shipLng: Double,
    ): List<Warehouse> {
        if (productIdsToQuantities.isEmpty()) return emptyList()

        val resultSets =
            productIdsToQuantities.entries
                .map { e -> warehouseRepo.findWithStock(e.key, e.value).associateBy { it.id } }

        val eligibleIds: Set<UUID> =
            resultSets
                .map { it.keys }
                .reduce { acc: Set<UUID>, ids: Set<UUID> -> acc intersect ids }

        if (eligibleIds.isEmpty()) return emptyList()

        return resultSets.first()
            .filterKeys { it in eligibleIds }
            .values
            .sortedBy { w -> distanceBetweenKm(shipLat, shipLng, w.latitude.toDouble(), w.longitude.toDouble()) }
    }

    private fun distanceBetweenKm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val earthRadiusKm = 6371.0
        val fromLatRad = Math.toRadians(fromLat)
        val toLatRad = Math.toRadians(toLat)
        val deltaLat = Math.toRadians(toLat - fromLat)
        val deltaLng = Math.toRadians(toLng - fromLng)
        val a = sin(deltaLat / 2).pow(2) + cos(fromLatRad) * cos(toLatRad) * sin(deltaLng / 2).pow(2)
        return earthRadiusKm * 2 * asin(sqrt(a))
    }
}
