package com.canals.orders.integration

import com.canals.orders.repository.CustomerRepository
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

class OrderControllerIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    private fun validOrderBody(
        customerId: UUID,
        productId: UUID,
        quantity: Int = 1,
        cardNumber: String = "4111111111111111",
    ): Map<String, Any> =
        mapOf(
            "customerId" to customerId.toString(),
            "shippingAddress" to mapOf("addressLine" to "123 Commerce St, Dallas, TX 75202"),
            "items" to listOf(mapOf("productId" to productId.toString(), "quantity" to quantity)),
            "payment" to mapOf("cardNumber" to cardNumber),
        )

    private fun postOrder(
        body: Any,
        idempotencyKey: String? = null,
    ): ResponseEntity<Map<String, Any>> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        idempotencyKey?.let { headers.set("Idempotency-Key", it) }
        return restTemplate.exchange(
            "/orders",
            HttpMethod.POST,
            HttpEntity(body, headers),
            object : ParameterizedTypeReference<Map<String, Any>>() {},
        )
    }

    @Test
    fun `GET orders returns 200 with a list`() {
        val response = restTemplate.getForEntity("/orders", List::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
    }

    @Test
    fun `POST orders with valid seed data returns 201 PAID order`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1001" }

        val response = postOrder(validOrderBody(customer.id, product.id))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = response.body!!
        assertThat(body["status"]).isEqualTo("PAID")
        assertThat(body["paymentId"].toString()).startsWith("pay_")
        assertThat(body["cardLast4"]).isEqualTo("1111")
        assertThat(body["totalAmount"].toString()).isEqualTo("24.99")
    }

    @Test
    fun `POST orders returns 404 for unknown customer id`() {
        val product = productRepository.findAll().first()
        val response = postOrder(validOrderBody(UUID.randomUUID(), product.id))
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `POST orders returns 404 for unknown product id`() {
        val customer = customerRepository.findAll().first()
        val response = postOrder(validOrderBody(customer.id, UUID.randomUUID()))
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `POST orders returns 422 when requested quantity exceeds all warehouse stock`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1001" }

        val response = postOrder(validOrderBody(customer.id, product.id, quantity = 9999))

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `POST orders returns 402 when forced-decline test card is used`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1002" }

        val response =
            postOrder(
                validOrderBody(customer.id, product.id, cardNumber = "4111111111110002"),
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYMENT_REQUIRED)
    }

    @Test
    fun `POST orders returns 400 for duplicate product ids in items list`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1001" }
        val body =
            mapOf(
                "customerId" to customer.id.toString(),
                "shippingAddress" to mapOf("addressLine" to "123 Main St"),
                "items" to
                    listOf(
                        mapOf("productId" to product.id.toString(), "quantity" to 1),
                        mapOf("productId" to product.id.toString(), "quantity" to 2),
                    ),
                "payment" to mapOf("cardNumber" to "4111111111111111"),
            )

        val response = postOrder(body)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `POST orders returns 400 for missing required field`() {
        val body =
            mapOf(
                "shippingAddress" to mapOf("addressLine" to "123 Main St"),
                "items" to listOf(mapOf("productId" to UUID.randomUUID().toString(), "quantity" to 1)),
                "payment" to mapOf("cardNumber" to "4111111111111111"),
            )

        val response = postOrder(body)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `POST orders with same Idempotency-Key and body returns same order id on retry`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1003" }
        val key = "idem-test-${UUID.randomUUID()}"
        val body = validOrderBody(customer.id, product.id)

        val first = postOrder(body, idempotencyKey = key)
        val second = postOrder(body, idempotencyKey = key)

        assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(second.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(first.body!!["id"]).isEqualTo(second.body!!["id"])
    }

    @Test
    fun `POST orders with same Idempotency-Key but different body returns 422`() {
        val customer = customerRepository.findAll().first()
        val product1 = productRepository.findAll().first { it.sku == "SKU-1005" }
        val product2 = productRepository.findAll().first { it.sku == "SKU-1006" }
        val key = "idem-conflict-${UUID.randomUUID()}"

        val first = postOrder(validOrderBody(customer.id, product1.id), idempotencyKey = key)
        assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)

        val second = postOrder(validOrderBody(customer.id, product2.id), idempotencyKey = key)
        assertThat(second.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `GET orders includes a previously created order`() {
        val customer = customerRepository.findAll().first()
        val product = productRepository.findAll().first { it.sku == "SKU-1007" }
        val createResponse = postOrder(validOrderBody(customer.id, product.id))
        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val createdId = createResponse.body!!["id"].toString()

        val listResponse = restTemplate.getForEntity("/orders", List::class.java)

        assertThat(listResponse.statusCode).isEqualTo(HttpStatus.OK)
        @Suppress("UNCHECKED_CAST")
        val ids = (listResponse.body as List<Map<String, Any>>).map { it["id"].toString() }
        assertThat(ids).contains(createdId)
    }

    @Test
    fun `error responses conform to RFC 7807 problem detail`() {
        val body = mapOf<String, Any>("invalid" to "body")

        val response = postOrder(body)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val responseBody = response.body!!
        assertThat(responseBody["type"]).isNotNull
        assertThat(responseBody["title"]).isNotNull
    }
}
