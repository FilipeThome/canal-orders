package com.canals.orders.dto.response

import com.canals.orders.domain.Customer
import java.time.OffsetDateTime
import java.util.UUID

data class CustomerResponse(
    val id: UUID,
    val email: String,
    val fullName: String,
    val createdAt: OffsetDateTime,
)

fun Customer.toResponse() =
    CustomerResponse(
        id = id,
        email = email,
        fullName = fullName,
        createdAt = createdAt,
    )
