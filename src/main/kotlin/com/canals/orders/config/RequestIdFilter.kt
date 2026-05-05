package com.canals.orders.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Tags every request with a UUID correlation ID, exposed in:
 *   * the `X-Request-Id` response header (so clients can correlate too),
 *   * SLF4J MDC under key `requestId` (consumed by the log pattern).
 *
 * If the client sends `X-Request-Id`, we honour it (useful for distributed
 * tracing across services).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val incoming = request.getHeader(HEADER)
        val id = if (incoming.isNullOrBlank()) UUID.randomUUID().toString() else incoming
        MDC.put(MDC_KEY, id)
        response.setHeader(HEADER, id)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        private const val HEADER = "X-Request-Id"
        private const val MDC_KEY = "requestId"
    }
}
