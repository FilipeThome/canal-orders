package com.canals.orders.dto.response

import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderItem
import com.canals.orders.domain.OrderStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class OrderResponse(
    val id: UUID,
    val customerId: UUID,
    val warehouseId: UUID,
    val status: OrderStatus,
    val shippingAddress: ShippingAddressResponse,
    val items: List<OrderItemResponse>,
    val totalAmount: BigDecimal,
    val currency: String,
    val cardLast4: String?,
    val paymentId: String?,
    val createdAt: OffsetDateTime,
    val paidAt: OffsetDateTime?,
    val failureReason: String?,
)

data class ShippingAddressResponse(
    val line: String,
)

data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
)

fun OrderItem.toResponse() =
    OrderItemResponse(
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice,
    )

fun Order.toResponse() =
    OrderResponse(
        id = id,
        customerId = customerId,
        warehouseId = warehouseId,
        status = status,
        shippingAddress = ShippingAddressResponse(line = shipAddressLine),
        items = items.map { it.toResponse() },
        totalAmount = totalAmount,
        currency = currency,
        cardLast4 = cardLast4,
        paymentId = paymentId,
        createdAt = createdAt,
        paidAt = paidAt,
        failureReason = failureReason,
    )
