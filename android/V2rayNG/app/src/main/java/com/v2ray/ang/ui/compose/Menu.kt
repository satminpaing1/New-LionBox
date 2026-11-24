package com.v2ray.ang.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Frosted popup used by every 3-dot menu in the app: cool tinted surface, white hairline and no
 * elevation shadow. Ripple tints the pressed row periwinkle instead of the default grey.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(GlassMenuTokens.radius)
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(color = glassMenuRippleColor())
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            shape = shape,
            containerColor = glassMenuSurfaceColor(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, glassMenuBorderColor()),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val ink = if (destructive) glassMenuDestructiveTextColor() else glassMenuItemInkColor()
    val iconTint = if (destructive) glassMenuDestructiveColor() else glassMenuActionColor()
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon?.let { icon ->
            {
                CompositionLocalProvider(LocalContentColor provides iconTint) { icon() }
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = ink,
            leadingIconColor = iconTint,
            disabledTextColor = ink.copy(alpha = 0.45f),
            disabledLeadingIconColor = iconTint.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = GlassMenuTokens.itemVerticalPadding)
    )
}

@Composable
fun <T> AppDropdownMenuItems(
    items: List<T>,
    labelRes: (T) -> Int,
    destructive: (T) -> Boolean = { false },
    onSelected: (T) -> Unit
) {
    items.forEach { item ->
        GlassDropdownMenuItem(
            text = { Text(stringResource(labelRes(item))) },
            onClick = { onSelected(item) },
            destructive = destructive(item)
        )
    }
}
