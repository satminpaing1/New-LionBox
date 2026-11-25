package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.random.Random

/**
 * Picks a local loopback port that the OS reports as free right now.
 *
 * The candidate comes from binding a temporary socket to port 0 on 127.0.0.1 and reading back the
 * port the kernel assigned, so the choice follows the OS ephemeral allocation instead of a
 * hardcoded or random number another app may already hold. The candidate is then verified for both
 * TCP and UDP, because the Xray SOCKS inbound listens on both.
 */
class FreePortAllocator(
    private val maxAttempts: Int = 8,
    private val pickCandidate: () -> Int? = { candidate() },
    private val isAvailable: (Int) -> Boolean = { isPortFree(it) },
) {
    /**
     * @param excluding ports that must not be returned, e.g. the port already picked for the
     *   other inbound of the same session.
     * @return A port number that was free when checked.
     * @throws IOException when no free port could be found within [maxAttempts].
     */
    fun allocate(excluding: Set<Int> = emptySet()): Int {
        repeat(maxAttempts) {
            val port = pickCandidate()
            if (port != null && port !in excluding && isAvailable(port)) return port
        }
        throw IOException("No free local port after $maxAttempts attempts")
    }

    /**
     * Allocates two distinct free ports, so the SOCKS and HTTP inbounds never collide.
     *
     * A preferred port is honoured only while the OS reports it free: another VPN or proxy app can
     * still hold the configured port after it was revoked, and binding it would kill the core.
     *
     * @param preferredSocks configured SOCKS port, or null to pick a fresh one.
     * @param preferredHttp configured HTTP port, or null to pick a fresh one.
     */
    fun allocatePair(preferredSocks: Int? = null, preferredHttp: Int? = null): Pair<Int, Int> {
        val socks = preferredSocks?.takeIf { it != preferredHttp && isAvailable(it) }
            ?: allocate()
        val http = preferredHttp?.takeIf { it != socks && isAvailable(it) }
            ?: allocate(excluding = setOf(socks))
        if (http == socks) throw IOException("No second free local port after $maxAttempts attempts")
        return socks to http
    }

    companion object {
        private const val MIN_FALLBACK_PORT = 10000
        private const val MAX_PORT = 65535

        /**
         * Asks the kernel for a free ephemeral port on the loopback address.
         */
        fun candidate(): Int? {
            return try {
                ServerSocket(0, 1, InetAddress.getByName(AppConfig.LOOPBACK)).use { it.localPort }
            } catch (_: IOException) {
                Random.nextInt(MIN_FALLBACK_PORT, MAX_PORT)
            }
        }

        /**
         * Checks that nothing holds [port] for TCP and for UDP on the loopback address.
         */
        fun isPortFree(port: Int): Boolean {
            if (port !in 1..MAX_PORT) return false
            val address = InetAddress.getByName(AppConfig.LOOPBACK)
            val tcpFree = try {
                ServerSocket().use { socket ->
                    socket.reuseAddress = false
                    socket.bind(InetSocketAddress(address, port), 1)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (!tcpFree) return false
            return try {
                DatagramSocket(port, address).use { }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
