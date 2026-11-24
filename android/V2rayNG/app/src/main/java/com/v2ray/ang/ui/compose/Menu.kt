package com.v2ray.ang.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

/**
 * Shared frosted dialog container: 95% ice-blue sheet, 24dp corners, 1.5dp white hairline,
 * zero dark elevation and a soft slate scrim instead of a heavy black one.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(glassDialogScrimColor())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(GlassMenuTokens.radius),
                color = glassMenuSurfaceColor(),
                contentColor = glassMenuItemInkColor(),
                border = BorderStroke(GlassMenuTokens.borderWidth, glassMenuBorderColor()),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                CompositionLocalProvider(
                    LocalRippleConfiguration provides RippleConfiguration(color = glassMenuRippleColor())
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
fun GlassDialogHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            color = glassMenuItemInkColor()
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                fontSize = 13.sp,
                color = glassDialogSubtitleColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 48–52dp tappable row with a 32dp pastel circle badge and dark navy label. */
@Composable
fun GlassDialogActionRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    badgeTint: Color? = null,
    iconTint: Color? = null
) {
    val ink = if (destructive) glassMenuDestructiveTextColor() else glassMenuItemInkColor()
    val tint = badgeTint ?: if (destructive) GlassMenuTokens.destructiveTint else GlassMenuTokens.actionTint
    val glyph = iconTint ?: if (destructive) GlassMenuTokens.destructive else GlassMenuTokens.action
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = GlassMenuTokens.actionRowHeight),
        enabled = enabled,
        shape = RoundedCornerShape(GlassMenuTokens.actionRowRadius),
        color = Color.Transparent,
        contentColor = ink
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(GlassMenuTokens.actionBadge)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = glyph
                )
            }
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) ink else ink.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Full-width pill button, used for Cancel and dialog confirmations. */
@Composable
fun GlassDialogPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: Int? = null,
    enabled: Boolean = true,
    gradient: List<Color>? = null
) {
    val fill = when {
        primary -> GlassTokens.accent
        destructive -> GlassMenuTokens.destructiveTint
        else -> glassDialogCancelFillColor()
    }
    val ink = when {
        primary -> Color.White
        destructive -> glassMenuDestructiveTextColor()
        else -> glassDialogCancelInkColor()
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        color = if (gradient != null) Color.Transparent else fill,
        contentColor = ink,
        border = BorderStroke(1.dp, glassMenuBorderColor())
    ) {
        Row(
            Modifier
                .then(
                    if (gradient != null) {
                        Modifier.background(Brush.horizontalGradient(gradient))
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 13.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    painterResource(leadingIcon),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    tint = ink
                )
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink)
        }
    }
}

@Composable
fun GlassDialogSectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = glassDialogSectionLabelColor(),
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** Scrollable rounded bottom sheet on the same 95% frost, for long menu lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
        shape = RoundedCornerShape(topStart = GlassMenuTokens.radius, topEnd = GlassMenuTokens.radius),
        containerColor = glassMenuSurfaceColor(),
        contentColor = glassMenuItemInkColor(),
        tonalElevation = 0.dp,
        scrimColor = glassDialogScrimColor(),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(glassDialogSectionLabelColor().copy(alpha = 0.45f))
            )
        }
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides RippleConfiguration(color = glassMenuRippleColor())
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(
                        GlassMenuTokens.borderWidth,
                        glassMenuBorderColor(),
                        RoundedCornerShape(topStart = GlassMenuTokens.radius, topEnd = GlassMenuTokens.radius)
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                content = content
            )
        }
    }
}
