package com.canals.orders.controller

import com.canals.orders.dto.request.CreateWarehouseRequest
import com.canals.orders.dto.request.UpdateWarehouseRequest
import com.canals.orders.dto.response.WarehouseResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.WarehouseService
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
@RequestMapping("/warehouses")
class WarehouseController(
    private val warehouseService: WarehouseService,
) {
    @GetMapping
    fun list(): List<WarehouseResponse> = warehouseService.list().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): WarehouseResponse = warehouseService.getById(id).toResponse()

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateWarehouseRequest,
    ): ResponseEntity<WarehouseResponse> {
        val warehouse = warehouseService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouse.toResponse())
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWarehouseRequest,
    ): WarehouseResponse = warehouseService.update(id, request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = warehouseService.delete(id)
}
