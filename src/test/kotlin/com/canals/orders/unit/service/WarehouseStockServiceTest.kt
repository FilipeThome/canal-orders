package com.canals.orders.unit.service

import com.canals.orders.domain.WarehouseStock
import com.canals.orders.domain.WarehouseStockId
import com.canals.orders.repository.WarehouseStockRepository
import com.canals.orders.service.StockReservation
import com.canals.orders.service.WarehouseStockService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class WarehouseStockServiceTest {
    private val repo = mockk<WarehouseStockRepository>()
    private val service = WarehouseStockService(repo)

    private fun stock(
        warehouseId: UUID,
        productId: UUID,
        qty: Int,
    ) = WarehouseStock(id = WarehouseStockId(warehouseId, productId), quantity = qty)

    @Test
    fun `tryLock returns Acquired when stock covers requested quantity`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val row = stock(warehouseId, productId, 10)
        every { repo.lockStockForUpdate(warehouseId, setOf(productId)) } returns listOf(row)

        val result = service.tryLock(warehouseId, mapOf(productId to 5))

        assertThat(result).isInstanceOf(StockReservation.Acquired::class.java)
        assertThat((result as StockReservation.Acquired).stockByProduct[productId]).isEqualTo(row)
    }

    @Test
    fun `tryLock returns Acquired when stock exactly meets requested quantity`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val row = stock(warehouseId, productId, 3)
        every { repo.lockStockForUpdate(warehouseId, setOf(productId)) } returns listOf(row)

        assertThat(service.tryLock(warehouseId, mapOf(productId to 3))).isInstanceOf(StockReservation.Acquired::class.java)
    }

    @Test
    fun `tryLock returns Unavailable when stock is below requested quantity`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val row = stock(warehouseId, productId, 2)
        every { repo.lockStockForUpdate(warehouseId, setOf(productId)) } returns listOf(row)

        assertThat(service.tryLock(warehouseId, mapOf(productId to 5))).isEqualTo(StockReservation.Unavailable)
    }

    @Test
    fun `tryLock returns Unavailable when no stock row exists for product`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        every { repo.lockStockForUpdate(warehouseId, setOf(productId)) } returns emptyList()

        assertThat(service.tryLock(warehouseId, mapOf(productId to 1))).isEqualTo(StockReservation.Unavailable)
    }

    @Test
    fun `tryLock returns Unavailable when one product in multi-product request lacks stock`() {
        val warehouseId = UUID.randomUUID()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { repo.lockStockForUpdate(warehouseId, setOf(id1, id2)) } returns
            listOf(
                stock(warehouseId, id1, 10),
                stock(warehouseId, id2, 1),
            )

        assertThat(service.tryLock(warehouseId, mapOf(id1 to 2, id2 to 5))).isEqualTo(StockReservation.Unavailable)
    }

    @Test
    fun `decrement reduces each product quantity by the ordered amount`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val row = stock(warehouseId, productId, 10)
        val reservation = StockReservation.Acquired(mapOf(productId to row))

        service.decrement(reservation, mapOf(productId to 3))

        assertThat(row.quantity).isEqualTo(7)
    }

    @Test
    fun `decrement updates the updatedAt timestamp`() {
        val warehouseId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val row = stock(warehouseId, productId, 10).also { it.updatedAt = OffsetDateTime.now().minusMinutes(1) }
        val before = row.updatedAt
        val reservation = StockReservation.Acquired(mapOf(productId to row))

        service.decrement(reservation, mapOf(productId to 1))

        assertThat(row.updatedAt).isAfter(before)
    }

    @Test
    fun `decrement handles multiple products independently`() {
        val warehouseId = UUID.randomUUID()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val row1 = stock(warehouseId, id1, 20)
        val row2 = stock(warehouseId, id2, 15)
        val reservation = StockReservation.Acquired(mapOf(id1 to row1, id2 to row2))

        service.decrement(reservation, mapOf(id1 to 5, id2 to 10))

        assertThat(row1.quantity).isEqualTo(15)
        assertThat(row2.quantity).isEqualTo(5)
    }
}
