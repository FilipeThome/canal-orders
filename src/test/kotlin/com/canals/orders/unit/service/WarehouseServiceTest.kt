package com.canals.orders.unit.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.repository.WarehouseRepository
import com.canals.orders.service.WarehouseService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class WarehouseServiceTest {
    private val warehouseRepository = mockk<WarehouseRepository>()
    private val service = WarehouseService(warehouseRepository)

    private fun mockWarehouse(id: UUID = UUID.randomUUID()) =
        Warehouse(
            id = id,
            code = "WH-NYC",
            name = "New York Warehouse",
            latitude = BigDecimal("40.712776"),
            longitude = BigDecimal("-74.005974"),
            address = "1 Fulton St, New York, NY",
            createdAt = OffsetDateTime.now(),
        )

    @Test
    fun `list returns all warehouses from repository`() {
        val warehouses = listOf(mockWarehouse(), mockWarehouse())
        every { warehouseRepository.findAll() } returns warehouses

        val result = service.list()

        assertThat(result).isEqualTo(warehouses)
        verify(exactly = 1) { warehouseRepository.findAll() }
    }

    @Test
    fun `list returns empty list when no warehouses exist`() {
        every { warehouseRepository.findAll() } returns emptyList()

        val result = service.list()

        assertThat(result).isEmpty()
    }
}
