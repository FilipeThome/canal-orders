package com.canals.orders.external

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Geocoding result: a (latitude, longitude) pair.
 */
data class GeoCoordinates(val latitude: BigDecimal, val longitude: BigDecimal)

/**
 * Address → lat/lng resolver. In production this would wrap Google Maps,
 * Mapbox, or HERE; here it's a stable hash-based mock so the same address
 * always yields the same coordinates.
 */
interface GeocodingService {
    fun geocode(address: String): GeoCoordinates
}

@Component
class MockGeocodingService(
    @Value("\${external.geocoding.seed:42}") private val seed: Long,
) : GeocodingService {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Deterministic mock: derive coords from a stable hash of the address,
     * mapped into the contiguous USA bounding box. Same address → same
     * coords across restarts, which matters for idempotency tests.
     *
     * We split the hash into two independent halves (one for lat, one for
     * lng) so the two coordinates are decorrelated.
     */
    override fun geocode(address: String): GeoCoordinates {
        val normalised = address.trim().lowercase()
        val rawHash = (normalised.hashCode().toLong() xor seed)
        val latHash = (rawHash and 0xffffffffL)
        val lngHash = (rawHash ushr 16) and 0xffffffffL

        // Continental USA bounding box (rough): lat 25..49, lng -125..-67.
        val lat = 25.0 + (latHash % 24_000_000L).toDouble() / 1_000_000.0
        val lng = -125.0 + (lngHash % 58_000_000L).toDouble() / 1_000_000.0

        val coords =
            GeoCoordinates(
                latitude = BigDecimal(lat).setScale(6, RoundingMode.HALF_UP),
                longitude = BigDecimal(lng).setScale(6, RoundingMode.HALF_UP),
            )
        log.debug("Geocoded '{}' → {}", normalised, coords)
        return coords
    }
}
