package com.canals.orders.controller

import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.dto.request.UpdateOrderRequest
import com.canals.orders.dto.response.OrderResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.IdempotencyService
import com.canals.orders.service.OrderService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/orders")
@Validated
class OrderController(
    private val idempotencyService: IdempotencyService,
    private val orderService: OrderService,
) {
    @GetMapping
    fun list(): List<OrderResponse> = orderService.listAll().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): OrderResponse = orderService.getById(id).toResponse()

    /**
     * Create a new order.
     * @param idempotencyKey  Optional `Idempotency-Key` header (Stripe-style).
     *                        Recommended for production clients to make
     *                        network retries safe.
     */
    @PostMapping
    fun create(
        @RequestHeader(name = "Idempotency-Key", required = true)
        @Size(max = 120, message = "Idempotency-Key must be at most 120 characters")
        idempotencyKey: String,
        @Valid @RequestBody
        request: CreateOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val result = idempotencyService.createOrder(idempotencyKey, request)
        return ResponseEntity
            .status(HttpStatus.valueOf(result.statusCode))
            .body(result.body.toResponse())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrderRequest,
    ): OrderResponse = orderService.update(id, request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = orderService.delete(id)
}
