package com.canals.orders.unit.service

import com.canals.orders.domain.OrderStatus
import com.canals.orders.domain.Warehouse
import com.canals.orders.domain.WarehouseStock
import com.canals.orders.domain.WarehouseStockId
import com.canals.orders.dto.AddressDto
import com.canals.orders.dto.CreateOrderRequest
import com.canals.orders.dto.OrderItemDto
import com.canals.orders.dto.PaymentDto
import com.canals.orders.exception.DuplicateProductInOrderException
import com.canals.orders.exception.NoEligibleWarehouseException
import com.canals.orders.exception.PaymentFailedException
import com.canals.orders.external.GeoCoordinates
import com.canals.orders.external.GeocodingService
import com.canals.orders.external.PaymentRequest
import com.canals.orders.external.PaymentResult
import com.canals.orders.external.PaymentService
import com.canals.orders.repository.OrderRepository
import com.canals.orders.service.CustomerService
import com.canals.orders.service.OrderService
import com.canals.orders.service.ProductService
import com.canals.orders.service.WarehouseSelectionService
import com.canals.orders.service.WarehouseStockService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class OrderServiceTest {
    private val customerService = mockk<CustomerService>()
    private val productService = mockk<ProductService>()
    private val warehouseSelectionService = mockk<WarehouseSelectionService>()
    private val warehouseStockService = mockk<WarehouseStockService>()
    private val orderRepository = mockk<OrderRepository>()
    private val geocodingService = mockk<GeocodingService>()
    private val paymentService = mockk<PaymentService>()

    private val service =
        OrderService(
            customerService = customerService,
            productService = productService,
            warehouseSelectionService = warehouseSelectionService,
            warehouseStockService = warehouseStockService,
            orderRepository = orderRepository,
            geocodingService = geocodingService,
            paymentService = paymentService,
        )

    private val customerId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val warehouseId = UUID.randomUUID()

    private fun request(items: List<OrderItemDto> = listOf(OrderItemDto(productId = productId, quantity = 2))) =
        CreateOrderRequest(
            customerId = customerId,
            shippingAddress = AddressDto("123 Main St, New York, NY"),
            items = items,
            payment = PaymentDto("4111111111111111"),
        )

    private fun setupHappyPath() {
        val customer =
            com.canals.orders.domain.Customer(
                id = customerId,
                email = "test@example.com",
                fullName = "Test User",
            )
        val product =
            com.canals.orders.domain.Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                unitPrice = BigDecimal("99.99"),
            )
        val warehouse =
            Warehouse(
                id = warehouseId,
                code = "WH-TEST",
                name = "Test Warehouse",
                latitude = BigDecimal("40.712776"),
                longitude = BigDecimal("-74.005974"),
                address = "1 Test Way",
            )
        val locked =
            mapOf(
                productId to
                    WarehouseStock(
                        id = WarehouseStockId(warehouseId, productId),
                        quantity = 100,
                    ),
            )
        every { customerService.getById(customerId) } returns customer
        every { productService.findAllByIds(listOf(productId)) } returns mapOf(productId to product)
        every { geocodingService.geocode(any()) } returns GeoCoordinates(BigDecimal("40.712776"), BigDecimal("-74.005974"))
        every { warehouseSelectionService.findEligibleOrderedByDistance(any(), any(), any()) } returns listOf(warehouse)
        every { warehouseStockService.tryLock(warehouseId, any()) } returns locked
        every { warehouseStockService.decrement(any(), any()) } just runs
        every { orderRepository.save(any()) } answers { firstArg() }
        every { paymentService.charge(any()) } returns PaymentResult.Approved("pay_test_123", "1111")
    }

    @Test
    fun `create returns PAID order with correct fields on successful payment`() {
        setupHappyPath()

        val order = service.create(request())

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(order.paymentId).isEqualTo("pay_test_123")
        assertThat(order.cardLast4).isEqualTo("1111")
        assertThat(order.customerId).isEqualTo(customerId)
        assertThat(order.warehouseId).isEqualTo(warehouseId)
    }

    @Test
    fun `create computes total amount as sum of unit price times quantity`() {
        setupHappyPath()

        val order = service.create(request())

        // 99.99 * 2 = 199.98
        assertThat(order.totalAmount).isEqualByComparingTo("199.98")
    }

    @Test
    fun `create populates order items from request lines`() {
        setupHappyPath()

        val order = service.create(request())

        assertThat(order.items).hasSize(1)
        val item = order.items.first()
        assertThat(item.productId).isEqualTo(productId)
        assertThat(item.productName).isEqualTo("Test Product")
        assertThat(item.quantity).isEqualTo(2)
        assertThat(item.unitPrice).isEqualByComparingTo("99.99")
    }

    @Test
    fun `create sends correct payment request to payment service`() {
        setupHappyPath()
        val captured = slot<PaymentRequest>()
        every { paymentService.charge(capture(captured)) } returns PaymentResult.Approved("pay_xyz", "1111")

        service.create(request())

        assertThat(captured.captured.cardNumber).isEqualTo("4111111111111111")
        assertThat(captured.captured.amount).isEqualByComparingTo("199.98")
        assertThat(captured.captured.currency).isEqualTo("USD")
    }

    @Test
    fun `create throws DuplicateProductInOrderException when request has duplicate product ids`() {
        val customer = com.canals.orders.domain.Customer(id = customerId, email = "x@x.com", fullName = "X")
        every { customerService.getById(customerId) } returns customer
        val dupItems =
            listOf(
                OrderItemDto(productId = productId, quantity = 1),
                OrderItemDto(productId = productId, quantity = 2),
            )

        assertThatThrownBy { service.create(request(items = dupItems)) }
            .isInstanceOf(DuplicateProductInOrderException::class.java)
            .hasMessageContaining(productId.toString())
    }

    @Test
    fun `create throws NoEligibleWarehouseException when selection service returns empty list`() {
        val customer = com.canals.orders.domain.Customer(id = customerId, email = "x@x.com", fullName = "X")
        val product =
            com.canals.orders.domain.Product(
                id = productId,
                sku = "SKU-001",
                name = "Prod",
                unitPrice = BigDecimal("9.99"),
            )
        every { customerService.getById(customerId) } returns customer
        every { productService.findAllByIds(any()) } returns mapOf(productId to product)
        every { geocodingService.geocode(any()) } returns GeoCoordinates(BigDecimal("40.0"), BigDecimal("-74.0"))
        every { warehouseSelectionService.findEligibleOrderedByDistance(any(), any(), any()) } returns emptyList()

        assertThatThrownBy { service.create(request()) }
            .isInstanceOf(NoEligibleWarehouseException::class.java)
            .hasMessageContaining("No single warehouse")
    }

    @Test
    fun `create throws PaymentFailedException when payment is declined`() {
        setupHappyPath()
        every { paymentService.charge(any()) } returns PaymentResult.Declined("Insufficient funds", "1111")

        assertThatThrownBy { service.create(request()) }
            .isInstanceOf(PaymentFailedException::class.java)
            .hasMessageContaining("Insufficient funds")
    }

    @Test
    fun `create tries next candidate warehouse when first loses stock under lock`() {
        val warehouseId2 = UUID.randomUUID()
        val customer = com.canals.orders.domain.Customer(id = customerId, email = "x@x.com", fullName = "X")
        val product =
            com.canals.orders.domain.Product(
                id = productId,
                sku = "SKU-001",
                name = "Prod",
                unitPrice = BigDecimal("10.00"),
            )
        val wh1 =
            Warehouse(
                id = warehouseId,
                code = "WH-1",
                name = "WH1",
                latitude = BigDecimal("40.0"),
                longitude = BigDecimal("-74.0"),
                address = "addr",
            )
        val wh2 =
            Warehouse(
                id = warehouseId2,
                code = "WH-2",
                name = "WH2",
                latitude = BigDecimal("34.0"),
                longitude = BigDecimal("-118.0"),
                address = "addr2",
            )
        val locked2 = mapOf(productId to WarehouseStock(id = WarehouseStockId(warehouseId2, productId), quantity = 50))
        every { customerService.getById(customerId) } returns customer
        every { productService.findAllByIds(any()) } returns mapOf(productId to product)
        every { geocodingService.geocode(any()) } returns GeoCoordinates(BigDecimal("40.0"), BigDecimal("-74.0"))
        every { warehouseSelectionService.findEligibleOrderedByDistance(any(), any(), any()) } returns listOf(wh1, wh2)
        every { warehouseStockService.tryLock(warehouseId, any()) } returns null
        every { warehouseStockService.tryLock(warehouseId2, any()) } returns locked2
        every { warehouseStockService.decrement(any(), any()) } just runs
        every { orderRepository.save(any()) } answers { firstArg() }
        every { paymentService.charge(any()) } returns PaymentResult.Approved("pay_wh2", "1111")

        val order = service.create(request())

        assertThat(order.warehouseId).isEqualTo(warehouseId2)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun `create throws NoEligibleWarehouseException when all candidates lose stock under lock`() {
        val customer = com.canals.orders.domain.Customer(id = customerId, email = "x@x.com", fullName = "X")
        val product =
            com.canals.orders.domain.Product(
                id = productId,
                sku = "SKU-001",
                name = "Prod",
                unitPrice = BigDecimal("9.99"),
            )
        val warehouse =
            Warehouse(
                id = warehouseId,
                code = "WH-TEST",
                name = "WH",
                latitude = BigDecimal("40.0"),
                longitude = BigDecimal("-74.0"),
                address = "addr",
            )
        every { customerService.getById(customerId) } returns customer
        every { productService.findAllByIds(any()) } returns mapOf(productId to product)
        every { geocodingService.geocode(any()) } returns GeoCoordinates(BigDecimal("40.0"), BigDecimal("-74.0"))
        every { warehouseSelectionService.findEligibleOrderedByDistance(any(), any(), any()) } returns listOf(warehouse)
        every { warehouseStockService.tryLock(warehouseId, any()) } returns null

        assertThatThrownBy { service.create(request()) }
            .isInstanceOf(NoEligibleWarehouseException::class.java)
            .hasMessageContaining("concurrent")
    }

    @Test
    fun `listAll delegates to repository`() {
        every { orderRepository.findAllWithItems() } returns emptyList()

        val result = service.listAll()

        assertThat(result).isEmpty()
        verify(exactly = 1) { orderRepository.findAllWithItems() }
    }
}
