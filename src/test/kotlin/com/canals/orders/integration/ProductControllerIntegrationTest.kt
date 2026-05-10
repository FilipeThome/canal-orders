package com.canals.orders.integration

import com.canals.orders.repository.ProductRepository
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

class ProductControllerIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var productRepository: ProductRepository

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }

    @Test
    fun `GET products returns 200 with seeded list`() {
        val response = restTemplate.getForEntity("/products", List::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotEmpty
    }

    @Test
    fun `GET products by id returns 200 for seeded product`() {
        val product = productRepository.findAll().first { it.sku == "SKU-1013" }

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.getForEntity(
                "/products/${product.id}",
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["id"]).isEqualTo(product.id.toString())
        assertThat(response.body!!["sku"]).isEqualTo("SKU-1013")
    }

    @Test
    fun `GET products by id returns 404 for unknown id`() {
        val response = restTemplate.getForEntity("/products/${UUID.randomUUID()}", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `POST products returns 201 with created product`() {
        val body = mapOf("sku" to "SKU-NEW-IT01", "name" to "Integration Test Product", "unitPrice" to 9.99)

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/products",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!["sku"]).isEqualTo("SKU-NEW-IT01")
        assertThat(response.body!!["id"]).isNotNull()
    }

    @Test
    fun `POST products with negative price returns 400`() {
        val body = mapOf("sku" to "SKU-BAD", "name" to "Bad", "unitPrice" to -5.0)
        val response =
            restTemplate.exchange(
                "/products",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `POST products with blank name returns 400`() {
        val body = mapOf("sku" to "SKU-BLANK", "name" to "", "unitPrice" to 9.99)
        val response =
            restTemplate.exchange(
                "/products",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `PUT products returns 200 with updated fields`() {
        val product = productRepository.findAll().first { it.sku == "SKU-1028" }
        val body = mapOf("sku" to "SKU-1028-UPD", "name" to "Updated Yoga Mat", "unitPrice" to 29.99)

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/products/${product.id}",
                HttpMethod.PUT,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["sku"]).isEqualTo("SKU-1028-UPD")
        assertThat(response.body!!["name"]).isEqualTo("Updated Yoga Mat")
    }

    @Test
    fun `PUT products returns 404 for unknown id`() {
        val body = mapOf("sku" to "X", "name" to "X", "unitPrice" to 1.0)
        val response =
            restTemplate.exchange(
                "/products/${UUID.randomUUID()}",
                HttpMethod.PUT,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `DELETE products returns 204 for created product`() {
        val createBody = mapOf("sku" to "SKU-DEL-IT01", "name" to "Delete Me", "unitPrice" to 1.0)

        @Suppress("UNCHECKED_CAST")
        val createResponse =
            restTemplate.exchange(
                "/products",
                HttpMethod.POST,
                HttpEntity(createBody, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>
        val createdId = createResponse.body!!["id"].toString()

        val deleteResponse =
            restTemplate.exchange(
                "/products/$createdId",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void::class.java,
            )

        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(productRepository.findById(UUID.fromString(createdId))).isEmpty
    }

    @Test
    fun `DELETE products returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/products/${UUID.randomUUID()}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
