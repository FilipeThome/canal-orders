package com.canals.orders.integration

import com.canals.orders.repository.CustomerRepository
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

class CustomerControllerIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var customerRepository: CustomerRepository

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }

    @Test
    fun `GET customers returns 200 with seeded list`() {
        val response = restTemplate.getForEntity("/customers", List::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotEmpty
    }

    @Test
    fun `GET customers by id returns 200 for seeded customer`() {
        val customer = customerRepository.findAll().first()

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.getForEntity(
                "/customers/${customer.id}",
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["id"]).isEqualTo(customer.id.toString())
        assertThat(response.body!!["email"]).isEqualTo(customer.email)
    }

    @Test
    fun `GET customers by id returns 404 for unknown id`() {
        @Suppress("UNCHECKED_CAST")
        val response = restTemplate.getForEntity("/customers/${UUID.randomUUID()}", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `POST customers returns 201 with created customer`() {
        val body = mapOf("email" to "newuser@test.com", "fullName" to "New User")

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/customers",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!["email"]).isEqualTo("newuser@test.com")
        assertThat(response.body!!["fullName"]).isEqualTo("New User")
        assertThat(response.body!!["id"]).isNotNull()
    }

    @Test
    fun `POST customers with invalid email returns 400`() {
        val body = mapOf("email" to "not-an-email", "fullName" to "Test")
        val response =
            restTemplate.exchange(
                "/customers",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `POST customers with blank fullName returns 400`() {
        val body = mapOf("email" to "valid@test.com", "fullName" to "")
        val response =
            restTemplate.exchange(
                "/customers",
                HttpMethod.POST,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `PUT customers returns 200 with updated fields`() {
        val customer = customerRepository.findAll().first()
        val body = mapOf("email" to "updated@test.com", "fullName" to "Updated Name")

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                "/customers/${customer.id}",
                HttpMethod.PUT,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!["email"]).isEqualTo("updated@test.com")
        assertThat(response.body!!["fullName"]).isEqualTo("Updated Name")
    }

    @Test
    fun `PUT customers returns 404 for unknown id`() {
        val body = mapOf("email" to "x@x.com", "fullName" to "X")
        val response =
            restTemplate.exchange(
                "/customers/${UUID.randomUUID()}",
                HttpMethod.PUT,
                HttpEntity(body, jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `DELETE customers returns 204 for created customer`() {
        val createBody = mapOf("email" to "todelete@test.com", "fullName" to "To Delete")

        @Suppress("UNCHECKED_CAST")
        val createResponse =
            restTemplate.exchange(
                "/customers",
                HttpMethod.POST,
                HttpEntity(createBody, jsonHeaders()),
                Map::class.java,
            ) as org.springframework.http.ResponseEntity<Map<String, Any>>
        val createdId = createResponse.body!!["id"].toString()

        val deleteResponse =
            restTemplate.exchange(
                "/customers/$createdId",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void::class.java,
            )

        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(customerRepository.findById(UUID.fromString(createdId))).isEmpty
    }

    @Test
    fun `DELETE customers returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/customers/${UUID.randomUUID()}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
