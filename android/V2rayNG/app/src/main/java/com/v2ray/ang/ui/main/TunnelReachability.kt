package com.v2ray.ang.ui.main

import com.v2ray.ang.util.Socks5Endpoint
import com.v2ray.ang.util.TunnelLatencyProbe
import com.v2ray.ang.util.TunnelProbeOutcome

/**
 * Whether the running tunnel can actually reach the public internet. [Blocked] means the core is up
 * and listening locally but a request through it never answered, which the dashboard shows as
 * "Connected (No Internet)".
 */
enum class TunnelReachability {
    Unknown,
    Checking,
    Reachable,
    Blocked,
}

/** Per-attempt budget for the post-connect reachability probe. */
internal const val TUNNEL_CHECK_TIMEOUT_MS = 5_000L

/** One retry, so a flaky first attempt does not raise the warning. */
internal const val TUNNEL_CHECK_ATTEMPTS = 2

/** Connectivity-check endpoints that answer 204/200 with no body. */
internal val TUNNEL_CHECK_URLS = listOf(
    "https://www.gstatic.com/generate_204",
    "https://cp.cloudflare.com",
)

/** The warning is shown only while a tunnel is up and a check has failed. */
internal fun noInternetWarning(reachability: TunnelReachability, isRunning: Boolean): Boolean =
    isRunning && reachability == TunnelReachability.Blocked

/**
 * Probes real HTTP(S) traffic through the core's local SOCKS5 inbound, so success means the whole
 * path (inbound -> outbound -> remote server -> internet) works, not merely that the core listens.
 * Each attempt probes one endpoint and the retry probes the other, so the verdict never takes
 * longer than [TUNNEL_CHECK_ATTEMPTS] x [TUNNEL_CHECK_TIMEOUT_MS].
 */
internal class TunnelReachabilityChecker(
    private val endpoint: () -> Socks5Endpoint?,
    private val probe: (Socks5Endpoint, String) -> TunnelProbeOutcome = { socks, url ->
        TunnelLatencyProbe.measure(socks, url, TUNNEL_CHECK_TIMEOUT_MS)
    },
) {
    fun reachable(): Boolean {
        val socks = endpoint() ?: return false
        repeat(TUNNEL_CHECK_ATTEMPTS) { attempt ->
            val url = TUNNEL_CHECK_URLS[attempt % TUNNEL_CHECK_URLS.size]
            if (probe(socks, url) is TunnelProbeOutcome.Success) return true
        }
        return false
    }
}
