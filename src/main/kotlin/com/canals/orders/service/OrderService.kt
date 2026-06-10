package com.canals.orders.service

import com.canals.orders.domain.Customer
import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderItem
import com.canals.orders.domain.OrderStatus
import com.canals.orders.domain.Product
import com.canals.orders.domain.Warehouse
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.dto.OrderItemDto
import com.canals.orders.dto.request.UpdateOrderRequest
import com.canals.orders.exception.DuplicateProductInOrderException
import com.canals.orders.exception.NoEligibleWarehouseException
import com.canals.orders.exception.OrderNotFoundException
import com.canals.orders.exception.PaymentFailedException
import com.canals.orders.external.GeoCoordinates
import com.canals.orders.external.GeocodingService
import com.canals.orders.external.PaymentRequest
import com.canals.orders.external.PaymentResult
import com.canals.orders.external.PaymentService
import com.canals.orders.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Order creation pipeline.
 *
 * Steps:
 *   1. Validate inputs (customer exists, products exist, no duplicate lines).
 *   2. Geocode the shipping address.
 *   3. Find warehouses with stock for ALL items, ordered by distance.
 *   4. Lock chosen warehouse stock rows (PESSIMISTIC_WRITE), verify quantities.
 *   5. Persist order as PENDING_PAYMENT; decrement stock.
 *   6. Charge payment gateway. On approval → PAID; on decline → roll back via exception.
 *
 * Stock is reserved before charging: the "reserve then charge" pattern prevents
 * a successful charge with no stock left. Payment failure throws, rolling back
 * both the order and the stock decrement in the same transaction.
 */
@Service
class OrderService(
    private val customerService: CustomerService,
    private val productService: ProductService,
    private val warehouseSelectionService: WarehouseSelectionService,
    private val warehouseStockService: WarehouseStockService,
    private val orderRepository: OrderRepository,
    private val geocodingService: GeocodingService,
    private val paymentService: PaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun listAll(): List<Order> = orderRepository.findAllWithItems()

    @Transactional(readOnly = true)
    fun getById(id: UUID): Order = orderRepository.findByIdWithItems(id) ?: throw OrderNotFoundException(id)

    @Transactional
    fun update(
        id: UUID,
        request: UpdateOrderRequest,
    ): Order =
        getById(id).also { order ->
            order.transitionTo(request.status)
            orderRepository.save(order)
        }

    @Transactional
    fun delete(id: UUID) {
        if (!orderRepository.existsById(id)) throw OrderNotFoundException(id)
        orderRepository.deleteById(id)
    }

    @Transactional
    fun create(request: CreateOrderRequest): Order {
        val customer = customerService.getById(request.customerId)
        validateNoDuplicateProducts(request.items)
        val products = productService.findAllByIds(request.items.map { it.productId })
        val coords = geocodingService.geocode(request.shippingAddress.addressLine)
        val productToQty = request.items.associate { it.productId to it.quantity }

        val candidates =
            warehouseSelectionService.findEligibleOrderedByDistance(
                productIdsToQuantities = productToQty,
                shipLat = coords.latitude.toDouble(),
                shipLng = coords.longitude.toDouble(),
            )
        if (candidates.isEmpty()) {
            throw NoEligibleWarehouseException("No single warehouse can fulfil all requested items in the given quantities.")
        }

        return fulfillWithFirstAvailableWarehouse(candidates, request, customer, products, productToQty, coords)
    }

    private fun validateNoDuplicateProducts(items: List<OrderItemDto>) {
        val duplicates =
            items.map { it.productId }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (duplicates.isNotEmpty()) throw DuplicateProductInOrderException(duplicates)
    }

    private fun fulfillWithFirstAvailableWarehouse(
        candidates: List<Warehouse>,
        request: CreateOrderRequest,
        customer: Customer,
        products: Map<UUID, Product>,
        productToQty: Map<UUID, Int>,
        coords: GeoCoordinates,
    ): Order {
        for (candidate in candidates) {
            when (val reservation = warehouseStockService.tryLock(candidate.id, productToQty)) {
                StockReservation.Unavailable -> {
                    log.warn("Warehouse {} lost feasibility under lock; trying next candidate", candidate.id)
                }
                is StockReservation.Acquired -> {
                    warehouseStockService.decrement(reservation, productToQty)
                    val order = buildOrder(request, customer, candidate, products, coords)
                    orderRepository.save(order)
                    return chargePayment(order, request.payment.normalizedCardNumber)
                }
            }
        }
        throw NoEligibleWarehouseException(
            "Stock for all eligible warehouses was claimed by concurrent orders. Please retry.",
        )
    }

    private fun buildOrder(
        request: CreateOrderRequest,
        customer: Customer,
        warehouse: Warehouse,
        products: Map<UUID, Product>,
        coords: GeoCoordinates,
    ): Order {
        val totalAmount =
            request.items
                .sumOf { line -> products.getValue(line.productId).unitPrice.multiply(BigDecimal(line.quantity)) }
                .setScale(2, RoundingMode.HALF_UP)

        return Order(
            id = UUID.randomUUID(),
            customerId = customer.id,
            warehouseId = warehouse.id,
            status = OrderStatus.PENDING_PAYMENT,
            shipAddressLine = request.shippingAddress.addressLine,
            shipLatitude = coords.latitude,
            shipLongitude = coords.longitude,
            totalAmount = totalAmount,
        ).apply {
            request.items.forEach { line ->
                val product = products.getValue(line.productId)
                items.add(
                    OrderItem(
                        order = this,
                        productId = product.id,
                        productName = product.name,
                        quantity = line.quantity,
                        unitPrice = product.unitPrice,
                    ),
                )
            }
        }
    }

    private fun chargePayment(
        order: Order,
        normalizedCardNumber: String,
    ): Order {
        val result =
            paymentService.charge(
                PaymentRequest(
                    cardNumber = normalizedCardNumber,
                    amount = order.totalAmount,
                    currency = order.currency,
                    description = "Canals order ${order.id}",
                ),
            )
        return when (result) {
            is PaymentResult.Approved -> {
                order.markPaid(result.paymentId, result.cardLast4)
                log.info(
                    "Order {} paid (warehouse={}, amount={} {})",
                    order.id,
                    order.warehouseId,
                    order.totalAmount,
                    order.currency,
                )
                order
            }
            is PaymentResult.Declined -> {
                log.warn("Order {} payment declined (card ...{}): {}", order.id, result.cardLast4, result.reason)
                throw PaymentFailedException(result.reason)
            }
        }
    }
}
