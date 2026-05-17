package com.canals.orders.unit.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.repository.WarehouseRepository
import com.canals.orders.service.WarehouseSelectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        verify(exactly = 0) { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), any()) }
    }

    @Test
    fun `returns empty list when batch query finds no eligible warehouse`() {
        val productId = UUID.randomUUID()
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 1L) } returns emptyList()

        assertThat(service.findEligibleOrderedByDistance(mapOf(productId to 1), 40.0, -74.0)).isEmpty()
    }

    @Test
    fun `returns the single warehouse returned by batch query`() {
        val productId = UUID.randomUUID()
        val wh = warehouse(40.71, -74.00)
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 1L) } returns listOf(wh)

        assertThat(service.findEligibleOrderedByDistance(mapOf(productId to 2), 40.0, -74.0))
            .containsExactly(wh)
    }

    @Test
    fun `returns empty list when no warehouse satisfies all products (DB does intersection)`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        // Batch query already returns intersection — no warehouse carries both
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 2L) } returns emptyList()

        assertThat(
            service.findEligibleOrderedByDistance(mapOf(id1 to 1, id2 to 1), 40.0, -74.0),
        ).isEmpty()
    }

    @Test
    fun `returns only warehouses carrying ALL requested products`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val whFull = warehouse(41.0, -73.5)
        // Batch query filters partial warehouses at DB level; only whFull returned
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 2L) } returns listOf(whFull)

        val result = service.findEligibleOrderedByDistance(mapOf(id1 to 1, id2 to 1), 40.0, -74.0)

        assertThat(result).containsExactly(whFull)
    }

    @Test
    fun `orders eligible warehouses by Haversine distance ascending`() {
        val productId = UUID.randomUUID()
        val near = warehouse(41.0, -73.5)   // ~80 km from ship point
        val far = warehouse(34.0, -118.0)   // ~3900 km from ship point
        every {
            warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 1L)
        } returns listOf(far, near) // reversed input order — service must sort

        val result = service.findEligibleOrderedByDistance(mapOf(productId to 1), 40.71, -74.0)

        assertThat(result).containsExactly(near, far)
    }

    @Test
    fun `passes correct productCount to batch query`() {
        val ids = (1..3).associate { UUID.randomUUID() to it }
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 3L) } returns emptyList()

        service.findEligibleOrderedByDistance(ids, 40.0, -74.0)

        verify(exactly = 1) { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), 3L) }
    }

    @Test
    fun `issues exactly one DB query regardless of product count`() {
        val ids = (1..5).associate { UUID.randomUUID() to it * 2 }
        every { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), any()) } returns emptyList()

        service.findEligibleOrderedByDistance(ids, 40.0, -74.0)

        verify(exactly = 1) { warehouseRepo.findWarehousesWithSufficientStock(any(), any(), any()) }
    }
}
