package com.v2ray.ang.ui.compose

internal const val DEFAULT_THEME_MODE = "1"

internal fun explicitThemeMode(savedMode: String?): String =
    if (savedMode == "2") "2" else DEFAULT_THEME_MODE

internal fun isExplicitDarkTheme(savedMode: String?): Boolean =
    explicitThemeMode(savedMode) == "2"
