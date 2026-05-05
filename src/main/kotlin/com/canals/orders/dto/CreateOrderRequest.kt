package com.canals.orders.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateOrderRequest(
    @field:NotNull
    val customerId: UUID,
    @field:NotNull
    @field:Valid
    val shippingAddress: AddressDto,
    @field:NotEmpty
    @field:Size(max = 100, message = "Order may contain at most 100 distinct items")
    @field:Valid
    val items: List<OrderItemDto>,
    @field:NotNull
    @field:Valid
    val payment: PaymentDto,
)

data class AddressDto(
    @field:NotBlank
    @field:Size(max = 300)
    val addressLine: String,
)

data class OrderItemDto(
    @field:NotNull
    val productId: UUID,
    @field:Positive
    @field:NotNull
    val quantity: Int,
)

data class PaymentDto(
    /**
     * Plain card number for the assessment scope only.
     * Stripped of spaces/dashes; we never persist it — only the last 4
     * digits and the gateway's payment id.
     */
    @field:NotBlank
    @field:Pattern(
        regexp = "^(?=(?:.*\\d){13,19}$)[0-9 -]+$",
        message = "cardNumber must contain 13-19 digits and only digits, spaces, or dashes",
    )
    @field:JsonProperty("cardNumber")
    val cardNumber: String,
) {
    val normalizedCardNumber: String
        get() = cardNumber.filter { it.isDigit() }
}
