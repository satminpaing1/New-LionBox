package com.v2ray.ang.dto

import java.io.Serializable

data class RuntimeTelemetry(
    val uptimeSeconds: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val uploadBytesPerSecond: Long = 0,
    val transport: String = "unknown",
    val ipAddress: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val ipLookupState: String = "loading",
) : Serializable
