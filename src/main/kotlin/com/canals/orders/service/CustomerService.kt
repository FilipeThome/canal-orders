package com.canals.orders.service

import com.canals.orders.domain.Customer
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
    fun getById(id: UUID): Customer = customerRepository.findById(id).orElseThrow { CustomerNotFoundException(id) }
}
