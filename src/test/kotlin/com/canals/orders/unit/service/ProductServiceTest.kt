package com.canals.orders.unit.service

import com.canals.orders.domain.Product
import com.canals.orders.dto.request.CreateProductRequest
import com.canals.orders.dto.request.UpdateProductRequest
import com.canals.orders.exception.ProductNotFoundException
import com.canals.orders.exception.ProductsNotFoundException
import com.canals.orders.repository.ProductRepository
import com.canals.orders.service.ProductService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ProductServiceTest {
    private val productRepository = mockk<ProductRepository>()
    private val service = ProductService(productRepository)

    private fun product(id: UUID = UUID.randomUUID()) =
        Product(id = id, sku = "SKU-${id.toString().take(8)}", name = "Test Product", unitPrice = BigDecimal("9.99"))

    @Test
    fun `list returns all products`() {
        val expected = listOf(product(), product())
        every { productRepository.findAll() } returns expected

        assertThat(service.list()).isEqualTo(expected)
    }

    @Test
    fun `getById returns product when found`() {
        val id = UUID.randomUUID()
        val expected = product(id)
        every { productRepository.findById(id) } returns Optional.of(expected)

        assertThat(service.getById(id)).isEqualTo(expected)
    }

    @Test
    fun `getById throws ProductNotFoundException when not found`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.getById(id) }
            .isInstanceOf(ProductNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }

    @Test
    fun `findAllByIds returns map keyed by product id`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val p1 = product(id1)
        val p2 = product(id2)
        every { productRepository.findAllByIdIn(listOf(id1, id2)) } returns listOf(p1, p2)

        val result = service.findAllByIds(listOf(id1, id2))

        assertThat(result).containsEntry(id1, p1).containsEntry(id2, p2)
    }

    @Test
    fun `findAllByIds throws ProductsNotFoundException listing the missing ids`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { productRepository.findAllByIdIn(listOf(id1, id2)) } returns listOf(product(id1))

        assertThatThrownBy { service.findAllByIds(listOf(id1, id2)) }
            .isInstanceOf(ProductsNotFoundException::class.java)
            .hasMessageContaining(id2.toString())
    }

    @Test
    fun `findAllByIds throws when all ids are unknown`() {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { productRepository.findAllByIdIn(ids) } returns emptyList()

        assertThatThrownBy { service.findAllByIds(ids) }
            .isInstanceOf(ProductsNotFoundException::class.java)
    }

    @Test
    fun `create saves new product with provided fields`() {
        val request = CreateProductRequest(sku = "SKU-NEW", name = "New Widget", unitPrice = BigDecimal("29.99"))
        every { productRepository.save(any()) } answers { firstArg() }

        val result = service.create(request)

        assertThat(result.sku).isEqualTo("SKU-NEW")
        assertThat(result.name).isEqualTo("New Widget")
        assertThat(result.unitPrice).isEqualByComparingTo("29.99")
        assertThat(result.id).isNotNull()
        verify(exactly = 1) { productRepository.save(any()) }
    }

    @Test
    fun `update saves updated fields preserving id and createdAt`() {
        val id = UUID.randomUUID()
        val existing = product(id)
        val request = UpdateProductRequest(sku = "SKU-UPD", name = "Updated", unitPrice = BigDecimal("49.99"))
        every { productRepository.findById(id) } returns Optional.of(existing)
        every { productRepository.save(any()) } answers { firstArg() }

        val result = service.update(id, request)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.sku).isEqualTo("SKU-UPD")
        assertThat(result.name).isEqualTo("Updated")
        assertThat(result.unitPrice).isEqualByComparingTo("49.99")
        assertThat(result.createdAt).isEqualTo(existing.createdAt)
    }

    @Test
    fun `update throws ProductNotFoundException when product does not exist`() {
        val id = UUID.randomUUID()
        val request = UpdateProductRequest(sku = "SKU-X", name = "X", unitPrice = BigDecimal("1.00"))
        every { productRepository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.update(id, request) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun `delete calls deleteById when product exists`() {
        val id = UUID.randomUUID()
        every { productRepository.existsById(id) } returns true
        every { productRepository.deleteById(id) } just runs

        service.delete(id)

        verify(exactly = 1) { productRepository.deleteById(id) }
    }

    @Test
    fun `delete throws ProductNotFoundException when product does not exist`() {
        val id = UUID.randomUUID()
        every { productRepository.existsById(id) } returns false

        assertThatThrownBy { service.delete(id) }
            .isInstanceOf(ProductNotFoundException::class.java)
            .hasMessageContaining(id.toString())
    }
}
