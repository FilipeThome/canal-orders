package com.canals.orders.dto.response

import com.canals.orders.domain.Warehouse
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class WarehouseResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val address: String,
    val createdAt: OffsetDateTime,
)

fun Warehouse.toResponse() =
    WarehouseResponse(
        id = id,
        code = code,
        name = name,
        latitude = latitude,
        longitude = longitude,
        address = address,
        createdAt = createdAt,
    )
