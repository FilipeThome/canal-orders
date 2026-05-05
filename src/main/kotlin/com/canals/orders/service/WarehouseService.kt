package com.canals.orders.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.repository.WarehouseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WarehouseService(
    private val warehouseRepository: WarehouseRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<Warehouse> = warehouseRepository.findAll()
}
