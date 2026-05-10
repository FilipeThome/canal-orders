package com.canals.orders.unit.controller

import com.canals.orders.controller.CustomerController
import com.canals.orders.domain.Customer
import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.exception.GlobalExceptionHandler
import com.canals.orders.service.CustomerService
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
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(CustomerController::class)
@Import(GlobalExceptionHandler::class)
class CustomerControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

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

    @Test
    fun `GET customers by id returns 200 with response`() {
        val id = UUID.randomUUID()
        val customer = mockCustomer(id)
        every { customerService.getById(id) } returns customer

        mockMvc.get("/customers/$id").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.email") { value("alice@example.com") }
            jsonPath("$.fullName") { value("Alice Smith") }
        }
    }

    @Test
    fun `GET customers by id returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { customerService.getById(id) } throws CustomerNotFoundException(id)

        mockMvc.get("/customers/$id").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST customers returns 201 with created customer`() {
        val customer = mockCustomer()
        every { customerService.create(any()) } returns customer

        mockMvc.post("/customers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "alice@example.com", "fullName" to "Alice Smith"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("alice@example.com") }
        }
    }

    @Test
    fun `POST customers with invalid email returns 400`() {
        mockMvc.post("/customers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "not-an-email", "fullName" to "Alice Smith"))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST customers with blank fullName returns 400`() {
        mockMvc.post("/customers") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "alice@example.com", "fullName" to ""))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT customers returns 200 with updated customer`() {
        val id = UUID.randomUUID()
        val customer = mockCustomer(id)
        every { customerService.update(eq(id), any()) } returns customer

        mockMvc.put("/customers/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "alice@example.com", "fullName" to "Alice Smith"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
        }
    }

    @Test
    fun `PUT customers returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { customerService.update(eq(id), any()) } throws CustomerNotFoundException(id)

        mockMvc.put("/customers/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "alice@example.com", "fullName" to "Alice Smith"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE customers returns 204`() {
        val id = UUID.randomUUID()
        every { customerService.delete(id) } just runs

        mockMvc.delete("/customers/$id").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE customers returns 404 when not found`() {
        val id = UUID.randomUUID()
        every { customerService.delete(id) } throws CustomerNotFoundException(id)

        mockMvc.delete("/customers/$id").andExpect {
            status { isNotFound() }
        }
    }
}
