package com.canals.orders.service

import com.canals.orders.domain.Product
import com.canals.orders.dto.request.CreateProductRequest
import com.canals.orders.dto.request.UpdateProductRequest
import com.canals.orders.exception.ProductNotFoundException
import com.canals.orders.exception.ProductsNotFoundException
import com.canals.orders.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<Product> = productRepository.findAll()

    @Transactional(readOnly = true)
    fun getById(id: UUID): Product = productRepository.findById(id).orElseThrow { ProductNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findAllByIds(ids: Collection<UUID>): Map<UUID, Product> {
        val products = productRepository.findAllByIdIn(ids).associateBy { it.id }
        val missing = ids.toSet() - products.keys
        if (missing.isNotEmpty()) throw ProductsNotFoundException(missing)
        return products
    }

    @Transactional
    fun create(request: CreateProductRequest): Product =
        productRepository.save(
            Product(
                id = UUID.randomUUID(),
                sku = request.sku,
                name = request.name,
                unitPrice = request.unitPrice,
            ),
        )

    @Transactional
    fun update(
        id: UUID,
        request: UpdateProductRequest,
    ): Product {
        val existing = getById(id)
        return productRepository.save(
            Product(
                id = existing.id,
                sku = request.sku,
                name = request.name,
                unitPrice = request.unitPrice,
                createdAt = existing.createdAt,
            ),
        )
    }

    @Transactional
    fun delete(id: UUID) {
        if (!productRepository.existsById(id)) throw ProductNotFoundException(id)
        productRepository.deleteById(id)
    }
}
