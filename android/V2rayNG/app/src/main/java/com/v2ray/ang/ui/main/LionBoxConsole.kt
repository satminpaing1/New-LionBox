package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.GlassCard
import com.v2ray.ang.ui.compose.GlassTokens
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.ambientBackground
import com.v2ray.ang.ui.compose.glassBorderColor
import com.v2ray.ang.ui.compose.glassHairlineColor
import com.v2ray.ang.ui.compose.glassStrongSurfaceColor
import com.v2ray.ang.ui.compose.glassSurfaceColor
import com.v2ray.ang.ui.compose.glassTextLabel
import com.v2ray.ang.ui.compose.glassTextPrimary
import com.v2ray.ang.ui.compose.glassTextSecondary
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.logcat.LogcatViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelProvider

internal enum class LionBoxTab(val label: Int, val icon: Int) {
    Dashboard(R.string.lion_dashboard, R.drawable.ic_home_24dp),
    Servers(R.string.lion_servers, R.drawable.ic_routing_24dp),
    Logs(R.string.lion_logs, R.drawable.ic_logcat_24dp),
    Settings(R.string.title_settings, R.drawable.ic_settings_24dp);

    val canImport: Boolean get() = this == Servers
}

internal fun connectionAction(hasSelection: Boolean, isRunning: Boolean): MainAction? =
    if (hasSelection || isRunning) MainAction.ToggleService else null

internal fun dashboardLatencyMillis(status: MainStatus, isRunning: Boolean, savedDelay: Long?): Long? =
    if (isRunning) (status as? MainStatus.ConnectionTest)?.result?.delayMillis else savedDelay

@Composable
internal fun LionBoxConsole(
    uiState: MainUiState,
    mainViewModel: MainViewModel,
    isLoading: Boolean,
    displayText: String,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onMore: (String, ProfileItem) -> Unit,
    onRemove: (String, String) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(LionBoxTab.Dashboard) }
    var query by rememberSaveable { mutableStateOf("") }
    var importVisible by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val groupFlow = remember(uiState.selectedGroupId) { mainViewModel.serverGroupState(uiState.selectedGroupId) }
    val group by groupFlow.collectAsStateWithLifecycle()
    val selected = group.rows.firstOrNull { it.guid == uiState.selectedGuid }
    val colors = MaterialTheme.colorScheme
    val darkTheme = LocalDarkTheme.current
    LaunchedEffect(uiState.isRunning, uiState.selectedGuid) {
        if (uiState.isRunning) onAction(MainAction.TestCurrentServer)
    }
    Scaffold(
        modifier = Modifier.ambientBackground(darkTheme, colors.background),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassStrongSurfaceColor())
                        .border(1.dp, glassBorderColor(), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.key_console_launcher), null, Modifier.size(34.dp), tint = Color.Unspecified)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = glassTextPrimary()
                    )
                    Text(stringResource(R.string.lion_subtitle), fontSize = 13.sp, color = glassTextSecondary())
                }
                if (tab.canImport) Box {
                    IconButton(onClick = { importMenu = true }, enabled = !isLoading) {
                        Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.menu_item_add_config), tint = glassTextPrimary())
                    }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lion_paste)) }, onClick = { importMenu = false; importVisible = true })
                        ImportMenuContent { importMenu = false; onAction(it) }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.lion_tools), tint = glassTextPrimary())
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        MoreMenuContent { moreMenu = false; onMoreMenuAction(it) }
                    }
                }
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = glassStrongSurfaceColor(),
                    border = BorderStroke(1.dp, glassBorderColor()),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LionBoxTab.entries.forEach { item ->
                            val active = tab == item
                            val activeGradient = if (darkTheme) {
                                Brush.linearGradient(listOf(Color(0x593B82F6), Color(0x4D8B5CF6)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFBFDBFE), Color(0xFFDDD6FE)))
                            }
                            val resting = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            val tabLabel = stringResource(item.label)
                            Column(
                                Modifier.weight(1f).heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (active) activeGradient else resting)
                                    .selectable(selected = active, role = Role.Tab, onClick = {
                                        tab = item
                                        query = ""
                                        onAction(MainAction.Search(""))
                                    })
                                    .semantics { contentDescription = tabLabel },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painterResource(item.icon), null, Modifier.size(22.dp),
                                    tint = if (active) (if (darkTheme) Color(0xFFBFDBFE) else Color(0xFF1D4ED8)) else glassTextSecondary()
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tabLabel,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) (if (darkTheme) Color(0xFFBFDBFE) else Color(0xFF1D4ED8)) else glassTextSecondary()
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (tab == LionBoxTab.Logs) {
            LionBoxLogs(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (tab == LionBoxTab.Dashboard) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LionPowerButton(uiState.isRunning, connectionAction(uiState.selectedGuid != null, uiState.isRunning) != null && !isLoading) {
                                connectionAction(uiState.selectedGuid != null, uiState.isRunning)?.let(onAction)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (uiState.isRunning) stringResource(R.string.lion_connected) else displayText,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isRunning) GlassTokens.connectedText else glassTextPrimary(),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(if (uiState.isRunning) R.string.lion_tap_disconnect else R.string.lion_tap_connect),
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 14.sp,
                                color = glassTextSecondary(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                selected?.remarks ?: stringResource(R.string.lion_no_server),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                fontSize = 13.sp,
                                color = glassTextSecondary(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item(key = "traffic") { TrafficStats(uiState) }
                    item(key = "runtime") { RuntimeStats(uiState, selected) }
                }
                if (tab.canImport) {
                    item {
                        Button(
                            onClick = { importVisible = true },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_add_24dp), null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lion_paste), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (tab == LionBoxTab.Dashboard || tab == LionBoxTab.Servers) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.lion_servers),
                                Modifier.weight(1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = glassTextPrimary()
                            )
                            TextButton(onClick = { onAction(MainAction.TestAllServers) }, enabled = group.rows.isNotEmpty() && !uiState.isTesting) {
                                Text(stringResource(R.string.lion_test), color = GlassTokens.accent)
                            }
                        }
                    }
                    if (uiState.groups.size > 1) item {
                        var groupMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { groupMenu = true },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, glassHairlineColor()),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = glassSurfaceColor(), contentColor = glassTextPrimary())
                            ) { Text(uiState.groups.firstOrNull { it.id == uiState.selectedGroupId }?.remarks.orEmpty()) }
                            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                uiState.groups.forEach { entry ->
                                    DropdownMenuItem(text = { Text(entry.remarks) }, onClick = { groupMenu = false; onAction(MainAction.SelectGroup(entry.id)) })
                                }
                            }
                        }
                    }
                    if (tab == LionBoxTab.Servers) item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; onAction(MainAction.Search(it)) },
                            label = { Text(stringResource(R.string.lion_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp)
                        )
                    }
                    if (group.rows.isEmpty()) item {
                        ConsoleCard {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(if (query.isBlank()) R.string.lion_empty else R.string.lion_no_results), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary())
                                Text(stringResource(R.string.lion_empty_detail), fontSize = 14.sp, color = glassTextSecondary())
                                if (tab.canImport) TextButton(onClick = { importVisible = true }, enabled = !isLoading) { Text(stringResource(R.string.lion_paste)) }
                            }
                        }
                    }
                    items(group.rows, key = { it.guid }) { row ->
                        LionServerCard(row, row.guid == uiState.selectedGuid,
                            onSelect = { onAction(MainAction.SelectServer(row.guid)) },
                            onMore = { onMore(row.guid, row.profile) },
                            onRemove = { onRemove(row.guid, row.remarks) })
                    }
                }
                if (tab == LionBoxTab.Settings) {
                    item {
                        Text(
                            stringResource(R.string.title_settings),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = glassTextPrimary()
                        )
                    }
                    items(listOf(MainDestination.Settings, MainDestination.Subscriptions, MainDestination.PerAppProxy, MainDestination.Routing, MainDestination.UserAssets, MainDestination.BackupRestore, MainDestination.About)) { destination ->
                        ConsoleCard(shape = RoundedCornerShape(20.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { onNavigate(destination) }.padding(horizontal = 18.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painterResource(destination.iconRes), null, Modifier.size(21.dp), tint = colors.primary)
                                }
                                Text(stringResource(destination.labelRes), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary())
                            }
                        }
                    }
                }
            }
        }
    }
    if (importVisible) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) importVisible = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.lion_paste)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.lion_empty_detail))
                    OutlinedTextField(value = importText, onValueChange = { importText = it }, label = { Text(stringResource(R.string.lion_config)) }, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), enabled = !isLoading)
                }
            },
            confirmButton = { TextButton(enabled = importText.isNotBlank() && !isLoading, onClick = { onAction(MainAction.ImportBatchConfig(importText.trim())); importText = ""; importVisible = false }) { Text(stringResource(R.string.lion_import)) } },
            dismissButton = { TextButton(onClick = { importVisible = false }, enabled = !isLoading) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun ConsoleCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable () -> Unit) {
    GlassCard(shape = shape, content = content)
}

@Composable
private fun delayLabel(delay: Long?): String = when {
    delay == null || delay == 0L -> "—"
    delay < 0 -> stringResource(R.string.connection_test_empty_message)
    else -> stringResource(R.string.server_test_delay_value, delay)
}

@Composable
private fun LionServerCard(row: ServerRowUiModel, selected: Boolean, onSelect: () -> Unit, onMore: () -> Unit, onRemove: () -> Unit) {
    val stateLabel = stringResource(if (selected) R.string.lion_selected else R.string.lion_select)
    val darkTheme = LocalDarkTheme.current
    val delay = row.testDelayMillis
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            if (darkTheme) Color(0x33C7D2FE) else Color(0xA6EEF2FF)
        } else glassSurfaceColor(),
        border = BorderStroke(1.dp, if (selected) Color(0xFFFFFFFF) else glassBorderColor()),
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel }
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_vpn_monochrome),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFF97316)
                    )
                    Text(
                        row.remarks,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTextPrimary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (darkTheme) Color(0x334338CA) else GlassTokens.ascent
                    ) {
                        Text(
                            row.typeDescription,
                            Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (darkTheme) Color(0xFFC7D2FE) else GlassTokens.ascentText
                        )
                    }
                    Text(
                        row.statistics,
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = glassTextSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (delay != null && delay > 0) {
                            if (darkTheme) Color(0x33047857) else GlassTokens.mintBadge
                        } else if (darkTheme) Color(0x1FFFFFFF) else Color(0xFFEAF0F9)
                    ) {
                        Text(
                            delayLabel(delay),
                            Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (delay != null && delay > 0) {
                                if (darkTheme) Color(0xFF6EE7B7) else GlassTokens.mintBadgeText
                            } else glassTextSecondary()
                        )
                    }
                }
            }
            IconButton(onClick = onMore, modifier = Modifier.size(44.dp)) {
                Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.lion_tools), modifier = Modifier.size(20.dp), tint = glassTextSecondary())
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), modifier = Modifier.size(20.dp), tint = glassTextSecondary())
            }
        }
    }
}

@Composable
private fun LionPowerButton(isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isRunning) R.string.lion_tap_disconnect else R.string.lion_tap_connect)
    val darkTheme = LocalDarkTheme.current
    val opacity = if (enabled) 1f else 0.45f
    val wave = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        wave.snapTo(0f)
        if (isRunning && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            while (isRunning) {
                wave.snapTo(0f)
                wave.animateTo(1f, tween(2800, easing = LinearEasing))
            }
        }
    }
    val haloFill = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.65f)
    val haloStroke = if (darkTheme) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.9f)
    val orbStart = if (isRunning) Color(0xFF34D399) else Color(0xFF38BDF8)
    val orbEnd = if (isRunning) Color(0xFF0F766E) else Color(0xFF3B82F6)
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val haloRadius = 90.dp.toPx()
            val ringRadius = 74.dp.toPx()
            drawCircle(haloFill, radius = haloRadius, center = c)
            // Soft coloured glow under the orb: a material elevation shadow would read as dirty grey.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orbStart.copy(alpha = 0.30f * opacity), Color.Transparent),
                    center = c,
                    radius = ringRadius
                ),
                radius = ringRadius,
                center = c
            )
            drawCircle(haloStroke, radius = haloRadius, center = c, style = Stroke(1.5.dp.toPx()))
            if (isRunning) {
                listOf(0f, 0.5f).forEachIndexed { index, offset ->
                    val progress = (wave.value + offset) % 1f
                    val alpha = (0.2f - index * 0.1f) * (1f - progress) * opacity
                    drawCircle(
                        GlassTokens.emerald.copy(alpha = alpha),
                        radius = ringRadius * (1f + 0.16f * progress),
                        center = c,
                        style = Stroke(2.dp.toPx())
                    )
                }
                val pulse = 1f + 0.035f * kotlin.math.sin(wave.value * 2f * kotlin.math.PI.toFloat())
                drawCircle(GlassTokens.mintRingFill.copy(alpha = 0.25f * opacity), radius = ringRadius * pulse, center = c)
                drawCircle(GlassTokens.mintRing.copy(alpha = 0.85f * opacity), radius = ringRadius * pulse, center = c, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(Color(0xFF3B82F6).copy(alpha = 0.10f * opacity), radius = ringRadius, center = c)
                drawCircle(Color(0xFF3B82F6).copy(alpha = 0.22f * opacity), radius = ringRadius, center = c, style = Stroke(2.dp.toPx()))
            }
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 0.dp,
            modifier = Modifier.size(116.dp).semantics { contentDescription = label; stateDescription = label }
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colors = listOf(orbStart.copy(alpha = opacity), orbEnd.copy(alpha = opacity)),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.35f * opacity), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.22f),
                            radius = size.minDimension * 0.62f
                        )
                    )
                    val icon = 44.dp.toPx()
                    val left = (size.width - icon) / 2f
                    val top = (size.height - icon) / 2f
                    val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = Color.White.copy(alpha = opacity),
                        startAngle = -50f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(left + icon * 0.14f, top + icon * 0.2f),
                        size = Size(icon * 0.72f, icon * 0.72f),
                        style = stroke
                    )
                    drawLine(
                        color = Color.White.copy(alpha = opacity),
                        start = Offset(size.width / 2f, top + icon * 0.05f),
                        end = Offset(size.width / 2f, top + icon * 0.46f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun LionBoxLogs(modifier: Modifier) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val model = remember(activity) { ViewModelProvider(activity)[LogcatViewModel::class.java] }
    val logs by model.filteredLogs.collectAsStateWithLifecycle()
    val loading by model.isLoading.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadLogcat() }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.lion_logs), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (logs.isEmpty() && !loading) {
            GlassCard {
                Text(
                    stringResource(R.string.lion_logs_empty),
                    Modifier.fillMaxWidth().padding(24.dp),
                    fontSize = 14.sp,
                    color = glassTextSecondary()
                )
            }
        }
        GlassCard {
            LazyColumn(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs.size) { index ->
                    Text(logs[index], fontSize = 12.sp, color = glassTextLabel())
                }
            }
        }
    }
}
