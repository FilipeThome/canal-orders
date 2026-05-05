package com.canals.orders.controller

import com.canals.orders.dto.response.CustomerResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.CustomerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
) {
    @GetMapping
    fun list(): List<CustomerResponse> = customerService.list().map { it.toResponse() }
}
