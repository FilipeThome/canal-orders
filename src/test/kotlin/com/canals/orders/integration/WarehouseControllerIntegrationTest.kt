package com.canals.orders.integration

import com.canals.orders.repository.WarehouseRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.UUID

class WarehouseControllerIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var warehouseRepository: WarehouseRepository

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }

    private fun validWarehouseBody(code: String = "WH-TEST") =
        mapOf(
            "code" to code,
            "name" to "Test Warehouse",
            "latitude" to 40.712776,
            "longitude" to -74.005974,
            "address" to "1 Test St, New York, NY",
        )

    @Test
    fun `GET warehouses returns 200 with seeded list`() {
        val response = restTemplate.getForEntity("/warehouses", List::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotEmpty
    }

    @Test
    fun `GET warehouses by id returns 200 for seeded warehouse`() {
        val warehouse = warehouseRepository.findAll().first { it.code == "WH-NYC" }

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.getForEntity(
                "/warehouses/${warehouse.id}",
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["id"]).isEqualTo(warehouse.id.toString())
        assertThat(response.body!!["code"]).isEqualTo("WH-NYC")
    }

    @Test
    fun `GET warehouses by id returns 404 for unknown id`() {
        val response = restTemplate.getForEntity("/warehouses/${UUID.randomUUID()}", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `POST warehouses returns 201 with created warehouse`() {
        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/warehouses",
                HttpMethod.POST,
                HttpEntity(validWarehouseBody("WH-IT-01"), jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!["code"]).isEqualTo("WH-IT-01")
        assertThat(response.body!!["id"]).isNotNull()
    }

    @Test
    fun `POST warehouses with latitude out of range returns 400`() {
        val body = validWarehouseBody().toMutableMap().also { it["latitude"] = 999.0 }
        val response =
            restTemplate.exchange(
                "/warehouses",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `POST warehouses with blank code returns 400`() {
        val body = validWarehouseBody().toMutableMap().also { it["code"] = "" }
        val response =
            restTemplate.exchange(
                "/warehouses",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `PUT warehouses returns 200 with updated fields`() {
        val warehouse = warehouseRepository.findAll().first { it.code == "WH-SEA" }
        val body = validWarehouseBody("WH-SEA-UPD").toMutableMap().also { it["name"] = "Seattle Updated" }

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/warehouses/${warehouse.id}",
                HttpMethod.PUT,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["name"]).isEqualTo("Seattle Updated")
    }

    @Test
    fun `PUT warehouses returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/warehouses/${UUID.randomUUID()}",
                HttpMethod.PUT,
                HttpEntity(validWarehouseBody(), jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `DELETE warehouses returns 204 for created warehouse`() {
        @Suppress("UNCHECKED_CAST")
        val createResponse =
            restTemplate.exchange(
                "/warehouses",
                HttpMethod.POST,
                HttpEntity(validWarehouseBody("WH-DEL-IT01"), jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>
        val createdId = createResponse.body!!["id"].toString()

        val deleteResponse =
            restTemplate.exchange(
                "/warehouses/$createdId",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void::class.java,
            )

        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(warehouseRepository.findById(UUID.fromString(createdId))).isEmpty
    }

    @Test
    fun `DELETE warehouses returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/warehouses/${UUID.randomUUID()}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
