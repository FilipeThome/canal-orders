package com.canals.orders.unit.service

import com.canals.orders.domain.Product
import com.canals.orders.exception.ProductsNotFoundException
import com.canals.orders.repository.ProductRepository
import com.canals.orders.service.ProductService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
}
