package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.GroupMapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoSelectTest {

    @Test
    fun picksTheLowestWorkingLatency() {
        val results = mapOf("slow" to 240L, "fast" to 38L, "mid" to 91L)
        assertEquals("fast", fastestServerGuid(results, listOf("slow", "fast", "mid")))
    }

    @Test
    fun ignoresFailedAndMissingResults() {
        val results = mapOf("failed" to -1L, "zero" to 0L, "ok" to 120L)
        assertEquals("ok", fastestServerGuid(results, listOf("failed", "zero", "ok", "untested")))
    }

    @Test
    fun returnsNullWhenNothingAnswered() {
        assertNull(fastestServerGuid(mapOf("failed" to -1L), listOf("failed")))
        assertNull(fastestServerGuid(emptyMap(), listOf("a", "b")))
    }

    @Test
    fun tabsPutDefaultFirstThenSubscriptions() {
        val groups = listOf(
            GroupMapItem("", "All"),
            GroupMapItem("sub-1", "Sub 1"),
            GroupMapItem("sub-2", "Import Sub")
        )
        val tabs = serverTabs(groups, "Default")
        assertEquals(listOf("", "sub-1", "sub-2"), tabs.map { it.groupId })
        assertEquals(listOf("Default", "Sub 1", "Import Sub"), tabs.map { it.remarks })
    }

    @Test
    fun tabsKeepDefaultWhenNoAllGroupExists() {
        val tabs = serverTabs(listOf(GroupMapItem("sub-1", "Sub 1")), "Default")
        assertEquals(listOf("", "sub-1"), tabs.map { it.groupId })
    }

    @Test
    fun storedDefaultGroupIsTheSingleDefaultTab() {
        val groups = listOf(
            GroupMapItem(AppConfig.DEFAULT_SUBSCRIPTION_ID, "Default"),
            GroupMapItem("import-sub", "import sub")
        )
        val tabs = serverTabs(groups, "Default")
        assertEquals(listOf(AppConfig.DEFAULT_SUBSCRIPTION_ID, "import-sub"), tabs.map { it.groupId })
        assertEquals(listOf("Default", "import sub"), tabs.map { it.remarks })
        assertEquals(1, tabs.count { it.remarks == "Default" })
    }

    @Test
    fun tabsCollapseDuplicateGroupsAndKeepDefaultFirst() {
        val groups = listOf(
            GroupMapItem("import-sub", "import sub"),
            GroupMapItem(AppConfig.DEFAULT_SUBSCRIPTION_ID, "Default"),
            GroupMapItem("import-sub", "import sub"),
            GroupMapItem("", "All")
        )
        val tabs = serverTabs(groups, "Default")
        assertEquals(listOf("", "import-sub"), tabs.map { it.groupId })
        assertEquals(listOf("Default", "import sub"), tabs.map { it.remarks })
    }

    @Test
    fun emptyStateOnlyForTheLoadedSelectedTabWithoutServers() {
        assertTrue(shouldShowEmptyState("g", "g", loaded = true, serverCount = 0, queryBlank = true))
        assertFalse(shouldShowEmptyState("g", "g", loaded = true, serverCount = 1, queryBlank = true))
        assertFalse(shouldShowEmptyState("g", "g", loaded = false, serverCount = 0, queryBlank = true))
        assertFalse(shouldShowEmptyState("other", "g", loaded = true, serverCount = 0, queryBlank = true))
        assertFalse(shouldShowEmptyState("g", "g", loaded = true, serverCount = 0, queryBlank = false))
    }
}
