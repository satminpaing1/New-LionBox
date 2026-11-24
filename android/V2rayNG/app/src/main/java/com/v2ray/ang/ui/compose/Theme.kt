package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AmbientTokens {
    val base = Color(0xFFEEF4FB)
    val blush = Color(0xFFBAE6FD)
    val lavender = Color(0xFFE0DAF5)
    val mint = Color(0xFFCCFBF1)
    val darkBlush = Color(0xFF304E72)
    val darkLavender = Color(0xFF494B78)
    val darkMint = Color(0xFF2B625A)
    val connected = Color(0xFF047857)
    val accentStart = Color(0xFF38BDF8)
    val accentEnd = Color(0xFF3B82F6)
    val glass = Color.White.copy(alpha = 0.75f)
    val glassBorder = Color.White.copy(alpha = 0.9f)
    val radius = 26.dp
}

fun Modifier.ambientBackground(darkTheme: Boolean, background: Color): Modifier =
    this.then(Modifier.drawWithCache {
        val radius = size.maxDimension * 0.75f
        val glowAlpha = if (darkTheme) 0.2f else 0.65f
        val glows = listOf(
            (if (darkTheme) AmbientTokens.darkBlush else AmbientTokens.blush) to androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.12f),
            (if (darkTheme) AmbientTokens.darkLavender else AmbientTokens.lavender) to androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.38f),
            (if (darkTheme) AmbientTokens.darkMint else AmbientTokens.mint) to androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.9f)
        ).map { (color, center) -> androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = glowAlpha), color.copy(alpha = 0f)), center, radius) }
        onDrawBehind {
            drawRect(if (darkTheme) background else AmbientTokens.base)
            glows.forEach { drawRect(it) }
        }
    })

private val LightColor = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = AmbientTokens.connected,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = AmbientTokens.connected,
    tertiary = Color(0xFF66568A),
    onTertiary = Color(0xFFFCF8FF),
    tertiaryContainer = Color(0xFFE9E2F5),
    onTertiaryContainer = Color(0xFF44365F),
    background = AmbientTokens.base,
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE9EEFC),
    onSurfaceVariant = Color(0xFF596B82),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFD8E3EF),
    surfaceTint = Color(0xFF2563EB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = Color(0xFFEFF4FB),
    surfaceContainerHigh = Color(0xFFE9EEFC),
    surfaceContainerHighest = Color(0xFFE0E7FF),
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
