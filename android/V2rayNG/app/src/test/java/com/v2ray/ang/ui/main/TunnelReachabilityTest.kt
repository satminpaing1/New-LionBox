package com.v2ray.ang.ui.main

import com.v2ray.ang.util.Socks5Endpoint
import com.v2ray.ang.util.TunnelProbeOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TunnelReachabilityTest {
    private val socks = Socks5Endpoint("127.0.0.1", 10808)

    private fun checker(attempts: MutableList<String> = mutableListOf(), result: (Int) -> TunnelProbeOutcome) =
        TunnelReachabilityChecker(
            endpoint = { socks },
            probe = { _, url ->
                attempts += url
                result(attempts.size)
            },
        )

    @Test
    fun `warning shows only while connected and blocked`() {
        assertTrue(noInternetWarning(TunnelReachability.Blocked, isRunning = true))
        assertFalse(noInternetWarning(TunnelReachability.Blocked, isRunning = false))
        assertFalse(noInternetWarning(TunnelReachability.Reachable, isRunning = true))
        assertFalse(noInternetWarning(TunnelReachability.Checking, isRunning = true))
        assertFalse(noInternetWarning(TunnelReachability.Unknown, isRunning = true))
    }

    @Test
    fun `first successful url ends the check`() {
        val attempts = mutableListOf<String>()
        val reachable = checker(attempts) { TunnelProbeOutcome.Success(40) }.reachable()

        assertTrue(reachable)
        assertEquals(listOf(TUNNEL_CHECK_URLS.first()), attempts)
    }

    @Test
    fun `a failed first attempt is retried once on the other endpoint and can succeed`() {
        val attempts = mutableListOf<String>()
        val reachable = checker(attempts) { index ->
            if (index == 1) {
                TunnelProbeOutcome.Failure("timeout")
            } else {
                TunnelProbeOutcome.Success(90)
            }
        }.reachable()

        assertTrue(reachable)
        assertEquals(TUNNEL_CHECK_URLS, attempts)
    }

    @Test
    fun `all attempts failing reports blocked after one probe per attempt`() {
        val attempts = mutableListOf<String>()
        val reachable = checker(attempts) { TunnelProbeOutcome.Failure("connection refused") }.reachable()

        assertFalse(reachable)
        assertEquals(TUNNEL_CHECK_ATTEMPTS, attempts.size)
        assertEquals(TUNNEL_CHECK_URLS, attempts)
    }

    @Test
    fun `no local socks inbound means blocked without probing`() {
        var probed = false
        val reachable = TunnelReachabilityChecker(
            endpoint = { null },
            probe = { _, _ ->
                probed = true
                TunnelProbeOutcome.Success(1)
            },
        ).reachable()

        assertFalse(reachable)
        assertFalse(probed)
    }

    @Test
    fun `check endpoints are the no-content ones requested`() {
        assertEquals("https://www.gstatic.com/generate_204", TUNNEL_CHECK_URLS.first())
        assertTrue(TUNNEL_CHECK_URLS.contains("https://cp.cloudflare.com"))
        assertEquals(5_000L, TUNNEL_CHECK_TIMEOUT_MS)
        assertEquals(2, TUNNEL_CHECK_ATTEMPTS)
    }
}
