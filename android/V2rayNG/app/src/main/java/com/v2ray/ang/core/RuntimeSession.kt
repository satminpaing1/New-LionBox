package com.v2ray.ang.core

import com.v2ray.ang.dto.RuntimeTelemetry

internal class RuntimeSession {
    private var startedAt: Long? = null

    @Synchronized
    fun start(nowMillis: Long): Boolean {
        if (startedAt != null) return false
        startedAt = nowMillis
        return true
    }

    @Synchronized
    fun stop() {
        startedAt = null
    }

    @Synchronized
    fun sample(nowMillis: Long, downloadBytes: Long, uploadBytes: Long, intervalMillis: Long): RuntimeTelemetry? {
        val start = startedAt ?: return null
        return RuntimeTelemetry(
            uptimeSeconds = ((nowMillis - start).coerceAtLeast(0) / 1000),
            downloadBytesPerSecond = bytesPerSecond(downloadBytes, intervalMillis),
            uploadBytesPerSecond = bytesPerSecond(uploadBytes, intervalMillis),
        )
    }
}

internal fun bytesPerSecond(bytes: Long, intervalMillis: Long): Long =
    if (bytes <= 0 || intervalMillis <= 0) 0 else (bytes.toDouble() * 1000 / intervalMillis).toLong()

internal fun countryFlag(code: String?): String? {
    val normalized = code?.uppercase(java.util.Locale.ROOT) ?: return null
    if (normalized !in java.util.Locale.getISOCountries()) return null
    return normalized.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
}

internal fun formatUptime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
}
