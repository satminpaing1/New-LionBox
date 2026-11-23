package com.v2ray.ang.ui.compose

import androidx.compose.ui.graphics.Color
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
            KeyConsoleTokens.background to KeyConsoleTokens.accent,
            KeyConsoleTokens.teal to KeyConsoleTokens.tealSurface,
            KeyConsoleTokens.violet to KeyConsoleTokens.violetSurface
        ).forEach { (foreground, background) ->
            val contrast = contrast(foreground, background)
            assertTrue(contrast >= 4.5, "$foreground on $background has contrast $contrast")
        }
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
