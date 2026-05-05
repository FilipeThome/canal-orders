package com.canals.orders.controller

import com.canals.orders.dto.response.WarehouseResponse
import com.canals.orders.dto.response.toResponse
import com.canals.orders.service.WarehouseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/warehouses")
class WarehouseController(
    private val warehouseService: WarehouseService,
) {
    @GetMapping
    fun list(): List<WarehouseResponse> = warehouseService.list().map { it.toResponse() }
}
