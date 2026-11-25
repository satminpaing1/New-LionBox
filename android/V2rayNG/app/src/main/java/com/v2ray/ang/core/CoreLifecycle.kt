package com.v2ray.ang.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serializes core lifecycle work in [CoreServiceManager].
 *
 * A start holds the gate until the core is up, a stop holds it until the native loop has exited,
 * so two taps on Connect cannot launch two Xray instances and a start cannot race a stop that has
 * not released the local port yet.
 */
internal class CoreLifecycleGate {
    private val lock = ReentrantLock()

    @Volatile
    var state: CoreLifecycleState = CoreLifecycleState.STOPPED
        private set

    /**
     * Runs [block] exclusively. A caller that arrives while another transition runs waits for it
     * to finish instead of failing, so a rapid Disconnect → Connect always ends up connected.
     */
    fun <T> runExclusive(block: () -> T): T = lock.withLock(block)

    fun markStarting() {
        state = CoreLifecycleState.STARTING
    }

    fun markRunning() {
        state = CoreLifecycleState.RUNNING
    }

    fun markStopping() {
        state = CoreLifecycleState.STOPPING
    }

    fun markStopped() {
        state = CoreLifecycleState.STOPPED
    }
}

internal enum class CoreLifecycleState { STOPPED, STARTING, RUNNING, STOPPING }

/**
 * Classifies a native start failure so the caller can retry a port clash exactly once.
 */
internal object CoreStartFailure {
    private val addressInUse = Regex("address already in use|bind: address already in use", RegexOption.IGNORE_CASE)

    /**
     * True when the native core reported that its local inbound could not bind.
     */
    fun isAddressInUse(message: String?): Boolean =
        message != null && addressInUse.containsMatchIn(message)

    /**
     * A single retry on a freshly allocated port, never a loop.
     */
    fun shouldRetryWithNewPort(attempt: Int, message: String?): Boolean =
        attempt == 0 && isAddressInUse(message)
}
