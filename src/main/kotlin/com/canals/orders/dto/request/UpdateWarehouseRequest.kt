package com.canals.orders.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateWarehouseRequest(
    @field:NotBlank
    @field:Size(max = 32)
    val code: String,
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: BigDecimal,
    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: BigDecimal,
    @field:NotBlank
    @field:Size(max = 300)
    val address: String,
)
