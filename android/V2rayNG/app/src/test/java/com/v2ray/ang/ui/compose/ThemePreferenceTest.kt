package com.v2ray.ang.ui.compose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemePreferenceTest {
    @Test
    fun `new installs default to light without consulting device appearance`() {
        assertEquals("1", DEFAULT_THEME_MODE)
        assertEquals("1", explicitThemeMode(null))
        assertFalse(isExplicitDarkTheme(null))
    }

    @Test
    fun `only explicit dark selection enables dark appearance`() {
        assertEquals("2", explicitThemeMode("2"))
        assertTrue(isExplicitDarkTheme("2"))
        listOf("1", "0", "-1", "", "invalid").forEach { value ->
            assertEquals("1", explicitThemeMode(value))
            assertFalse(isExplicitDarkTheme(value))
        }
    }

    @Test
    fun `stored selections survive reload and can switch back to light`() {
        var stored: String? = null
        stored = explicitThemeMode("2")
        assertTrue(isExplicitDarkTheme(stored))
        stored = explicitThemeMode("1")
        assertFalse(isExplicitDarkTheme(stored))
    }
}
