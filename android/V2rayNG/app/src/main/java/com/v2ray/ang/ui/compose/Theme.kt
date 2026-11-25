package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlassTokens {
    val base = Color(0xFFEEF5FF)
    val sky = Color(0xFFBAE6FD)
    val mint = Color(0xFFA7F3D0)
    val lavender = Color(0xFFDDD6FE)
    val ink = Color(0xFF0F172A)
    val slate = Color(0xFF334155)
    val muted = Color(0xFF64748B)
    val skyAccent = Color(0xFF38BDF8)
    val accent = Color(0xFF3B82F6)
    val emerald = Color(0xFF10B981)
    val connectedText = Color(0xFF2E8B57)
    val teal = Color(0xFF0F766E)
    val mintRing = Color(0xFF6EE7B7)
    val mintRingFill = Color(0x4034D399)
    val ascent = Color(0xFFE0E7FF)
    val ascentText = Color(0xFF4338CA)
    val mintBadge = Color(0xFFD1FAE5)
    val mintBadgeText = Color(0xFF047857)
    val darkBase = Color(0xFF0B1220)
    val darkSky = Color(0xFF1E3A8A)
    val darkMint = Color(0xFF14532D)
    val darkLavender = Color(0xFF312E81)
}

/**
 * Dashboard card tints. Each card carries its own pastel hue so the stats read as a set of
 * distinct objects, never as one uniform white block.
 */
object GlassTints {
    val iceBlue = Color(0xFFE0F2FE)
    val rose = Color(0xFFFCE7F3)
    val peach = Color(0xFFFEF3C7)
    val lavender = Color(0xFFF3E8FF)
    val mint = Color(0xFFD1FAE5)
    val cyan = Color(0xFFCFFAFE)
    val amber = Color(0xFFFFEDD5)
    val periwinkle = Color(0xFFEEF2FF)

    /** Cool ice-indigo used by the exit-IP card: 65% pastel so it reads indigo, not yellow. */
    val mist = Color(0xFFE0E7FF)
}

/**
 * Advisory (non-blocking) warning surface: the connected-but-no-internet banner. A rose frost at
 * 80% keeps the dashboard readable underneath; ink colours are picked per theme for contrast.
 */
object GlassWarningTokens {
    val tint = GlassMenuTokens.destructiveTint
    val lightAlpha = 0.80f
    val titleLight = GlassMenuTokens.destructiveText
    val titleDark = Color(0xFFFCA5A5)
    val bodyLight = Color(0xFF7F1D1D)
    val bodyDark = Color(0xFFFECACA)
    val actionFillLight = Color(0xFFFFE4E6)
    val actionFillDark = Color(0xFF7F1D1D)
    val actionInkDark = Color(0xFFFEE2E2)
    val cornerRadius = 18.dp
}

/**
 * Menu and dialog glass. Popups get their own cool-tinted frost instead of a plain white box,
 * with a white hairline and zero elevation so no dark hardware shadow leaks through.
 */
object GlassMenuTokens {
    val surface = Color(0xFFF2F6FF)
    val surfaceFillLight = Color(0xF2F2F6FF)
    val surfaceTop = Color(0xFFF0F6FF)
    val surfaceBottom = Color(0xFFF3EEFF)
    val ink = Color(0xFF0F172A)
    val action = Color(0xFF2563EB)
    val destructive = Color(0xFFDC2626)
    val destructiveText = Color(0xFFB91C1C)
    val actionTint = Color(0xFFDBEAFE)
    val destructiveTint = Color(0xFFFEE2E2)
    val skyTint = Color(0xFFE0F2FE)
    val indigoTint = Color(0xFFE0E7FF)
    val indigoInk = Color(0xFF4338CA)
    val ripple = Color(0x59C7D2FE)
    val divider = Color(0x3394A3B8)
    val radius = 24.dp
    val borderWidth = 1.5.dp
    val itemVerticalPadding = 13.dp
    val actionRowRadius = 14.dp
    val actionRowHeight = 52.dp
    val actionBadge = 32.dp
    val scrim = Color(0x4D0F172A)
    val cancelFill = Color(0x99E2E8F0)
    val cancelInk = Color(0xFF334155)
    val sectionLabel = Color(0xFF64748B)
    val subtitle = Color(0xFF475569)

    val darkSurface = Color(0xF22B3446)
    val darkInk = Color(0xFFF1F5F9)
    val darkAction = Color(0xFF93C5FD)
    val darkDestructive = Color(0xFFFCA5A5)
    val darkBorder = Color(0x2EFFFFFF)
    val darkRipple = Color(0x4DC7D2FE)
    val darkScrim = Color(0x990B1220)
    val darkCancelFill = Color(0x33E2E8F0)
    val darkCancelInk = Color(0xFFE2E8F0)
    val darkSectionLabel = Color(0xFF94A3B8)
    val darkSubtitle = Color(0xFFCBD5E1)
}

@Composable
fun glassMenuSurfaceColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkSurface else GlassMenuTokens.surfaceFillLight

@Composable
fun glassMenuBorderColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkBorder else Color(0xFFFFFFFF)

@Composable
fun glassMenuItemInkColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkInk else Color(0xFF0F172A)

@Composable
fun glassMenuActionColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkAction else GlassMenuTokens.action

@Composable
fun glassMenuDestructiveColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkDestructive else GlassMenuTokens.destructive

@Composable
fun glassMenuDestructiveTextColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkDestructive else GlassMenuTokens.destructiveText

@Composable
fun glassMenuRippleColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkRipple else GlassMenuTokens.ripple

@Composable
fun glassDialogScrimColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkScrim else GlassMenuTokens.scrim

@Composable
fun glassDialogCancelFillColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkCancelFill else GlassMenuTokens.cancelFill

@Composable
fun glassDialogCancelInkColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkCancelInk else GlassMenuTokens.cancelInk

@Composable
fun glassDialogSectionLabelColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkSectionLabel else GlassMenuTokens.sectionLabel

@Composable
fun glassDialogSubtitleColor(): Color =
    if (LocalDarkTheme.current) GlassMenuTokens.darkSubtitle else GlassMenuTokens.subtitle

@Composable
fun glassWarningTitleColor(): Color =
    if (LocalDarkTheme.current) GlassWarningTokens.titleDark else GlassWarningTokens.titleLight

@Composable
fun glassWarningBodyColor(): Color =
    if (LocalDarkTheme.current) GlassWarningTokens.bodyDark else GlassWarningTokens.bodyLight

@Composable
fun glassWarningActionFillColor(): Color =
    if (LocalDarkTheme.current) GlassWarningTokens.actionFillDark else GlassWarningTokens.actionFillLight

@Composable
fun glassWarningActionInkColor(): Color =
    if (LocalDarkTheme.current) GlassWarningTokens.actionInkDark else GlassMenuTokens.destructiveText


/**
 * Material elevation is always zero on glass: a hardware shadow bleeds through a translucent fill
 * and renders as a dark grey smudge on Android, so cards separate by fill and hairline only.
 */
val glassCardElevation = 0.dp

/**
 * Frosted pastel fill: 90% pastel in light mode so the hue survives the aurora, a faint veil in
 * dark mode. Individual cards may ask for a lighter wash (the exit-IP card uses 65%).
 */
fun glassTintFill(tint: Color, dark: Boolean, lightAlpha: Float = 0.90f): Color =
    tint.copy(alpha = if (dark) 0.14f else lightAlpha)

@Composable
fun glassTintColor(tint: Color): Color = glassTintFill(tint, LocalDarkTheme.current)

internal fun glassSurfaceFill(dark: Boolean): Color =
    if (dark) Color(0x17FFFFFF) else Color(0xD9FFFFFF)

internal fun glassBorderTint(dark: Boolean): Color =
    if (dark) Color(0x2EFFFFFF) else Color(0xFFFFFFFF)

/** Frosted card fill; bright white glass in light mode, faint white veil on charcoal in dark mode. */
@Composable
fun glassSurfaceColor(): Color = glassSurfaceFill(LocalDarkTheme.current)

@Composable
fun glassStrongSurfaceColor(): Color = if (LocalDarkTheme.current) Color(0x24FFFFFF) else Color(0xE6FFFFFF)

@Composable
fun glassBorderColor(): Color = glassBorderTint(LocalDarkTheme.current)

@Composable
fun glassHairlineColor(): Color = if (LocalDarkTheme.current) Color(0x33FFFFFF) else Color(0x2E0F172A)

@Composable
fun glassTextPrimary(): Color = if (LocalDarkTheme.current) Color(0xFFF1F5F9) else GlassTokens.ink

@Composable
fun glassTextSecondary(): Color = if (LocalDarkTheme.current) Color(0xFFCBD5E1) else GlassTokens.muted

@Composable
fun glassTextLabel(): Color = if (LocalDarkTheme.current) Color(0xFFE2E8F0) else GlassTokens.slate

/**
 * The one frosted card surface every screen uses: bright translucent white, crisp solid white
 * hairline, and no material elevation — a hardware shadow would show through the translucent
 * fill and read as a dirty grey smudge on Android, so separation comes from fill and border only.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color? = null,
    tintAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = when {
            tint == null -> glassSurfaceColor()
            tintAlpha == null -> glassTintColor(tint)
            else -> glassTintFill(tint, LocalDarkTheme.current, tintAlpha)
        },
        contentColor = glassTextPrimary(),
        border = BorderStroke(1.dp, glassBorderColor()),
        shadowElevation = glassCardElevation,
        tonalElevation = 0.dp,
        content = content
    )
}

fun Modifier.ambientBackground(darkTheme: Boolean, background: Color): Modifier =
    this.then(Modifier.drawWithCache {
        val glows = if (darkTheme) {
            listOf(
                GlassTokens.darkSky.copy(alpha = 0.55f) to androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.04f),
                GlassTokens.darkMint.copy(alpha = 0.45f) to androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.94f),
                GlassTokens.darkLavender.copy(alpha = 0.5f) to androidx.compose.ui.geometry.Offset(size.width * 0.98f, size.height * 0.72f)
            )
        } else {
            listOf(
                GlassTokens.sky.copy(alpha = 0.5f) to androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.02f),
                GlassTokens.mint.copy(alpha = 0.45f) to androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.96f),
                GlassTokens.lavender.copy(alpha = 0.55f) to androidx.compose.ui.geometry.Offset(size.width * 0.98f, size.height * 0.6f)
            )
        }
        val radius = size.maxDimension * 0.62f
        onDrawBehind {
            drawRect(if (darkTheme) GlassTokens.darkBase else GlassTokens.base)
            glows.forEach { (color, center) ->
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(color, color.copy(alpha = 0f)),
                        center,
                        radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    })

private val LightColor = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFF6366F1),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF3730A3),
    background = Color(0xFFEEF5FF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFDBE6F5),
    surfaceTint = Color(0xFF3B82F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6FAFF),
    surfaceContainer = Color(0xFFF1F7FF),
    surfaceContainerHigh = Color(0xFFE9F1FC),
    surfaceContainerHighest = Color(0xFFE2ECFA),
)

object KeyConsoleTokens {
    val background = Color(0xFF11151D)
    val surface = Color(0xFF1B222E)
    val raised = Color(0xFF283343)
    val text = Color(0xFFEDF2FA)
    val muted = Color(0xFFB4C0D2)
    val accent = Color(0xFFACCDFB)
    val teal = Color(0xFFA1DCCD)
    val violet = Color(0xFFD1C1EB)
    val tealSurface = Color(0xFF203F39)
    val violetSurface = Color(0xFF373046)
    val border = Color(0xFF465469)
    val radius = androidx.compose.ui.unit.Dp(14f)
    val space = androidx.compose.ui.unit.Dp(8f)
}

private val DarkColor = darkColorScheme(
    primary = KeyConsoleTokens.accent,
    onPrimary = KeyConsoleTokens.background,
    primaryContainer = KeyConsoleTokens.raised,
    onPrimaryContainer = KeyConsoleTokens.text,
    secondary = KeyConsoleTokens.teal,
    onSecondary = KeyConsoleTokens.background,
    secondaryContainer = KeyConsoleTokens.tealSurface,
    onSecondaryContainer = KeyConsoleTokens.teal,
    tertiary = KeyConsoleTokens.violet,
    onTertiary = KeyConsoleTokens.background,
    tertiaryContainer = KeyConsoleTokens.violetSurface,
    onTertiaryContainer = KeyConsoleTokens.violet,
    background = KeyConsoleTokens.background,
    onBackground = KeyConsoleTokens.text,
    surface = KeyConsoleTokens.background,
    onSurface = KeyConsoleTokens.text,
    surfaceVariant = KeyConsoleTokens.raised,
    onSurfaceVariant = KeyConsoleTokens.muted,
    outline = KeyConsoleTokens.muted,
    outlineVariant = KeyConsoleTokens.border,
    surfaceTint = KeyConsoleTokens.accent,
    surfaceContainerLowest = KeyConsoleTokens.background,
    surfaceContainerLow = KeyConsoleTokens.surface,
    surfaceContainer = KeyConsoleTokens.surface,
    surfaceContainerHigh = KeyConsoleTokens.raised,
    surfaceContainerHighest = KeyConsoleTokens.raised,
)

// Semantic Colors
val colorPing = KeyConsoleTokens.accent
val colorPingRed = DarkColor.error
val colorConfigType = KeyConsoleTokens.accent
val colorFabActive = KeyConsoleTokens.accent
val colorFabInactiveLight = LightColor.outline
val colorFabInactiveDark = KeyConsoleTokens.muted
val dividerColorLight = LightColor.outlineVariant
val dividerColorDark = KeyConsoleTokens.border

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        explicitThemeMode(MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT))
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        val explicitMode = explicitThemeMode(mode)
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, explicitMode)
        _themeMode.value = explicitMode
        applyNightMode(explicitMode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value = explicitThemeMode(MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT))
        applyNightMode(_themeMode.value)
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }

    private fun applyNightMode(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            if (isExplicitDarkTheme(mode)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return isExplicitDarkTheme(mode)
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colorScheme.onBackground
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppSnackbarBridge(controller = snackbarController)
                    content()
                    AppSnackbarHost(hostState = snackbarController.hostState)
                }
            }
        }
    }
}
