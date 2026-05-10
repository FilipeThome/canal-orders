package com.canals.orders.integration

import com.canals.orders.repository.CustomerRepository
import com.canals.orders.repository.OrderRepository
import com.canals.orders.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.UUID

class OrderCrudIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var orderRepository: OrderRepository

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().also { it.contentType = MediaType.APPLICATION_JSON }

    private fun postOrder(
        customerId: UUID,
        productId: UUID,
        quantity: Int = 1,
        key: String = UUID.randomUUID().toString(),
    ): ResponseEntity<Map<String, Any>> {
        val body =
            mapOf(
                "customerId" to customerId.toString(),
                "shippingAddress" to mapOf("addressLine" to "123 Commerce St, Dallas, TX 75202"),
                "items" to listOf(mapOf("productId" to productId.toString(), "quantity" to quantity)),
                "payment" to mapOf("cardNumber" to "4111111111111111"),
            )
        val headers = jsonHeaders().also { it.set("Idempotency-Key", key) }
        return restTemplate.exchange(
            "/orders",
            HttpMethod.POST,
            HttpEntity(body, headers),
            object : ParameterizedTypeReference<Map<String, Any>>() {},
        )
    }

    @Test
    fun `GET orders by id returns 200 for created order`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1029" }
        val createResponse = postOrder(customer.id, product.id)
        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val orderId = createResponse.body!!["id"].toString()

        @Suppress("UNCHECKED_CAST")
        val getResponse = restTemplate.getForEntity("/orders/$orderId", Map::class.java) as ResponseEntity<Map<String, Any>>

        assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getResponse.body!!["id"]).isEqualTo(orderId)
        assertThat(getResponse.body!!["status"]).isEqualTo("PENDING_PAYMENT")
    }

    @Test
    fun `GET orders by id returns 404 for unknown id`() {
        val response = restTemplate.getForEntity("/orders/${UUID.randomUUID()}", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `PUT orders returns 200 with updated status`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1030" }
        val createResponse = postOrder(customer.id, product.id)
        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val orderId = createResponse.body!!["id"].toString()

        @Suppress("UNCHECKED_CAST")
        val updateResponse =
            restTemplate.exchange(
                "/orders/$orderId",
                HttpMethod.PUT,
                HttpEntity(mapOf("status" to "CANCELLED"), jsonHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any>>

        assertThat(updateResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(updateResponse.body!!["status"]).isEqualTo("CANCELLED")
    }

    @Test
    fun `PUT orders returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/orders/${UUID.randomUUID()}",
                HttpMethod.PUT,
                HttpEntity(mapOf("status" to "CANCELLED"), jsonHeaders()),
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `DELETE orders returns 204 for created order`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1013" }
        val createResponse = postOrder(customer.id, product.id)
        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val orderId = createResponse.body!!["id"].toString()

        val deleteResponse =
            restTemplate.exchange(
                "/orders/$orderId",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void::class.java,
            )

        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(orderRepository.findById(UUID.fromString(orderId))).isEmpty
    }

    @Test
    fun `DELETE orders returns 404 for unknown id`() {
        val response =
            restTemplate.exchange(
                "/orders/${UUID.randomUUID()}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
