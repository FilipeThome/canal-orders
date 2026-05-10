package com.canals.orders.unit.controller

import com.canals.orders.controller.WarehouseController
import com.canals.orders.domain.Warehouse
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.exception.WarehouseNotFoundException
import com.canals.orders.service.WarehouseService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(WarehouseController::class)
@Import(GlobalExceptionHandler::class)
class WarehouseControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

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

    private fun validWarehouseBody() =
        mapOf(
            "code" to "WH-NYC",
            "name" to "New York Warehouse",
            "latitude" to 40.712776,
            "longitude" to -74.005974,
            "address" to "1 Fulton St, New York, NY",
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

    @Test
    fun `GET warehouses by id returns 200 with response`() {
        val id = UUID.randomUUID()
        val warehouse = mockWarehouse(id)
        every { warehouseService.getById(id) } returns warehouse

        mockMvc.get("/warehouses/$id").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.code") { value("WH-NYC") }
        }
    }

    @Test
    fun `GET warehouses by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { warehouseService.getById(id) } throws WarehouseNotFoundException(id)

        mockMvc.get("/warehouses/$id").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST warehouses returns 201 with created warehouse`() {
        val warehouse = mockWarehouse()
        every { warehouseService.create(any()) } returns warehouse

        mockMvc.post("/warehouses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validWarehouseBody())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.code") { value("WH-NYC") }
        }
    }

    @Test
    fun `POST warehouses with invalid latitude returns 400`() {
        val body = validWarehouseBody().toMutableMap().also { it["latitude"] = 999.0 }

        mockMvc.post("/warehouses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST warehouses with blank code returns 400`() {
        val body = validWarehouseBody().toMutableMap().also { it["code"] = "" }

        mockMvc.post("/warehouses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT warehouses returns 200 with updated warehouse`() {
        val id = UUID.randomUUID()
        val warehouse = mockWarehouse(id)
        every { warehouseService.update(any(), any()) } returns warehouse

        mockMvc.put("/warehouses/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validWarehouseBody())
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
        }
    }

    @Test
    fun `PUT warehouses returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { warehouseService.update(any(), any()) } throws WarehouseNotFoundException(id)

        mockMvc.put("/warehouses/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validWarehouseBody())
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE warehouses returns 204`() {
        val id = UUID.randomUUID()
        every { warehouseService.delete(id) } just runs

        mockMvc.delete("/warehouses/$id").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE warehouses returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { warehouseService.delete(id) } throws WarehouseNotFoundException(id)

        mockMvc.delete("/warehouses/$id").andExpect {
            status { isNotFound() }
        }
    }
}
