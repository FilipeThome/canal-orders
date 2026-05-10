package com.canals.orders.unit.controller

import com.canals.orders.controller.ProductController
import com.canals.orders.domain.Product
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.service.ProductService
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

@WebMvcTest(ProductController::class)
@Import(GlobalExceptionHandler::class)
class ProductControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

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
}
