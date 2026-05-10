package com.canals.orders.dto.request

import com.canals.orders.domain.OrderStatus
import jakarta.validation.constraints.NotNull

data class UpdateOrderRequest(
    @field:NotNull
    val status: OrderStatus,
)
