package com.v2ray.ang.util

import java.net.Socket

/**
 * Lets the running VPN service protect sockets opened by the app process.
 *
 * While a TUN interface is up, a plain socket the app creates can be routed back into that tunnel
 * and the connection loops. Test sockets that must reach the internet directly are handed to
 * `VpnService.protect()` through this hook, which [com.v2ray.ang.core.CoreServiceManager] binds
 * for the lifetime of the core loop.
 */
internal object SocketProtector {
    @Volatile
    var protector: ((Socket) -> Boolean)? = null

    /**
     * @return True when the socket is known to bypass the tunnel (or no tunnel is up).
     */
    fun protect(socket: Socket): Boolean = protector?.invoke(socket) ?: true

    fun clear() {
        protector = null
    }
}
