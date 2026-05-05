package com.canals.orders.controller

import com.canals.orders.dto.response.ProductResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.ProductService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
) {
    @GetMapping
    fun list(): List<ProductResponse> = productService.list().map { it.toResponse() }
}
