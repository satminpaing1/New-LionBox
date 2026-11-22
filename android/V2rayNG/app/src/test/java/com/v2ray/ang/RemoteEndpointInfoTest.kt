package com.v2ray.ang

import com.v2ray.ang.handler.SpeedtestManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RemoteEndpointInfoTest {
    @Test
    fun `IP API response provides IP country code and full name`() {
        val endpoint = SpeedtestManager.parseRemoteIPInfo("""{"ip":"203.0.113.10","country_code":"SG","country":"Singapore"}""")
        assertEquals("203.0.113.10", endpoint?.ipAddress)
        assertEquals("SG", endpoint?.countryCode)
        assertEquals("Singapore", endpoint?.countryName)
    }

    @Test
    fun `country name alone does not invent a flag`() {
        val endpoint = SpeedtestManager.parseRemoteIPInfo("""{"ip":"203.0.113.10","country":"Singapore"}""")
        assertNull(endpoint?.countryCode)
        assertEquals("Singapore", endpoint?.countryName)
    }

    @Test
    fun `invalid country code and empty IP response remain unavailable`() {
        assertNull(SpeedtestManager.parseRemoteIPInfo("""{"ip":"203.0.113.10","country_code":"ZZ"}""")?.countryCode)
        assertNull(SpeedtestManager.parseRemoteIPInfo("{}"))
    }
}
