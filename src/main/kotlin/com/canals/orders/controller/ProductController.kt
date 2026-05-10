package com.canals.orders.controller

import com.canals.orders.dto.request.CreateProductRequest
import com.canals.orders.dto.request.UpdateProductRequest
import com.canals.orders.dto.response.ProductResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.ProductService
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
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
) {
    @GetMapping
    fun list(): List<ProductResponse> = productService.list().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): ProductResponse = productService.getById(id).toResponse()

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateProductRequest,
    ): ResponseEntity<ProductResponse> {
        val product = productService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(product.toResponse())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ProductResponse = productService.update(id, request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = productService.delete(id)
}
