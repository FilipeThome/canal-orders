package com.canals.orders.controller

import com.canals.orders.dto.request.CreateCustomerRequest
import com.canals.orders.dto.request.UpdateCustomerRequest
import com.canals.orders.dto.response.CustomerResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.CustomerService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
) {
    @GetMapping
    fun list(): List<CustomerResponse> = customerService.list().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): CustomerResponse = customerService.getById(id).toResponse()

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateCustomerRequest,
    ): ResponseEntity<CustomerResponse> {
        val customer = customerService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(customer.toResponse())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCustomerRequest,
    ): CustomerResponse = customerService.update(id, request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = customerService.delete(id)
}
