package com.canals.orders.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

/**
 * Translates exceptions into RFC 7807 Problem Details responses.
 *
 * Why ProblemDetail? Spring 6 ships with first-class support and it's the
 * format most modern API consumers expect. It also keeps the error envelope
 * stable as we add more error types.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val errors =
            ex.bindingResult.fieldErrors.associate {
                it.field to (it.defaultMessage ?: "invalid")
            }
        val pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-error")
        pd.setProperty("errors", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun onConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ProblemDetail> {
        // Triggered by @Validated method-parameter constraints (e.g. the
        // Idempotency-Key @Size on the controller).
        val errors =
            ex.constraintViolations.associate {
                it.propertyPath.toString() to it.message
            }
        val pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-error")
        pd.setProperty("errors", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun onMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(problem(HttpStatus.BAD_REQUEST, "Required header '${ex.headerName}' is missing", "missing-header"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onMalformedJson(ex: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                problem(
                    HttpStatus.BAD_REQUEST,
                    "Malformed or invalid JSON request body",
                    "invalid-json",
                ),
            )

    @ExceptionHandler(
        CustomerNotFoundException::class,
        ProductsNotFoundException::class,
        ProductNotFoundException::class,
        WarehouseNotFoundException::class,
        OrderNotFoundException::class,
    )
    fun onNotFound(ex: DomainException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(problem(HttpStatus.NOT_FOUND, ex.message ?: "Not found", "not-found"))

    @ExceptionHandler(NoEligibleWarehouseException::class)
    fun onNoWarehouse(ex: NoEligibleWarehouseException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                problem(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ex.message ?: "No warehouse can fulfil this order",
                    "no-eligible-warehouse",
                ),
            )

    @ExceptionHandler(DuplicateProductInOrderException::class)
    fun onDuplicateProducts(ex: DuplicateProductInOrderException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                problem(
                    HttpStatus.BAD_REQUEST,
                    ex.message ?: "Duplicate products",
                    "duplicate-products",
                ),
            )

    @ExceptionHandler(PaymentFailedException::class)
    fun onPaymentFailed(ex: PaymentFailedException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.PAYMENT_REQUIRED)
            .body(
                problem(
                    HttpStatus.PAYMENT_REQUIRED,
                    ex.message ?: "Payment declined",
                    "payment-failed",
                ),
            )

    @ExceptionHandler(IdempotencyConflictException::class)
    fun onIdempotencyConflict(ex: IdempotencyConflictException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                problem(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ex.message ?: "Idempotency conflict",
                    "idempotency-conflict",
                ),
            )

    @ExceptionHandler(NoResourceFoundException::class)
    fun onNoResource(ex: NoResourceFoundException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(problem(HttpStatus.NOT_FOUND, "Endpoint not found", "not-found"))

    @ExceptionHandler(Exception::class)
    fun onUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        // Anything we did not anticipate is a 500 — log full stack so SREs
        // can investigate, but don't leak internals to the client.
        log.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                problem(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected server error",
                    "internal-error",
                ),
            )
    }

    private fun problem(
        status: HttpStatus,
        detail: String,
        type: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.type = URI.create("https://canals.example/errors/$type")
            this.title = status.reasonPhrase
        }
}
