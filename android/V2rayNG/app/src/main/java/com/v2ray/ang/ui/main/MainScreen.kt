package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.QRCodeDialog

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val availableUpdate by mainViewModel.availableUpdate.collectAsStateWithLifecycle()
    availableUpdate?.let { update ->
        val installState by mainViewModel.apkInstallState.collectAsStateWithLifecycle()
        val installPermissionRequired by mainViewModel.installPermissionRequired.collectAsStateWithLifecycle()
        val installError by mainViewModel.installError.collectAsStateWithLifecycle()
        AppUpdateDialog(
            update = update,
            installState = installState,
            installPermissionRequired = installPermissionRequired,
            installError = installError,
            onAction = onAction
        )
    }
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val displayText = mainViewModel.formatStatus(uiState.status)
    var showDelAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showDelDuplicateConfirm by rememberSaveable { mutableStateOf(false) }
    var showDelInvalidConfirm by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) { mutableStateOf<ServerDeleteTarget?>(null) }
    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, remarks ->
        if (uiState.confirmRemove) showRemoveConfirm = ServerDeleteTarget(guid, remarks)
        else onAction(MainAction.RemoveServer(guid))
    }
    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )
    shareTarget?.let { (guid, profile, more) ->
        ShareMethodDialog(guid, profile, more, onDismiss = { shareTarget = null }, onAction = onAction, onRemove = removeServer)
    }
    uiState.shareQRCodeBitmap?.let { bitmap ->
        QRCodeDialog(bitmap = bitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }
    LionBoxConsole(
        uiState = uiState,
        mainViewModel = mainViewModel,
        isLoading = isLoading,
        displayText = displayText,
        onAction = onAction,
        onNavigate = onNavigate,
        onMore = { guid, profile -> shareTarget = Triple(guid, profile, true) },
        onRemove = removeServer,
        onMoreMenuAction = { action ->
            when (action) {
                MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
            }
        }
    )
}
