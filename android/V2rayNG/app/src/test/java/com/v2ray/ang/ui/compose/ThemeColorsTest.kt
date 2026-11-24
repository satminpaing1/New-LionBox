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

    @Test
    fun `dashboard card tints stay pastel frosted and readable`() {
        val tints = mapOf(
            "ice blue" to (GlassTints.iceBlue to Color(0xFFE0F2FE)),
            "rose" to (GlassTints.rose to Color(0xFFFCE7F3)),
            "peach" to (GlassTints.peach to Color(0xFFFEF3C7)),
            "lavender" to (GlassTints.lavender to Color(0xFFF3E8FF)),
            "mint" to (GlassTints.mint to Color(0xFFD1FAE5)),
            "cyan" to (GlassTints.cyan to Color(0xFFCFFAFE)),
            "amber" to (GlassTints.amber to Color(0xFFFFEDD5)),
            "periwinkle" to (GlassTints.periwinkle to Color(0xFFEEF2FF))
        )
        tints.forEach { (name, pair) ->
            val (tint, expected) = pair
            assertEquals(expected, tint, "$name keeps its pastel hue")
            val light = glassTintFill(tint, dark = false)
            assertEquals(0.90f, light.alpha, 0.005f, "$name stays frosted at 90% in light mode")
            assertTrue(glassTintFill(tint, dark = true).alpha < light.alpha, "$name veils down in dark mode")
            val composited = blendOverWhite(light)
            val contrast = contrast(GlassTokens.ink, composited)
            assertTrue(contrast >= 4.5, "$name card ink has contrast $contrast")
        }
    }

    @Test
    fun `exit ip card uses cool mist instead of yellow`() {
        assertEquals(Color(0xFFE0E7FF), GlassTints.mist, "exit-IP card takes the ice-indigo hue")
        val fill = glassTintFill(GlassTints.mist, dark = false, lightAlpha = 0.65f)
        assertEquals(Color(0xA6E0E7FF), fill, "exit-IP card is 65% periwinkle frost")
        val composited = blendOverWhite(fill)
        val contrast = contrast(GlassTokens.ink, composited)
        assertTrue(contrast >= 4.5, "exit-IP ink has contrast $contrast")
    }

    @Test
    fun `popup menus and dialogs use the frosted glass palette`() {
        assertEquals(Color(0xFFF2F6FF), GlassMenuTokens.surface, "dialog surface is cool ice-blue")
        assertEquals(Color(0xFF0F172A), GlassMenuTokens.ink, "menu text is deep navy")
        assertEquals(Color(0xFF2563EB), GlassMenuTokens.action, "normal menu icons are royal blue")
        assertEquals(Color(0xFFDC2626), GlassMenuTokens.destructive, "delete icons are crimson")
        assertEquals(Color(0x59C7D2FE), GlassMenuTokens.ripple, "press ripple is 35% periwinkle")
        assertEquals(Color(0x3394A3B8), GlassMenuTokens.divider, "dividers are 20% slate")
        assertEquals(Color(0xFFDBEAFE), GlassMenuTokens.actionTint, "3-dot circle is pastel periwinkle")
        assertEquals(Color(0xFFFEE2E2), GlassMenuTokens.destructiveTint, "trash circle is pastel rose")
        assertEquals(24.dp, GlassMenuTokens.radius, "dialogs, sheets and menus use 24dp corners")
        assertEquals(1.5.dp, GlassMenuTokens.borderWidth, "glass containers use a 1.5dp white hairline")
        assertEquals(14.dp, GlassMenuTokens.actionRowRadius, "dialog action rows use 14dp corners")
        assertEquals(0x4D, (GlassMenuTokens.scrim.alpha * 255).toInt(), "dialog scrim is 30% light slate")
        assertEquals(0xF2, (GlassMenuTokens.surfaceFillLight.alpha * 255).toInt(), "dialog frost sits at 95%")

        val surface = Color(0xFFF2F6FF)
        assertTrue(contrast(GlassMenuTokens.ink, surface) >= 4.5, "menu ink is readable on the frost")
        assertTrue(
            contrast(GlassMenuTokens.destructiveText, surface) >= 4.5,
            "destructive menu text is readable on the frost"
        )
        assertTrue(contrast(GlassMenuTokens.action, surface) >= 4.5, "royal-blue menu icons are readable")
    }

    private fun blendOverWhite(color: Color): Color = Color(
        red = color.red * color.alpha + (1f - color.alpha),
        green = color.green * color.alpha + (1f - color.alpha),
        blue = color.blue * color.alpha + (1f - color.alpha)
    )

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
