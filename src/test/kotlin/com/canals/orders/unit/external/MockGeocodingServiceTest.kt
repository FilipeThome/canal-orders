package com.canals.orders.unit.external

import com.canals.orders.external.MockGeocodingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MockGeocodingServiceTest {
    private val service = MockGeocodingService(seed = 42L)

    @Test
    fun `same address always returns identical coordinates`() {
        val address = "123 Main St, New York, NY"

        val first = service.geocode(address)
        val second = service.geocode(address)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different addresses produce different coordinates`() {
        val nyCoords = service.geocode("123 Main St, New York, NY")
        val laCoords = service.geocode("500 Oak Ave, Los Angeles, CA")

        assertThat(nyCoords).isNotEqualTo(laCoords)
    }

    @Test
    fun `coordinates fall within continental USA bounding box`() {
        val coords = service.geocode("1600 Pennsylvania Ave NW, Washington, DC")

        assertThat(coords.latitude.toDouble()).isBetween(25.0, 49.0)
        assertThat(coords.longitude.toDouble()).isBetween(-125.0, -67.0)
    }

    @Test
    fun `geocoding is case and leading-trailing-whitespace insensitive`() {
        val padded = service.geocode("  Chicago, IL  ")
        val lower = service.geocode("chicago, il")

        assertThat(padded).isEqualTo(lower)
    }

    @Test
    fun `result coordinates have exactly 6 decimal places`() {
        val coords = service.geocode("Seattle, WA")

        assertThat(coords.latitude.scale()).isEqualTo(6)
        assertThat(coords.longitude.scale()).isEqualTo(6)
    }

    @Test
    fun `seed affects output so different seeds yield different coordinates`() {
        val other = MockGeocodingService(seed = 99L)

        val coordsDefault = service.geocode("Dallas, TX")
        val coordsOther = other.geocode("Dallas, TX")

        assertThat(coordsDefault).isNotEqualTo(coordsOther)
    }
}
