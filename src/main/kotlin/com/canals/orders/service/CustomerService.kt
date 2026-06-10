package com.canals.orders.service

import com.canals.orders.domain.Customer
import com.canals.orders.dto.request.CreateCustomerRequest
import com.canals.orders.dto.request.UpdateCustomerRequest
import com.canals.orders.exception.CustomerNotFoundException
import com.canals.orders.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<Customer> = customerRepository.findAll()

    @Transactional(readOnly = true)
    fun getById(id: UUID): Customer =
        customerRepository.findById(id).orElseThrow { CustomerNotFoundException(id) }

    @Transactional
    fun create(request: CreateCustomerRequest): Customer =
        customerRepository.save(
            Customer(
                id = UUID.randomUUID(),
                email = request.email,
                fullName = request.fullName,
            ),
        )

    @Transactional
    fun update(
        id: UUID,
        request: UpdateCustomerRequest,
    ): Customer =
        getById(id).let { existing ->
            customerRepository.save(
                Customer(
                    id = existing.id,
                    email = request.email,
                    fullName = request.fullName,
                    createdAt = existing.createdAt,
                ),
            )
        }

    @Transactional
    fun delete(id: UUID) {
        if (!customerRepository.existsById(id)) throw CustomerNotFoundException(id)
        customerRepository.deleteById(id)
    }
}
