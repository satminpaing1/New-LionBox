package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VlessFmtTest {
    @Test
    fun importsVlessTcpProfile() {
        val profile = VlessFmt.parse("vless://12345678-1234-4234-8234-123456789abc@example.com:443?encryption=none&security=tls&type=tcp&sni=example.com#Test")
        assertNotNull(profile)
        assertEquals(EConfigType.VLESS, profile!!.configType)
        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals("12345678-1234-4234-8234-123456789abc", profile.password)
        assertEquals("tls", profile.security)
        assertEquals("example.com", profile.sni)
        assertEquals("Test", profile.remarks)
    }

    @Test
    fun missingTransportQueryIsRejected() {
        assertNull(VlessFmt.parse("vless://12345678-1234-4234-8234-123456789abc@example.com:443"))
    }

    @Test
    fun realityParametersSurviveImport() {
        val profile = VlessFmt.parse("vless://12345678-1234-4234-8234-123456789abc@example.com:443?encryption=none&security=reality&type=tcp&sni=example.org&fp=chrome&pbk=test-public-key&sid=ab12&flow=xtls-rprx-vision#Reality")!!
        assertEquals("reality", profile.security)
        assertEquals("chrome", profile.fingerPrint)
        assertEquals("example.org", profile.sni)
        assertEquals("xtls-rprx-vision", profile.flow)
    }
}
