package com.canals.orders.unit.controller

import com.canals.orders.controller.WarehouseController
import com.canals.orders.domain.Warehouse
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.service.WarehouseService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(WarehouseController::class)
@Import(GlobalExceptionHandler::class)
class WarehouseControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var warehouseService: WarehouseService

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
    fun `GET warehouses returns 200 with empty list`() {
        every { warehouseService.list() } returns emptyList()

        mockMvc.get("/warehouses").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `GET warehouses returns mapped warehouse responses`() {
        val warehouse = mockWarehouse()
        every { warehouseService.list() } returns listOf(warehouse)

        mockMvc.get("/warehouses").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(warehouse.id.toString()) }
            jsonPath("$[0].name") { value("New York Warehouse") }
            jsonPath("$[0].code") { value("WH-NYC") }
        }
    }
}
