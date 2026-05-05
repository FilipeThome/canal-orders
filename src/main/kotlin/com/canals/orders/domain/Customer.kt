package com.canals.orders.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "customers")
class Customer(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
    @Column(name = "email", nullable = false, unique = true, length = 254)
    val email: String,
    @Column(name = "full_name", nullable = false, length = 200)
    val fullName: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
