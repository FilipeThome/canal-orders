package com.canals.orders.service

import com.canals.orders.domain.Warehouse
import com.canals.orders.dto.request.CreateWarehouseRequest
import com.canals.orders.dto.request.UpdateWarehouseRequest
import com.canals.orders.exception.WarehouseNotFoundException
import com.canals.orders.repository.WarehouseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WarehouseService(
    private val warehouseRepository: WarehouseRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<Warehouse> = warehouseRepository.findAll()

    @Transactional(readOnly = true)
    fun getById(id: UUID): Warehouse = warehouseRepository.findById(id).orElseThrow { WarehouseNotFoundException(id) }

    @Transactional
    fun create(request: CreateWarehouseRequest): Warehouse =
        warehouseRepository.save(
            Warehouse(
                id = UUID.randomUUID(),
                code = request.code,
                name = request.name,
                latitude = request.latitude,
                longitude = request.longitude,
                address = request.address,
            ),
        )

    @Transactional
    fun update(
        id: UUID,
        request: UpdateWarehouseRequest,
    ): Warehouse {
        val existing = getById(id)
        return warehouseRepository.save(
            Warehouse(
                id = existing.id,
                code = request.code,
                name = request.name,
                latitude = request.latitude,
                longitude = request.longitude,
                address = request.address,
                createdAt = existing.createdAt,
            ),
        )
    }

    @Transactional
    fun delete(id: UUID) {
        if (!warehouseRepository.existsById(id)) throw WarehouseNotFoundException(id)
        warehouseRepository.deleteById(id)
    }
}
