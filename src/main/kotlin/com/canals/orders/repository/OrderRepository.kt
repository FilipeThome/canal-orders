package com.canals.orders.repository

import com.canals.orders.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    fun findByIdWithItems(
        @Param("id") id: UUID,
    ): Order?

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items")
    fun findAllWithItems(): List<Order>
}
