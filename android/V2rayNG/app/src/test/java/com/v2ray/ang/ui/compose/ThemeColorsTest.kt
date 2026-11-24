package com.v2ray.ang.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class ThemeColorsTest {
    @Test
    fun `dark text and metric accents remain readable on their surfaces`() {
        listOf(
            KeyConsoleTokens.text to KeyConsoleTokens.background,
            KeyConsoleTokens.text to KeyConsoleTokens.surface,
            KeyConsoleTokens.text to KeyConsoleTokens.raised,
            KeyConsoleTokens.muted to KeyConsoleTokens.surface,
            KeyConsoleTokens.muted to KeyConsoleTokens.raised,
            KeyConsoleTokens.accent to KeyConsoleTokens.raised,
            KeyConsoleTokens.accent to KeyConsoleTokens.background,
            KeyConsoleTokens.accent to KeyConsoleTokens.surface,
            KeyConsoleTokens.muted to KeyConsoleTokens.background,
            KeyConsoleTokens.background to KeyConsoleTokens.accent,
            KeyConsoleTokens.teal to KeyConsoleTokens.tealSurface,
            KeyConsoleTokens.violet to KeyConsoleTokens.violetSurface
        ).forEach { (foreground, background) ->
            val contrast = contrast(foreground, background)
            assertTrue(contrast >= 4.5, "$foreground on $background has contrast $contrast")
        }
    }

    @Test
    fun `glass cards stay flat and separate by white fill and hairline`() {
        assertEquals(0.dp, glassCardElevation, "glass cards must not draw a hardware elevation shadow")
        assertEquals(Color(0xD9FFFFFF), glassSurfaceFill(dark = false), "light glass fill is 85% white")
        assertEquals(Color(0xFFFFFFFF), glassBorderTint(dark = false), "light glass hairline is solid white")
        assertEquals(0.85f, glassSurfaceFill(dark = false).alpha, 0.001f)
        assertTrue(glassBorderTint(dark = true).alpha < glassBorderTint(dark = false).alpha)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double =
            if (channel <= 0.04045f) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        return linear(color.red) * 0.2126 + linear(color.green) * 0.7152 + linear(color.blue) * 0.0722
    }
}
