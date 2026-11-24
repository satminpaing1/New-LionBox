package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.ambientBackground
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
    LaunchedEffect(uiState.isRunning, uiState.selectedGuid) {
        if (uiState.isRunning) onAction(MainAction.TestCurrentServer)
    }
    Scaffold(
        modifier = Modifier.ambientBackground(com.v2ray.ang.ui.compose.LocalDarkTheme.current, colors.background),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(painterResource(R.drawable.key_console_launcher), null,
                    Modifier.size(44.dp), tint = Color.Unspecified)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.lion_subtitle), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
                if (tab.canImport) Box {
                    IconButton(onClick = { importMenu = true }, enabled = !isLoading) {
                        Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.menu_item_add_config))
                    }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lion_paste)) }, onClick = { importMenu = false; importVisible = true })
                        ImportMenuContent { importMenu = false; onAction(it) }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.lion_tools))
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        MoreMenuContent { moreMenu = false; onMoreMenuAction(it) }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(50),
                color = if (com.v2ray.ang.ui.compose.LocalDarkTheme.current) colors.surfaceContainerLow else com.v2ray.ang.ui.compose.AmbientTokens.glass,
                border = BorderStroke(1.dp, if (com.v2ray.ang.ui.compose.LocalDarkTheme.current) colors.outlineVariant else com.v2ray.ang.ui.compose.AmbientTokens.glassBorder),
                shadowElevation = 4.dp
            ) {
                NavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    LionBoxTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; query = ""; onAction(MainAction.Search("")) },
                            icon = { Icon(painterResource(item.icon), null) },
                            label = { Text(stringResource(item.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = colors.primaryContainer)
                        )
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (tab == LionBoxTab.Dashboard) {
                    item {
                        ConsoleCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!uiState.isRunning) {
                                    Surface(shape = CircleShape, color = colors.primaryContainer, border = BorderStroke(1.dp, colors.primary)) {
                                        Text(displayText, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                                    }
                                }
                                LionPowerButton(uiState.isRunning, connectionAction(uiState.selectedGuid != null, uiState.isRunning) != null && !isLoading) {
                                    connectionAction(uiState.selectedGuid != null, uiState.isRunning)?.let(onAction)
                                }
                                Text(stringResource(if (uiState.isRunning) R.string.lion_tap_disconnect else R.string.lion_tap_connect), modifier = Modifier.fillMaxWidth(), color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(selected?.remarks ?: stringResource(R.string.lion_no_server), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                TrafficStats(uiState)
                                OutlinedButton(onClick = { onAction(if (uiState.isRunning) MainAction.TestCurrentServer else MainAction.TestRealAllServers) }, enabled = uiState.selectedGuid != null && !uiState.isTesting && !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                                    Text(stringResource(if (uiState.isTesting) R.string.connection_test_testing else R.string.connection_test_pending))
                                }
                            }
                        }
                    }
                    item(key = "runtime") { RuntimeStats(uiState, selected) }
                }
                if (tab.canImport) {
                    item {
                        Button(onClick = { importVisible = true }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(painterResource(R.drawable.ic_add_24dp), null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lion_paste))
                        }
                    }
                }
                if (tab == LionBoxTab.Dashboard || tab == LionBoxTab.Servers) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.lion_servers), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onAction(MainAction.TestAllServers) }, enabled = group.rows.isNotEmpty() && !uiState.isTesting) { Text(stringResource(R.string.lion_test)) }
                        }
                    }
                    if (uiState.groups.size > 1) item {
                        var groupMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { groupMenu = true }) { Text(uiState.groups.firstOrNull { it.id == uiState.selectedGroupId }?.remarks.orEmpty()) }
                            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                uiState.groups.forEach { entry ->
                                    DropdownMenuItem(text = { Text(entry.remarks) }, onClick = { groupMenu = false; onAction(MainAction.SelectGroup(entry.id)) })
                                }
                            }
                        }
                    }
                    if (tab == LionBoxTab.Servers) item {
                        OutlinedTextField(value = query, onValueChange = { query = it; onAction(MainAction.Search(it)) }, label = { Text(stringResource(R.string.lion_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    }
                    if (group.rows.isEmpty()) item {
                        ConsoleCard {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(if (query.isBlank()) R.string.lion_empty else R.string.lion_no_results), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.lion_empty_detail), color = colors.onSurfaceVariant)
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
                    item { Text(stringResource(R.string.title_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    items(listOf(MainDestination.Settings, MainDestination.Subscriptions, MainDestination.PerAppProxy, MainDestination.Routing, MainDestination.UserAssets, MainDestination.BackupRestore, MainDestination.About)) { destination ->
                        ConsoleCard {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onNavigate(destination) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(painterResource(destination.iconRes), null, tint = colors.primary)
                                Text(stringResource(destination.labelRes), style = MaterialTheme.typography.titleMedium)
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
private fun ConsoleCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = com.v2ray.ang.ui.compose.LocalDarkTheme.current
    Surface(
        shape = RoundedCornerShape(com.v2ray.ang.ui.compose.AmbientTokens.radius),
        color = if (dark) colors.surfaceContainer else com.v2ray.ang.ui.compose.AmbientTokens.glass,
        border = BorderStroke(1.dp, if (dark) colors.outlineVariant else com.v2ray.ang.ui.compose.AmbientTokens.glassBorder),
        shadowElevation = 2.dp,
        content = content
    )
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
    val colors = MaterialTheme.colorScheme
    val dark = com.v2ray.ang.ui.compose.LocalDarkTheme.current
    Surface(
        shape = RoundedCornerShape(40.dp),
        color = if (dark) colors.surfaceContainer else com.v2ray.ang.ui.compose.AmbientTokens.glass,
        border = BorderStroke(1.dp, if (selected) colors.primary else if (dark) colors.outlineVariant else com.v2ray.ang.ui.compose.AmbientTokens.glassBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.remarks, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = colors.primaryContainer) {
                        Text(row.typeDescription, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
                    }
                    val delay = row.testDelayMillis
                    val measured = delay != null && delay > 0
                    Surface(shape = RoundedCornerShape(50), color = if (measured) colors.secondaryContainer else colors.surfaceVariant) {
                        Text(delayLabel(delay), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (measured) colors.onSecondaryContainer else colors.onSurfaceVariant)
                    }
                }
                Text(row.statistics, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.lion_tools), modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LionPowerButton(isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isRunning) R.string.lion_tap_disconnect else R.string.lion_tap_connect)
    val colors = MaterialTheme.colorScheme
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(com.v2ray.ang.ui.compose.AmbientTokens.accentStart, com.v2ray.ang.ui.compose.AmbientTokens.accentEnd)
    )
    val position = remember { androidx.compose.animation.core.Animatable(if (isRunning) 1f else 0f) }
    LaunchedEffect(isRunning) {
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            position.animateTo(if (isRunning) 1f else 0f, androidx.compose.animation.core.tween(200))
        } else {
            position.snapTo(if (isRunning) 1f else 0f)
        }
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        shadowElevation = if (enabled) 6.dp else 0.dp,
        modifier = Modifier.size(width = 156.dp, height = 88.dp).semantics {
            contentDescription = label
            stateDescription = label
            role = Role.Switch
            toggleableState = if (isRunning) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
        }
    ) {
        Box(Modifier.background(if (enabled) gradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)))) {
            Surface(
                modifier = Modifier.padding(8.dp).offset(x = (68 * position.value).dp).size(72.dp),
                shape = CircleShape,
                color = colors.surface,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_vpn_monochrome), null, modifier = Modifier.size(32.dp), tint = if (enabled) colors.primary else colors.onSurfaceVariant)
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
        Text(stringResource(R.string.lion_logs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (logs.isEmpty() && !loading) Text(stringResource(R.string.lion_logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs.size) { index -> Text(logs[index], style = MaterialTheme.typography.bodySmall) }
        }
    }
}
