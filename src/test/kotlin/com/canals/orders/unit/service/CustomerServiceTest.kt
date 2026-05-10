package com.canals.orders.unit.service

import com.canals.orders.domain.Customer
import com.canals.orders.dto.request.CreateCustomerRequest
import com.canals.orders.dto.request.UpdateCustomerRequest
import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.repository.CustomerRepository
import com.canals.orders.service.CustomerService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class CustomerServiceTest {
    private val customerRepository = mockk<CustomerRepository>()
    private val service = CustomerService(customerRepository)

    private fun customer(id: UUID = UUID.randomUUID()) = Customer(id = id, email = "user@example.com", fullName = "Test User")

    @Test
    fun `list returns all customers from repository`() {
        val expected = listOf(customer(), customer())
        every { customerRepository.findAll() } returns expected

        assertThat(service.list()).isEqualTo(expected)
    }

    @Test
    fun `getById returns customer when found`() {
        val id = UUID.randomUUID()
        val expected = customer(id)
        every { customerRepository.findById(id) } returns Optional.of(expected)

        assertThat(service.getById(id)).isEqualTo(expected)
    }

    @Test
    fun `getById throws CustomerNotFoundException when id is unknown`() {
        val id = UUID.randomUUID()
        every { customerRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.getById(id) }
            .isInstanceOf(CustomerNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }

    @Test
    fun `getById queries repository exactly once`() {
        val id = UUID.randomUUID()
        every { customerRepository.findById(id) } returns Optional.of(customer(id))

        service.getById(id)

        verify(exactly = 1) { customerRepository.findById(id) }
    }

    @Test
    fun `create saves new customer with provided email and fullName`() {
        val request = CreateCustomerRequest(email = "new@example.com", fullName = "New User")
        val saved = slot<Customer>()
        every { customerRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.create(request)

        assertThat(result.email).isEqualTo("new@example.com")
        assertThat(result.fullName).isEqualTo("New User")
        verify(exactly = 1) { customerRepository.save(any()) }
    }

    @Test
    fun `create assigns a non-null UUID to new customer`() {
        val request = CreateCustomerRequest(email = "x@x.com", fullName = "X")
        every { customerRepository.save(any()) } answers { firstArg() }

        val result = service.create(request)

        assertThat(result.id).isNotNull()
    }

    @Test
    fun `update saves updated fields preserving id and createdAt`() {
        val id = UUID.randomUUID()
        val existing = customer(id)
        val request = UpdateCustomerRequest(email = "updated@example.com", fullName = "Updated Name")
        every { customerRepository.findById(id) } returns Optional.of(existing)
        every { customerRepository.save(any()) } answers { firstArg() }

        val result = service.update(id, request)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.email).isEqualTo("updated@example.com")
        assertThat(result.fullName).isEqualTo("Updated Name")
        assertThat(result.createdAt).isEqualTo(existing.createdAt)
    }

    @Test
    fun `update throws CustomerNotFoundException when customer does not exist`() {
        val id = UUID.randomUUID()
        val request = UpdateCustomerRequest(email = "x@x.com", fullName = "X")
        every { customerRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.update(id, request) }
            .isInstanceOf(CustomerNotFoundException::class.java)
    }

    @Test
    fun `delete calls deleteById when customer exists`() {
        val id = UUID.randomUUID()
        every { customerRepository.existsById(id) } returns true
        every { customerRepository.deleteById(id) } just runs

        service.delete(id)

        verify(exactly = 1) { customerRepository.deleteById(id) }
    }

    @Test
    fun `delete throws CustomerNotFoundException when customer does not exist`() {
        val id = UUID.randomUUID()
        every { customerRepository.existsById(id) } returns false

        assertThatThrownBy { service.delete(id) }
            .isInstanceOf(CustomerNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }
}
