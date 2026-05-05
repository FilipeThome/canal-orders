package com.canals.orders.domain

enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    CANCELLED,
}
