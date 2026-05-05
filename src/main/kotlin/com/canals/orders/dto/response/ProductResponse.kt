package com.canals.orders.dto.response

import com.canals.orders.domain.Product
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class ProductResponse(
    val id: UUID,
    val sku: String,
    val name: String,
    val unitPrice: BigDecimal,
    val createdAt: OffsetDateTime,
)

fun Product.toResponse() =
    ProductResponse(
        id = id,
        sku = sku,
        name = name,
        unitPrice = unitPrice,
        createdAt = createdAt,
    )
