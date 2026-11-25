package com.v2ray.ang.dto

import java.io.Serializable

data class TestServiceMessage(
    val key: Int,
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList(),
    /**
     * Retained for IPC/serialization compatibility with a message queued by an older build.
     * Every latency test is a real-delay test now, so the field is ignored.
     */
    val onlyTcp: Boolean = false,
    /** Probes in flight at once; 0 uses the user's real-ping concurrency setting. */
    val concurrency: Int = 0,
    /** Per-server probe budget in milliseconds; 0 keeps the legacy unbounded probe. */
    val probeTimeoutMillis: Long = 0L
) : Serializable

