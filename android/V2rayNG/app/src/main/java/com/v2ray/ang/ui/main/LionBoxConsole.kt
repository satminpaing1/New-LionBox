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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.GlassCard
import com.v2ray.ang.ui.compose.GlassDropdownMenu
import com.v2ray.ang.ui.compose.GlassDropdownMenuItem
import com.v2ray.ang.ui.compose.GlassMenuTokens
import com.v2ray.ang.ui.compose.GlassTokens
import com.v2ray.ang.ui.compose.GlassTints
import com.v2ray.ang.ui.compose.GlassWarningTokens
import com.v2ray.ang.ui.compose.glassWarningActionFillColor
import com.v2ray.ang.ui.compose.glassWarningActionInkColor
import com.v2ray.ang.ui.compose.glassWarningBodyColor
import com.v2ray.ang.ui.compose.glassWarningTitleColor
import com.v2ray.ang.ui.compose.glassMenuBorderColor
import com.v2ray.ang.ui.compose.glassMenuSurfaceColor
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

internal fun dashboardLatencyMillis(status: MainStatus, savedDelay: Long?): Long? =
    when (status) {
        is MainStatus.ConnectionTest -> status.result.delayMillis
        is MainStatus.Testing -> null
        else -> savedDelay
    }

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
    val allGroupFlow = remember { mainViewModel.serverGroupState("") }
    val allGroup by allGroupFlow.collectAsStateWithLifecycle()
    val selected = group.rows.firstOrNull { it.guid == uiState.selectedGuid }
        ?: allGroup.rows.firstOrNull { it.guid == uiState.selectedGuid }
    val openServersTab: () -> Unit = {
        tab = LionBoxTab.Servers
        query = ""
        onAction(MainAction.Search(""))
    }
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
                    if (importMenu) {
                        ImportOptionsSheet(
                            onPaste = { importMenu = false; importVisible = true },
                            onAction = { importMenu = false; onAction(it) },
                            onDismissRequest = { importMenu = false }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.lion_tools), tint = glassTextPrimary())
                    }
                    GlassDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
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
        } else if (tab == LionBoxTab.Servers) {
            LionServersPage(
                modifier = Modifier.fillMaxSize().padding(padding),
                uiState = uiState,
                mainViewModel = mainViewModel,
                isLoading = isLoading,
                query = query,
                onQueryChange = { query = it },
                onAction = onAction,
                onMore = onMore,
                onRemove = onRemove
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val noInternet = noInternetWarning(uiState.tunnelReachability, uiState.isRunning)
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
                                if (uiState.isRunning) {
                                    stringResource(
                                        if (noInternet) R.string.lion_connected_no_internet else R.string.lion_connected
                                    )
                                } else {
                                    displayText
                                },
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    noInternet -> glassWarningTitleColor()
                                    uiState.isRunning -> GlassTokens.connectedText
                                    else -> glassTextPrimary()
                                },
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
                    if (noInternet) {
                        item(key = "no_internet") {
                            NoInternetWarning(
                                onSwitchServer = {
                                    if (uiState.autoSelectFastest) {
                                        onAction(MainAction.ReconnectFastestServer)
                                    } else {
                                        openServersTab()
                                    }
                                },
                                onRetry = { onAction(MainAction.RetryTunnelCheck) }
                            )
                        }
                    }
                    item(key = "traffic") { TrafficStats(uiState) }
                    item(key = "runtime") { RuntimeStats(uiState, selected) }
                }
                if (tab == LionBoxTab.Dashboard) {
                    item(key = "selected_server") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.lion_selected),
                                    Modifier.weight(1f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glassTextPrimary()
                                )
                                TextButton(onClick = openServersTab) {
                                    Text(
                                        stringResource(R.string.lion_change),
                                        color = GlassTokens.accent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (selected == null) {
                                ConsoleCard {
                                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            stringResource(R.string.lion_dashboard_no_server),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = glassTextPrimary()
                                        )
                                        Text(stringResource(R.string.lion_dashboard_no_server_detail), fontSize = 14.sp, color = glassTextSecondary())
                                        TextButton(onClick = openServersTab) { Text(stringResource(R.string.lion_choose_server)) }
                                    }
                                }
                            } else {
                                LionServerCard(
                                    selected,
                                    selected = true,
                                    autoBadge = uiState.autoSelectFastest,
                                    onSelect = openServersTab,
                                    onMore = { onMore(selected.guid, selected.profile) },
                                    onRemove = { onRemove(selected.guid, selected.remarks) }
                                )
                            }
                        }
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
                    items(listOf(MainDestination.Settings, MainDestination.Subscriptions, MainDestination.PerAppProxy, MainDestination.Routing, MainDestination.UserAssets, MainDestination.BackupRestore)) { destination ->
                        ConsoleCard(shape = RoundedCornerShape(20.dp)) {
                            SettingsRow(destination, onNavigate)
                        }
                    }
                    item {
                        UpdateCheckCard(mainViewModel = mainViewModel, onAction = onAction)
                    }
                    item {
                        ConsoleCard(shape = RoundedCornerShape(20.dp)) {
                            SettingsRow(MainDestination.About, onNavigate)
                        }
                    }
                }
            }
        }
    }
    if (importVisible) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) importVisible = false },
            shape = RoundedCornerShape(GlassMenuTokens.radius),
            modifier = Modifier.border(1.dp, glassMenuBorderColor(), RoundedCornerShape(GlassMenuTokens.radius)),
            containerColor = glassMenuSurfaceColor(),
            tonalElevation = 0.dp,
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
private fun NoInternetWarning(onSwitchServer: () -> Unit, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassWarningTokens.cornerRadius),
        tint = GlassWarningTokens.tint,
        tintAlpha = GlassWarningTokens.lightAlpha
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning_amber_24dp),
                contentDescription = stringResource(R.string.lion_no_internet_warning),
                tint = glassWarningTitleColor(),
                modifier = Modifier.padding(top = 1.dp).size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.lion_no_internet_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = glassWarningTitleColor()
                )
                Text(
                    stringResource(R.string.lion_no_internet_body),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = glassWarningBodyColor()
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarningPill(stringResource(R.string.lion_no_internet_switch), onSwitchServer)
                    WarningPill(stringResource(R.string.lion_no_internet_retry), onRetry)
                }
            }
        }
    }
}

@Composable
private fun WarningPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(glassWarningActionFillColor())
            .border(1.dp, glassBorderColor(), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = glassWarningActionInkColor())
    }
}

@Composable
private fun ConsoleCard(shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable () -> Unit) {
    GlassCard(shape = shape, content = content)
}

@Composable
private fun SettingsRow(destination: MainDestination, onNavigate: (MainDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
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

/** Settings card that checks releases by hand and reports the installed version. */
@Composable
private fun UpdateCheckCard(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit) {
    val status by mainViewModel.updateCheckStatus.collectAsStateWithLifecycle()
    val statusText = when (val current = status) {
        is UpdateCheckStatus.Checking -> stringResource(R.string.lion_update_checking)
        is UpdateCheckStatus.UpToDate -> stringResource(R.string.lion_update_up_to_date)
        is UpdateCheckStatus.Failed -> stringResource(R.string.lion_update_check_failed)
        UpdateCheckStatus.Idle -> stringResource(R.string.lion_update_tap_to_check)
    }
    GlassCard(shape = RoundedCornerShape(20.dp), tint = GlassTints.iceBlue, tintAlpha = 0.65f) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    if (status !is UpdateCheckStatus.Checking) onAction(MainAction.CheckAppUpdate)
                }
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_check_update_24dp),
                    null,
                    Modifier.size(21.dp),
                    tint = GlassMenuTokens.action
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.lion_update_card_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = glassTextPrimary()
                )
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = glassTextSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status is UpdateCheckStatus.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = GlassMenuTokens.action
                )
            } else {
                Text(
                    stringResource(R.string.lion_update_badge, com.v2ray.ang.BuildConfig.VERSION_NAME),
                    fontSize = 12.sp,
                    color = glassTextSecondary()
                )
            }
        }
    }
}

@Composable
private fun delayLabel(delay: Long?): String = when {
    delay == null || delay == 0L -> "—"
    delay < 0 -> stringResource(R.string.connection_test_empty_message)
    else -> stringResource(R.string.server_test_delay_value, delay)
}

@Composable
private fun LionServerCard(
    row: ServerRowUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onMore: () -> Unit,
    onRemove: () -> Unit,
    autoBadge: Boolean = false
) {
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
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel }
                .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(R.drawable.ic_vpn_monochrome),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color(0xFFF97316)
                )
                Text(
                    row.remarks,
                    Modifier.weight(1f, fill = false),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glassTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (autoBadge) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (darkTheme) Color(0x334338CA) else Color(0xFFDBEAFE)
                    ) {
                        Text(
                            stringResource(R.string.lion_auto_badge),
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (darkTheme) Color(0xFFC7D2FE) else Color(0xFF1E40AF)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (darkTheme) Color(0x334338CA) else GlassTokens.ascent
                    ) {
                        Text(
                            row.typeDescription,
                            Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (darkTheme) Color(0xFFC7D2FE) else GlassTokens.ascentText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        row.statistics,
                        Modifier.weight(1f, fill = false),
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
                CircleActionButton(
                    icon = R.drawable.ic_more_vert_24dp,
                    label = stringResource(R.string.lion_tools),
                    background = if (darkTheme) Color(0x33DBEAFE) else GlassMenuTokens.actionTint,
                    iconTint = if (darkTheme) Color(0xFF93C5FD) else Color(0xFF1E40AF),
                    onClick = onMore
                )
                CircleActionButton(
                    icon = R.drawable.ic_delete_24dp,
                    label = stringResource(R.string.action_delete),
                    background = if (darkTheme) Color(0x33FEE2E2) else GlassMenuTokens.destructiveTint,
                    iconTint = if (darkTheme) Color(0xFFFCA5A5) else GlassMenuTokens.destructive,
                    onClick = onRemove
                )
            }
        }
    }
}

/** 36dp pastel circle with a 44dp touch target, flat (no elevation). */
@Composable
private fun CircleActionButton(
    icon: Int,
    label: String,
    background: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(36.dp).background(background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
        }
    }
}

private fun animatorsEnabled(): Boolean =
    android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()

@Composable
private fun LionPowerButton(isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isRunning) R.string.lion_tap_disconnect else R.string.lion_tap_connect)
    val darkTheme = LocalDarkTheme.current
    val opacity = if (enabled) 1f else 0.45f
    val wave = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        wave.snapTo(0f)
        if (isRunning && animatorsEnabled()) {
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

internal data class ServerTab(val groupId: String, val remarks: String)

/**
 * One Default tab first, then every imported subscription in saved order.
 *
 * The stored default group (`__default_subscription__`) and the all-servers group (`""`)
 * are the same logical tab as the built-in Default entry, so whichever of them exists
 * becomes the first tab and is never repeated. Duplicate group ids collapse to one tab.
 */
internal fun serverTabs(groups: List<GroupMapItem>, defaultLabel: String): List<ServerTab> {
    val defaultId = groups.firstOrNull { it.id.isEmpty() }?.id
        ?: groups.firstOrNull { it.id == AppConfig.DEFAULT_SUBSCRIPTION_ID }?.id
        ?: ""
    val tabs = mutableListOf(ServerTab(defaultId, defaultLabel))
    val seen = mutableSetOf(defaultId, "", AppConfig.DEFAULT_SUBSCRIPTION_ID)
    groups.forEach { group ->
        if (seen.add(group.id)) tabs += ServerTab(group.id, group.remarks)
    }
    return tabs
}

/** The empty card belongs to the selected tab only, and only once that tab really has no servers. */
internal fun shouldShowEmptyState(
    tabGroupId: String,
    selectedGroupId: String,
    loaded: Boolean,
    serverCount: Int,
    queryBlank: Boolean
): Boolean = loaded && queryBlank && serverCount == 0 && tabGroupId == selectedGroupId

/** Default (single keys) plus every imported subscription, side by side and swipeable. */
@Composable
private fun LionServersPage(
    modifier: Modifier,
    uiState: MainUiState,
    mainViewModel: MainViewModel,
    isLoading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onAction: (MainAction) -> Unit,
    onMore: (String, ProfileItem) -> Unit,
    onRemove: (String, String) -> Unit
) {
    val darkTheme = LocalDarkTheme.current
    val defaultLabel = stringResource(R.string.lion_tab_default)
    val tabs = remember(uiState.groups, defaultLabel) { serverTabs(uiState.groups, defaultLabel) }
    val tabIds = remember(tabs) { tabs.map { it.groupId } }
    val pagerState = rememberPagerState(
        initialPage = remember { tabs.indexOfFirst { it.groupId == uiState.selectedGroupId }.coerceAtLeast(0) },
        pageCount = { tabs.size }
    )
    val activeTab = tabs.getOrNull(pagerState.settledPage)
    val latestSelectedGroupId by rememberUpdatedState(uiState.selectedGroupId)
    val latestTabs by rememberUpdatedState(tabs)
    val latestOnAction by rememberUpdatedState(onAction)

    // Keep the pager on the selected group; an unrepresented selection falls back to the first tab.
    LaunchedEffect(tabIds, uiState.selectedGroupId) {
        if (tabIds.isEmpty()) return@LaunchedEffect
        val index = tabIds.indexOf(uiState.selectedGroupId)
        if (index >= 0) {
            if (index != pagerState.settledPage) pagerState.animateScrollToPage(index)
        } else if (pagerState.settledPage == 0) {
            onAction(MainAction.SelectGroup(tabIds.first()))
        }
    }
    // A swipe selects its final tab only after the pager settles, never an intermediate page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                val id = latestTabs.getOrNull(page)?.groupId ?: return@collect
                if (id != latestSelectedGroupId) latestOnAction(MainAction.SelectGroup(id))
            }
    }

    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lion_servers),
                    Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = glassTextPrimary()
                )
                TextButton(
                    onClick = { onAction(MainAction.TestAllServers) },
                    enabled = !uiState.isTesting &&
                        activeTab?.groupId == uiState.selectedGroupId &&
                        uiState.groups.any { it.id == activeTab.groupId }
                ) {
                    Text(stringResource(R.string.lion_test), color = GlassTokens.accent)
                }
            }
            AutoSelectCard(
                enabled = uiState.autoSelectFastest,
                onClick = { onAction(MainAction.SetAutoSelectFastest(!uiState.autoSelectFastest)) }
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.lion_search), color = glassTextSecondary()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = if (darkTheme) Color(0x24FFFFFF) else Color(0x8CFFFFFF),
                    border = BorderStroke(1.dp, Color.White)
                ) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(tabs) { index, tab ->
                            val selectedTab = index == pagerState.currentPage
                            val selectedLabel = stringResource(R.string.lion_tab_selected)
                            Box(
                                Modifier
                                    .heightIn(min = 44.dp)
                                    .widthIn(max = 168.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selectedTab) {
                                            if (darkTheme) Color(0x334338CA) else Color(0xFFDBEAFE)
                                        } else Color.Transparent
                                    )
                                    .clickable(role = Role.Tab) {
                                        onAction(MainAction.SelectGroup(tab.groupId))
                                    }
                                    .semantics {
                                        if (selectedTab) stateDescription = selectedLabel
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tab.remarks,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab) {
                                        if (darkTheme) Color(0xFFC7D2FE) else Color(0xFF1E40AF)
                                    } else glassTextSecondary(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                val activeId = activeTab?.groupId.orEmpty()
                if (activeId.isNotEmpty()) {
                    CircleActionButton(
                        icon = R.drawable.ic_refresh_24dp,
                        label = stringResource(R.string.lion_update_subscription),
                        background = if (darkTheme) Color(0x33DBEAFE) else Color(0xFFEEF2FF),
                        iconTint = if (darkTheme) Color(0xFF93C5FD) else Color(0xFF1E40AF),
                        onClick = { onAction(MainAction.UpdateGroupSubscription(activeId)) }
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top
        ) { page ->
            val tab = tabs.getOrNull(page) ?: return@HorizontalPager
            ServerGroupPage(
                tab = tab,
                query = query,
                uiState = uiState,
                mainViewModel = mainViewModel,
                isLoading = isLoading,
                onAction = onAction,
                onMore = onMore,
                onRemove = onRemove
            )
        }
    }
}

@Composable
private fun AutoSelectCard(enabled: Boolean, onClick: () -> Unit) {
    val darkTheme = LocalDarkTheme.current
    val stateLabel = stringResource(if (enabled) R.string.lion_auto_on else R.string.lion_auto_off)
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        tint = GlassTints.iceBlue,
        tintAlpha = 0.65f
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Switch, onClick = onClick)
                .semantics {
                    stateDescription = stateLabel
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(
                    if (enabled) {
                        if (darkTheme) Color(0x334338CA) else Color(0xFFDBEAFE)
                    } else if (darkTheme) Color(0x24FFFFFF) else Color(0x99FFFFFF),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (enabled) R.drawable.ic_flash_on_24dp else R.drawable.ic_flash_off_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (enabled) {
                        if (darkTheme) Color(0xFFC7D2FE) else Color(0xFF1E40AF)
                    } else Color(0xFF64748B)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.lion_auto_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glassTextPrimary()
                )
                Text(
                    stringResource(R.string.lion_auto_detail),
                    fontSize = 12.sp,
                    color = glassTextSecondary()
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (enabled) {
                    if (darkTheme) Color(0x334338CA) else Color(0xFF1E40AF)
                } else if (darkTheme) Color(0x24FFFFFF) else Color(0x99FFFFFF)
            ) {
                Text(
                    stringResource(if (enabled) R.string.lion_auto_on else R.string.lion_auto_off),
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        enabled -> Color.White
                        darkTheme -> Color(0xFFCBD5E1)
                        else -> Color(0xFF64748B)
                    }
                )
            }
        }
    }
}

@Composable
private fun ServerGroupPage(
    tab: ServerTab,
    query: String,
    uiState: MainUiState,
    mainViewModel: MainViewModel,
    isLoading: Boolean,
    onAction: (MainAction) -> Unit,
    onMore: (String, ProfileItem) -> Unit,
    onRemove: (String, String) -> Unit
) {
    val flow = remember(tab.groupId) { mainViewModel.serverGroupState(tab.groupId) }
    val state by flow.collectAsStateWithLifecycle()
    val rows = remember(state.rows, tab.groupId) {
        if (tab.groupId.isEmpty()) {
            state.rows.filter {
                it.profile.subscriptionId.isEmpty() ||
                    it.profile.subscriptionId == AppConfig.DEFAULT_SUBSCRIPTION_ID
            }
        } else {
            state.rows
        }
    }
    val showEmptyState = shouldShowEmptyState(
        tabGroupId = tab.groupId,
        selectedGroupId = uiState.selectedGroupId,
        loaded = state.loaded,
        serverCount = rows.size,
        queryBlank = query.isBlank()
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showEmptyState) {
            item(key = "empty") {
                ConsoleCard {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.lion_empty),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTextPrimary()
                        )
                        Text(
                            stringResource(if (tab.groupId.isEmpty()) R.string.lion_dashboard_no_server_detail else R.string.lion_empty_detail),
                            fontSize = 14.sp,
                            color = glassTextSecondary()
                        )
                    }
                }
            }
        } else if (state.loaded && rows.isEmpty() && query.isNotBlank()) {
            item(key = "no_results") {
                ConsoleCard {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.lion_no_results),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTextPrimary()
                        )
                    }
                }
            }
        }
        items(rows, key = { it.guid }) { row ->
            LionServerCard(
                row,
                row.guid == uiState.selectedGuid,
                onSelect = { onAction(MainAction.SelectServer(row.guid)) },
                onMore = { onMore(row.guid, row.profile) },
                onRemove = { onRemove(row.guid, row.remarks) }
            )
        }
    }
}
