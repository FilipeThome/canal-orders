package com.canals.orders.unit.controller

import com.canals.orders.controller.ProductController
import com.canals.orders.domain.Product
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.exception.ProductNotFoundException
import com.canals.orders.service.ProductService
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

@WebMvcTest(ProductController::class)
@Import(GlobalExceptionHandler::class)
class ProductControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var productService: ProductService

    private fun mockProduct(id: UUID = UUID.randomUUID()) =
        Product(
            id = id,
            sku = "SKU-001",
            name = "Widget A",
            unitPrice = BigDecimal("19.99"),
            createdAt = OffsetDateTime.now(),
        )

    @Test
    fun `GET products returns 200 with empty list`() {
        every { productService.list() } returns emptyList()

        mockMvc.get("/products").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `GET products returns mapped product responses`() {
        val product = mockProduct()
        every { productService.list() } returns listOf(product)

        mockMvc.get("/products").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(product.id.toString()) }
            jsonPath("$[0].name") { value("Widget A") }
            jsonPath("$[0].unitPrice") { value(19.99) }
        }
    }

    @Test
    fun `GET products by id returns 200 with response`() {
        val id = UUID.randomUUID()
        val product = mockProduct(id)
        every { productService.getById(id) } returns product

        mockMvc.get("/products/$id").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.sku") { value("SKU-001") }
        }
    }

    @Test
    fun `GET products by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { productService.getById(id) } throws ProductNotFoundException(id)

        mockMvc.get("/products/$id").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST products returns 201 with created product`() {
        val product = mockProduct()
        every { productService.create(any()) } returns product

        mockMvc.post("/products") {
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("sku" to "SKU-001", "name" to "Widget A", "unitPrice" to 19.99),
                )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.sku") { value("SKU-001") }
        }
    }

    @Test
    fun `POST products with negative unitPrice returns 400`() {
        mockMvc.post("/products") {
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("sku" to "SKU-001", "name" to "Widget A", "unitPrice" to -1.0),
                )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST products with blank sku returns 400`() {
        mockMvc.post("/products") {
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("sku" to "", "name" to "Widget A", "unitPrice" to 9.99),
                )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT products returns 200 with updated product`() {
        val id = UUID.randomUUID()
        val product = mockProduct(id)
        every { productService.update(any(), any()) } returns product

        mockMvc.put("/products/$id") {
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("sku" to "SKU-001", "name" to "Widget A", "unitPrice" to 19.99),
                )
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
        }
    }

    @Test
    fun `PUT products returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { productService.update(any(), any()) } throws ProductNotFoundException(id)

        mockMvc.put("/products/$id") {
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("sku" to "SKU-001", "name" to "Widget A", "unitPrice" to 19.99),
                )
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE products returns 204`() {
        val id = UUID.randomUUID()
        every { productService.delete(id) } just runs

        mockMvc.delete("/products/$id").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE products returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { productService.delete(id) } throws ProductNotFoundException(id)

        mockMvc.delete("/products/$id").andExpect {
            status { isNotFound() }
        }
    }
}
