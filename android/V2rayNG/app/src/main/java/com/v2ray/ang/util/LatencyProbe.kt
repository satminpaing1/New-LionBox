package com.v2ray.ang.util

import java.util.concurrent.CancellationException

internal object LatencyProbe {
    fun measure(primaryUrl: String, fallbackUrl: String, probe: (String) -> Long): Long {
        val urls = listOf(primaryUrl, fallbackUrl, "https://cp.cloudflare.com/generate_204").distinct()
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
