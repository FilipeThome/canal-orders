package com.canals.orders.exception

import java.util.UUID

sealed class DomainException(message: String) : RuntimeException(message)

class CustomerNotFoundException(id: UUID) :
    DomainException("Customer $id not found")

class ProductsNotFoundException(missing: Collection<UUID>) :
    DomainException("Products not found: ${missing.joinToString()}")

class NoEligibleWarehouseException(message: String) :
    DomainException(message)

class PaymentFailedException(reason: String) :
    DomainException("Payment failed: $reason")

class IdempotencyConflictException(key: String) :
    DomainException(
        "Idempotency-Key '$key' was reused with a different request body. " +
            "Either retry with the original body or use a fresh key.",
    )

class DuplicateProductInOrderException(productIds: Collection<UUID>) :
    DomainException(
        "Order contains duplicate product IDs (combine quantities into one line item): " +
            productIds.joinToString(),
    )

class ProductNotFoundException(id: UUID) :
    DomainException("Product $id not found")

class WarehouseNotFoundException(id: UUID) :
    DomainException("Warehouse $id not found")

class OrderNotFoundException(id: UUID) :
    DomainException("Order $id not found")
