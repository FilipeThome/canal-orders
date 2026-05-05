package com.canals.orders.service

import com.canals.orders.domain.Order
import com.canals.orders.domain.OrderItem
import com.canals.orders.domain.OrderStatus
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.exception.DuplicateProductInOrderException
import com.canals.orders.exception.NoEligibleWarehouseException
import com.canals.orders.exception.PaymentFailedException
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
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Order creation pipeline.
 *
 * Step-by-step:
 *   1. Validate inputs (customer exists, products exist, no duplicate lines).
 *   2. Geocode the shipping address.
 *   3. Find every warehouse with stock for ALL items, ordered by distance.
 *   4. Lock the chosen warehouse's stock rows (PESSIMISTIC_WRITE) and
 *      verify quantities again — between step 3 and now another tx may
 *      have shipped some inventory.
 *   5. Persist the order in PENDING_PAYMENT and decrement stock.
 *   6. Call the payment gateway. On approval mark PAID; on decline mark
 *      PAYMENT_FAILED and roll back stock changes.
 *
 * Why we decrement stock BEFORE charging: the alternative (charge first,
 * then decrement) means a successful charge with no stock left is possible
 * if a competitor steals the last unit between steps. We prefer the
 * "reserve then charge" pattern; on payment failure we restore stock in a
 * compensating action.
 */
@Service
class OrderService(
    private val customerService: CustomerService,
    private val productService: ProductService,
    private val warehouseSelection: WarehouseSelectionService,
    private val warehouseStockService: WarehouseStockService,
    private val orderRepo: OrderRepository,
    private val geocoding: GeocodingService,
    private val payment: PaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun listAll(): List<Order> = orderRepo.findAllWithItems()

    @Transactional
    fun create(request: CreateOrderRequest): Order {
        // ---- 1. Validate ----
        val customer = customerService.getById(request.customerId)

        val productIds = request.items.map { it.productId }
        val duplicates = productIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicates.isNotEmpty()) throw DuplicateProductInOrderException(duplicates)

        val products = productService.findAllByIds(productIds)

        // ---- 2. Geocode shipping address ----
        val coords = geocoding.geocode(request.shippingAddress.line)

        // ---- 3. Find eligible warehouse, closest first ----
        val productToQty = request.items.associate { it.productId to it.quantity }
        val candidates =
            warehouseSelection.findEligibleOrderedByDistance(
                productIdsToQuantities = productToQty,
                shipLat = coords.latitude.toDouble(),
                shipLng = coords.longitude.toDouble(),
            )
        if (candidates.isEmpty()) {
            throw NoEligibleWarehouseException(
                "No single warehouse can fulfil all requested items in the given quantities.",
            )
        }

        // The query already filters by stock availability, but a concurrent
        // order may invalidate the result before we lock. We try candidates
        // in order; if the first is no longer feasible we move to the next.
        for (candidate in candidates) {
            val warehouseId = candidate.id
            val locked = warehouseStockService.tryLock(warehouseId, productToQty)
            if (locked == null) {
                log.warn(
                    "Warehouse {} lost feasibility under lock; trying next candidate",
                    warehouseId,
                )
                continue
            }

            // ---- 4. Decrement stock ----
            warehouseStockService.decrement(locked, productToQty)

            // ---- 5. Persist order in PENDING_PAYMENT ----
            val totalAmount =
                request.items
                    .map { line -> products.getValue(line.productId).unitPrice.multiply(BigDecimal(line.quantity)) }
                    .reduce(BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)

            val order =
                Order(
                    id = UUID.randomUUID(),
                    customerId = customer.id,
                    warehouseId = warehouseId,
                    status = OrderStatus.PENDING_PAYMENT,
                    shipAddressLine = request.shippingAddress.line,
                    shipLatitude = coords.latitude,
                    shipLongitude = coords.longitude,
                    totalAmount = totalAmount,
                    currency = "USD",
                )
            request.items.forEach { line ->
                val product = products.getValue(line.productId)
                order.items.add(
                    OrderItem(
                        order = order,
                        productId = product.id,
                        productName = product.name,
                        quantity = line.quantity,
                        unitPrice = product.unitPrice,
                    ),
                )
            }
            orderRepo.save(order)

            // ---- 6. Charge the card ----
            val result =
                payment.charge(
                    PaymentRequest(
                        cardNumber = request.payment.normalizedCardNumber,
                        amount = totalAmount,
                        currency = order.currency,
                        description = "Canals order ${order.id}",
                    ),
                )

            return when (result) {
                is PaymentResult.Approved -> {
                    order.status = OrderStatus.PAID
                    order.paymentId = result.paymentId
                    order.cardLast4 = result.cardLast4
                    order.paidAt = OffsetDateTime.now()
                    order.updatedAt = OffsetDateTime.now()
                    log.info(
                        "Order {} paid (warehouse={}, amount={} {})",
                        order.id,
                        warehouseId,
                        totalAmount,
                        order.currency,
                    )
                    order
                }
                is PaymentResult.Declined -> {
                    // Throwing rolls back the entire transaction — including
                    // the order row AND the stock decrement. That's the
                    // compensation. A persisted "PAYMENT_FAILED" order is
                    // arguably useful for audit, but it would leave inventory
                    // committed; we'd need to release it on a separate Tx.
                    // For this assessment we surface the failure to the caller.
                    log.warn(
                        "Order {} payment declined (card …{}): {}",
                        order.id,
                        result.cardLast4,
                        result.reason,
                    )
                    throw PaymentFailedException(result.reason)
                }
            }
        }

        // All candidates lost feasibility under lock.
        throw NoEligibleWarehouseException(
            "Stock for all eligible warehouses was claimed by concurrent orders. " +
                "Please retry.",
        )
    }
}
