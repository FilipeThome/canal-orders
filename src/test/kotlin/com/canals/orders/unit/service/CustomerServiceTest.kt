package com.canals.orders.unit.service

import com.canals.orders.domain.Customer
import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.repository.CustomerRepository
import com.canals.orders.service.CustomerService
import io.mockk.every
import io.mockk.mockk
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
}
