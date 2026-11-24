package com.v2ray.ang.util

import java.util.concurrent.CancellationException

internal object LatencyProbe {
    /**
     * Probes the configured URLs in order and returns the first non-negative delay.
     *
     * @param maxUrls how many URLs may be tried; auto-connect uses 1 so a single server
     * cannot spend the whole per-server budget on the fallback chain.
     */
    fun measure(primaryUrl: String, fallbackUrl: String, maxUrls: Int = 3, probe: (String) -> Long): Long {
        val urls = listOf(primaryUrl, fallbackUrl, "https://cp.cloudflare.com/generate_204")
            .distinct()
            .take(maxUrls.coerceAtLeast(1))
        for (url in urls) {
            val delay = try {
                probe(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                -1L
            }
            if (delay >= 0) return delay
        }
        return -1L
    }
}
