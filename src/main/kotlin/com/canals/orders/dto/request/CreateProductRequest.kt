package com.canals.orders.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateProductRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val sku: String,
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    @field:NotNull
    @field:Positive
    val unitPrice: BigDecimal,
)
