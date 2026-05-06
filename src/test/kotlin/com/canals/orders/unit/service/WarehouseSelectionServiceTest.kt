package com.canals.orders.unit.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.repository.WarehouseRepository
import com.canals.orders.service.WarehouseSelectionService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class WarehouseSelectionServiceTest {
    private val warehouseRepo = mockk<WarehouseRepository>()
    private val service = WarehouseSelectionService(warehouseRepo)

    private fun warehouse(
        lat: Double,
        lng: Double,
    ) = Warehouse(
        id = UUID.randomUUID(),
        code = "WH-${lat.toInt()}-${lng.toInt()}",
        name = "Warehouse @ $lat,$lng",
        latitude = BigDecimal(lat),
        longitude = BigDecimal(lng),
        address = "Test address",
    )

    @Test
    fun `returns empty list when product map is empty`() {
        assertThat(service.findEligibleOrderedByDistance(emptyMap(), 40.0, -74.0)).isEmpty()
    }

    @Test
    fun `returns empty list when no warehouse has stock for the product`() {
        val productId = UUID.randomUUID()
        every { warehouseRepo.findWithStock(productId, 1) } returns emptyList()

        assertThat(service.findEligibleOrderedByDistance(mapOf(productId to 1), 40.0, -74.0)).isEmpty()
    }

    @Test
    fun `returns the single warehouse that carries the product`() {
        val productId = UUID.randomUUID()
        val wh = warehouse(40.71, -74.00)
        every { warehouseRepo.findWithStock(productId, 2) } returns listOf(wh)

        assertThat(service.findEligibleOrderedByDistance(mapOf(productId to 2), 40.0, -74.0))
            .containsExactly(wh)
    }

    @Test
    fun `returns empty list when warehouses have disjoint product coverage`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val whA = warehouse(40.0, -74.0) // NYC — has id1 only
        val whB = warehouse(34.0, -118.0) // LA — has id2 only
        every { warehouseRepo.findWithStock(id1, 1) } returns listOf(whA)
        every { warehouseRepo.findWithStock(id2, 1) } returns listOf(whB)

        assertThat(
            service.findEligibleOrderedByDistance(mapOf(id1 to 1, id2 to 1), 40.0, -74.0),
        ).isEmpty()
    }

    @Test
    fun `returns only warehouses that carry ALL requested products`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val whFull = warehouse(41.0, -73.5) // has both products
        val whPartial = warehouse(34.0, -118.0) // has only id1
        every { warehouseRepo.findWithStock(id1, 1) } returns listOf(whFull, whPartial)
        every { warehouseRepo.findWithStock(id2, 1) } returns listOf(whFull)

        val result = service.findEligibleOrderedByDistance(mapOf(id1 to 1, id2 to 1), 40.0, -74.0)

        assertThat(result).containsExactly(whFull)
    }

    @Test
    fun `orders eligible warehouses by Haversine distance ascending`() {
        val productId = UUID.randomUUID()
        val near = warehouse(41.0, -73.5) // ~80 km from ship point
        val far = warehouse(34.0, -118.0) // ~3900 km from ship point
        every { warehouseRepo.findWithStock(productId, 1) } returns listOf(far, near) // reversed input order

        val result = service.findEligibleOrderedByDistance(mapOf(productId to 1), 40.71, -74.0)

        assertThat(result).containsExactly(near, far)
    }
}
