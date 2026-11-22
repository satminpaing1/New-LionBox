package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LionBoxConsoleTest {
    @Test
    fun `server addresses in visible names are masked without changing descriptive text`() {
        assertEquals("Singapore ******", maskServerAddress("Singapore vpn.example.com", "vpn.example.com"))
        assertEquals("******", maskServerAddress("VPN.EXAMPLE.COM", "vpn.example.com"))
        assertEquals("Singapore", maskServerAddress("Singapore", null))
    }

    @Test
    fun `connected dashboard uses live latency instead of saved profile latency`() {
        val status = MainStatus.ConnectionTest(com.v2ray.ang.dto.ConnectionTestResult(42))
        assertEquals(42L, dashboardLatencyMillis(status, true, 180))
        assertEquals(180L, dashboardLatencyMillis(status, false, 180))
    }

    @Test
    fun `live failures and zero latency are preserved`() {
        assertEquals(-1L, dashboardLatencyMillis(MainStatus.ConnectionTest(com.v2ray.ang.dto.ConnectionTestResult(-1)), true, 80))
        assertEquals(0L, dashboardLatencyMillis(MainStatus.ConnectionTest(com.v2ray.ang.dto.ConnectionTestResult(0)), true, 80))
    }

    @Test
    fun `unmeasured and testing connections do not show stale saved latency`() {
        assertNull(dashboardLatencyMillis(MainStatus.Testing, true, 80))
        assertNull(dashboardLatencyMillis(MainStatus.Connected, true, 80))
        assertNull(dashboardLatencyMillis(MainStatus.Connected, false, null))
    }

    @Test
    fun newInstallCannotStartWithoutASelectedProfile() {
        assertNull(connectionAction(hasSelection = false, isRunning = false))
    }

    @Test
    fun selectedProfileCanStartTheExistingService() {
        assertEquals(MainAction.ToggleService, connectionAction(hasSelection = true, isRunning = false))
    }

    @Test
    fun runningServiceCanStopEvenWhenTheSelectedProfileWasRemoved() {
        assertEquals(MainAction.ToggleService, connectionAction(hasSelection = false, isRunning = true))
        assertEquals(MainAction.ToggleService, connectionAction(hasSelection = true, isRunning = true))
    }

    @Test
    fun importIsAvailableOnlyOnTheServersTab() {
        LionBoxTab.entries.forEach { tab ->
            assertEquals(tab == LionBoxTab.Servers, tab.canImport)
        }
    }

    @Test
    fun navigationUsesThePreviewTabOrder() {
        assertEquals(listOf(LionBoxTab.Dashboard, LionBoxTab.Servers, LionBoxTab.Logs, LionBoxTab.Settings), LionBoxTab.entries)
    }
}
