package com.v2ray.ang.contracts

import android.app.Service
import android.net.Network

interface ServiceControl {
    /**
     * Gets the service instance.
     * @return The service instance.
     */
    fun getService(): Service

    /**
     * Starts the service.
     */
    fun startService()

    /**
     * Stops the service.
     */
    fun stopService()

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    fun vpnProtect(socket: Int): Boolean

    /**
     * Protects a socket owned by this app process so it bypasses the tunnel interface.
     * The run modes without a TUN return true, matching [vpnProtect].
     *
     * @param socket The socket to protect.
     * @return True if the socket bypasses the tunnel, false otherwise.
     */
    fun vpnProtect(socket: java.net.Socket): Boolean = true

    /**
     * Declares the networks the tunnel runs on top of.
     * Only meaningful for the VPN service, the other run modes have no interface to report.
     *
     * @param networks The upstream networks, null to let the system pick.
     * @return True if the networks were accepted.
     */
    fun setUnderlyingNetworks(networks: Array<Network>?): Boolean = false
}
