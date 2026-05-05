package com.canals.orders.service

import com.canals.orders.domain.Product
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
    fun findAllByIds(ids: Collection<UUID>): Map<UUID, Product> {
        val products = productRepository.findAllByIdIn(ids).associateBy { it.id }
        val missing = ids.toSet() - products.keys
        if (missing.isNotEmpty()) throw ProductsNotFoundException(missing)
        return products
    }
}
