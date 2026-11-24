package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    /** Wall-clock time of the last real-ping result; 0 when the server was never tested. */
    var testTimeMillis: Long = 0L,
)
