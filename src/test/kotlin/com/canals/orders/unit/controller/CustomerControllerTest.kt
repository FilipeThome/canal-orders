package com.canals.orders.unit.controller

import com.canals.orders.controller.CustomerController
import com.canals.orders.domain.Customer
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.service.CustomerService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(CustomerController::class)
@Import(GlobalExceptionHandler::class)
class CustomerControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var customerService: CustomerService

    private fun mockCustomer(id: UUID = UUID.randomUUID()) =
        Customer(
            id = id,
            email = "alice@example.com",
            fullName = "Alice Smith",
            createdAt = OffsetDateTime.now(),
        )

    @Test
    fun `GET customers returns 200 with empty list`() {
        every { customerService.list() } returns emptyList()

        mockMvc.get("/customers").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$") { isArray() }
        }
    }

    @Test
    fun `GET customers returns mapped customer responses`() {
        val customer = mockCustomer()
        every { customerService.list() } returns listOf(customer)

        mockMvc.get("/customers").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(customer.id.toString()) }
            jsonPath("$[0].email") { value("alice@example.com") }
            jsonPath("$[0].fullName") { value("Alice Smith") }
        }
    }
}
