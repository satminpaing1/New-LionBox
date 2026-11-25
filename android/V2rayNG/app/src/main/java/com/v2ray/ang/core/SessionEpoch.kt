package com.v2ray.ang.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Numbers every established VPN session so a teardown can tell whether it still owns the session.
 *
 * The OS calls [android.net.VpnService.onRevoke] on the main thread and the core stops on a worker,
 * so a revoke teardown may still be running when the user taps Connect again. A teardown for an
 * older epoch must neither stop the new core nor stop the service the new session lives in.
 */
internal class SessionEpoch {
    private val value = AtomicLong(0)

    /** Starts a new session and returns its epoch. */
    fun begin(): Long = value.incrementAndGet()

    /** The epoch of the newest session. */
    fun current(): Long = value.get()

    /** True while [epoch] is still the newest session. */
    fun isCurrent(epoch: Long): Boolean = value.get() == epoch
}
