package com.canals.orders.repository

import com.canals.orders.domain.Product
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID> {
    fun findAllByIdIn(ids: Collection<UUID>): List<Product>
}
