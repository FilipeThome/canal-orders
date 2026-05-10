package com.canals.orders.unit.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.dto.request.CreateWarehouseRequest
import com.canals.orders.dto.request.UpdateWarehouseRequest
import com.canals.orders.exception.WarehouseNotFoundException
import com.canals.orders.repository.WarehouseRepository
import com.canals.orders.service.WarehouseService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Optional
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

    @Test
    fun `getById returns warehouse when found`() {
        val id = UUID.randomUUID()
        val expected = mockWarehouse(id)
        every { warehouseRepository.findById(id) } returns Optional.of(expected)

        assertThat(service.getById(id)).isEqualTo(expected)
    }

    @Test
    fun `getById throws WarehouseNotFoundException when not found`() {
        val id = UUID.randomUUID()
        every { warehouseRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.getById(id) }
            .isInstanceOf(WarehouseNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }

    @Test
    fun `create saves new warehouse with provided fields`() {
        val request =
            CreateWarehouseRequest(
                code = "WH-LA",
                name = "Los Angeles",
                latitude = BigDecimal("34.052235"),
                longitude = BigDecimal("-118.243683"),
                address = "1 LA St",
            )
        every { warehouseRepository.save(any()) } answers { firstArg() }

        val result = service.create(request)

        assertThat(result.code).isEqualTo("WH-LA")
        assertThat(result.name).isEqualTo("Los Angeles")
        assertThat(result.latitude).isEqualByComparingTo("34.052235")
        assertThat(result.id).isNotNull()
        verify(exactly = 1) { warehouseRepository.save(any()) }
    }

    @Test
    fun `update saves updated fields preserving id and createdAt`() {
        val id = UUID.randomUUID()
        val existing = mockWarehouse(id)
        val request =
            UpdateWarehouseRequest(
                code = "WH-UPD",
                name = "Updated Warehouse",
                latitude = BigDecimal("35.0"),
                longitude = BigDecimal("-80.0"),
                address = "New Address",
            )
        every { warehouseRepository.findById(id) } returns Optional.of(existing)
        every { warehouseRepository.save(any()) } answers { firstArg() }

        val result = service.update(id, request)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.code).isEqualTo("WH-UPD")
        assertThat(result.name).isEqualTo("Updated Warehouse")
        assertThat(result.createdAt).isEqualTo(existing.createdAt)
    }

    @Test
    fun `update throws WarehouseNotFoundException when warehouse does not exist`() {
        val id = UUID.randomUUID()
        val request =
            UpdateWarehouseRequest(
                code = "X",
                name = "X",
                latitude = BigDecimal("0"),
                longitude = BigDecimal("0"),
                address = "X",
            )
        every { warehouseRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.update(id, request) }
            .isInstanceOf(WarehouseNotFoundException::class.java)
    }

    @Test
    fun `delete calls deleteById when warehouse exists`() {
        val id = UUID.randomUUID()
        every { warehouseRepository.existsById(id) } returns true
        every { warehouseRepository.deleteById(id) } just runs

        service.delete(id)

        verify(exactly = 1) { warehouseRepository.deleteById(id) }
    }

    @Test
    fun `delete throws WarehouseNotFoundException when warehouse does not exist`() {
        val id = UUID.randomUUID()
        every { warehouseRepository.existsById(id) } returns false

        assertThatThrownBy { service.delete(id) }
            .isInstanceOf(WarehouseNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }
}
