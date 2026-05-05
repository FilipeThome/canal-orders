package com.canals.orders.repository

import com.canals.orders.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String>
