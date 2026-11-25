package com.v2ray.ang.handler

/**
 * Picks the local ports one VPN session binds.
 *
 * A configured port is reused only while the OS reports it free. When another VPN app is revoked
 * it can still hold its local inbound (classically 10808) for a moment, and binding that port then
 * makes the Xray core fail with "address already in use". In every other case a freshly allocated
 * free pair is used, so a session never starts on a port somebody else holds.
 */
internal object SessionPortPolicy {

    /**
     * @param preferredSocks configured SOCKS port, or null when the session is fully dynamic.
     * @param preferredHttp configured HTTP port, or null when the session is fully dynamic.
     * @param isFree whether a port can be bound on loopback for TCP and UDP right now.
     * @param allocateFreePair allocates a pair of distinct free ports.
     * @return the SOCKS/HTTP pair for this session; the two may be equal when Xray serves both
     *   protocols from one inbound.
     */
    fun resolve(
        preferredSocks: Int?,
        preferredHttp: Int?,
        isFree: (Int) -> Boolean,
        allocateFreePair: () -> Pair<Int, Int>,
    ): Pair<Int, Int> {
        val freeSocksPreferred = preferredSocks?.takeIf { it > 0 && isFree(it) }
        val freeHttpPreferred = preferredHttp?.takeIf { it > 0 && isFree(it) }
        if (freeSocksPreferred != null && freeHttpPreferred != null) {
            return freeSocksPreferred to freeHttpPreferred
        }

        val (freeSocks, freeHttp) = allocateFreePair()
        val socks = freeSocksPreferred ?: freeSocks
        val http = freeHttpPreferred?.takeIf { it != socks }
            ?: listOf(freeHttp, freeSocks).first { it != socks }
        return socks to http
    }
}
