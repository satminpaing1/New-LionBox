package com.v2ray.ang

import com.v2ray.ang.util.LatencyProbe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class LatencyProbeTest {
    @Test
    fun `primary success does not run fallback`() {
        val calls = mutableListOf<String>()
        assertEquals(0L, LatencyProbe.measure("primary", "fallback") { calls.add(it); 0L })
        assertEquals(listOf("primary"), calls)
    }

    @Test
    fun `failed primary tries configured fallback`() {
        val calls = mutableListOf<String>()
        assertEquals(42L, LatencyProbe.measure("primary", "fallback") {
            calls.add(it)
            if (it == "primary") -2L else 42L
        })
        assertEquals(listOf("primary", "fallback"), calls)
    }

    @Test
    fun `blocked Google endpoints try independent endpoint`() {
        val calls = mutableListOf<String>()
        assertEquals(65L, LatencyProbe.measure("primary", "fallback") {
            calls.add(it)
            if (it == "https://cp.cloudflare.com/generate_204") 65L else throw IOException()
        })
        assertEquals(listOf("primary", "fallback", "https://cp.cloudflare.com/generate_204"), calls)
    }

    @Test
    fun `duplicate endpoints are not retried and all failures remain failure`() {
        val calls = mutableListOf<String>()
        assertEquals(-1L, LatencyProbe.measure("primary", "primary") { calls.add(it); -1L })
        assertEquals(listOf("primary", "https://cp.cloudflare.com/generate_204"), calls)
    }

    @Test
    fun `cancellation does not start more network requests`() {
        val calls = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            LatencyProbe.measure("primary", "fallback") { calls.add(it); throw CancellationException() }
        }
        assertEquals(listOf("primary"), calls)
    }
}
