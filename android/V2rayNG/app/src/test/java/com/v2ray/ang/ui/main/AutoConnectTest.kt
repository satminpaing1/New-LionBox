package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoConnectTest {
    private val now = 1_700_000_000_000L

    @Test
    fun freshCachedLatencyPicksLowestPing() {
        val entries = listOf(
            CachedLatency("fast", 80L, now - 60_000L),
            CachedLatency("mid", 180L, now - 60_000L),
            CachedLatency("slow", 900L, now - 60_000L),
        )

        assertEquals("fast", fastestCachedGuid(entries, now))
    }

    @Test
    fun staleCachedLatencyIsIgnored() {
        val entries = listOf(
            CachedLatency("stale", 50L, now - AUTO_CONNECT_FAST_CACHE_MS - 1L),
            CachedLatency("fresh", 300L, now - AUTO_CONNECT_FAST_CACHE_MS + 1L),
        )

        assertEquals("fresh", fastestCachedGuid(entries, now))
    }

    @Test
    fun failedAndUntestedServersNeverWin() {
        val entries = listOf(
            CachedLatency("failed", -1L, now - 1_000L),
            CachedLatency("zero", 0L, now - 1_000L),
            CachedLatency("untested", 30L, 0L),
        )

        assertNull(fastestCachedGuid(entries, now))
    }

    @Test
    fun futureTimestampDoesNotCountAsFresh() {
        val entries = listOf(CachedLatency("skewed", 40L, now + 5_000L))

        assertNull(fastestCachedGuid(entries, now))
    }

    @Test
    fun activeGroupIsTheOnlyCandidateSet() {
        val active = listOf("a", "b")
        val all = listOf("a", "b", "c", "d")

        assertEquals(active, autoConnectCandidates(active, all))
    }

    @Test
    fun emptyActiveGroupFallsBackToAllServers() {
        val all = listOf("a", "a", "", "b")

        assertEquals(listOf("a", "b"), autoConnectCandidates(emptyList(), all))
    }

    @Test
    fun fastEnoughThresholdStopsEarly() {
        assertEquals(250L, AUTO_CONNECT_FAST_ENOUGH_MS)
        // The Auto probe is a real URL test through each server's outbound, so it gets the
        // same 3 s budget as the manual test instead of a TCP-connect-sized one.
        assertEquals(3_000L, AUTO_CONNECT_PROBE_TIMEOUT_MS)
        assertEquals(3_000L, AUTO_CONNECT_WAIT_WINDOW_MS)
        assertEquals(15, AUTO_CONNECT_CONCURRENCY)
        assertEquals(10 * 60 * 1000L, AUTO_CONNECT_FAST_CACHE_MS)
    }
}
