package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RuntimeSessionTest {
    @Test
    fun `session starts once and resets only on stop`() {
        val session = RuntimeSession()
        assertNull(session.sample(1000, 0, 0, 1000))
        assertTrue(session.start(1000))
        assertFalse(session.start(2000))
        assertEquals(9L, session.sample(10000, 2048, 1024, 2000)?.uptimeSeconds)
        assertEquals(1024L, session.sample(10000, 2048, 1024, 2000)?.downloadBytesPerSecond)
        session.stop()
        session.stop()
        assertNull(session.sample(11000, 10, 10, 1000))
        assertTrue(session.start(12000))
        assertEquals(0L, session.sample(12000, 0, 0, 1000)?.uptimeSeconds)
    }

    @Test
    fun `speed uses elapsed time and handles invalid samples`() {
        assertEquals(2000L, bytesPerSecond(3000, 1500))
        assertEquals(0L, bytesPerSecond(-1, 1000))
        assertEquals(0L, bytesPerSecond(10, 0))
        assertEquals("25:01:01", formatUptime(90061))
        assertEquals("00:00:00", formatUptime(-1))
    }

    @Test
    fun `flag requires an ISO country code rather than a server label`() {
        assertEquals("🇸🇬", countryFlag("sg"))
        assertNull(countryFlag("Singapore"))
        assertNull(countryFlag("ZZ"))
        assertNull(countryFlag(null))
    }
}
