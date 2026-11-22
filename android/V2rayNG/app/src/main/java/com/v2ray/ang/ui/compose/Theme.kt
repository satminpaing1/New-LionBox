package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF245BCD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBDCFF),
    onPrimaryContainer = Color(0xFF15346B),
    secondary = Color(0xFF147454),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4ECDD),
    onSecondaryContainer = Color(0xFF124632),
    tertiary = Color(0xFF655096),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1D5F5),
    onTertiaryContainer = Color(0xFF382655),
    background = Color(0xFFE2EAF5),
    onBackground = Color(0xFF14243B),
    surface = Color(0xFFEBE6F5),
    onSurface = Color(0xFF14243B),
    surfaceVariant = Color(0xFFD4DEED),
    onSurfaceVariant = Color(0xFF41516A),
    outline = Color(0xFF718198),
    outlineVariant = Color(0xFFB6C6DD),
    surfaceTint = Color(0xFF245BCD),
    surfaceContainerLowest = Color(0xFFEAF2F8),
    surfaceContainerLow = Color(0xFFDDE9F7),
    surfaceContainer = Color(0xFFDCE7F4),
    surfaceContainerHigh = Color(0xFFD2E0F0),
    surfaceContainerHighest = Color(0xFFC9D9EB),
)

object KeyConsoleTokens {
    val background = Color(0xFF0C1626)
    val surface = Color(0xFF152238)
    val raised = Color(0xFF1B2C46)
    val text = Color(0xFFF0F5FF)
    val muted = Color(0xFFAFBED3)
    val accent = Color(0xFF90B7FF)
    val teal = Color(0xFF7DDCB0)
    val violet = Color(0xFFB6C7E2)
    val tealSurface = Color(0xFF15392E)
    val violetSurface = Color(0xFF253650)
    val border = Color(0xFF344762)
    val radius = androidx.compose.ui.unit.Dp(12f)
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
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2") ?: "2"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2") ?: "2"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
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
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
