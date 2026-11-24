package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
