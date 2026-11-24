package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.GlassBottomSheet
import com.v2ray.ang.ui.compose.GlassDialog
import com.v2ray.ang.ui.compose.GlassDialogActionRow
import com.v2ray.ang.ui.compose.GlassDialogHeader
import com.v2ray.ang.ui.compose.GlassDialogPillButton
import com.v2ray.ang.ui.compose.GlassDialogSectionHeader
import com.v2ray.ang.ui.compose.GlassMenuTokens

private enum class ImportMenuAction(@StringRes val labelRes: Int, val action: MainAction) {
    QRCode(R.string.menu_item_import_config_qrcode, MainAction.ImportQRcode),
    Clipboard(R.string.menu_item_import_config_clipboard, MainAction.ImportClipboard),
    LocalFile(R.string.menu_item_import_config_local, MainAction.ImportConfigLocal),
    PolicyGroup(R.string.menu_item_import_config_policy_group, MainAction.ImportManually(EConfigType.POLICYGROUP.value)),
    ProxyChain(R.string.menu_item_import_config_proxy_chain, MainAction.ImportManually(EConfigType.PROXYCHAIN.value)),
    Vmess(R.string.menu_item_import_config_manually_vmess, MainAction.ImportManually(EConfigType.VMESS.value)),
    Vless(R.string.menu_item_import_config_manually_vless, MainAction.ImportManually(EConfigType.VLESS.value)),
    Shadowsocks(R.string.menu_item_import_config_manually_ss, MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)),
    Socks(R.string.menu_item_import_config_manually_socks, MainAction.ImportManually(EConfigType.SOCKS.value)),
    Http(R.string.menu_item_import_config_manually_http, MainAction.ImportManually(EConfigType.HTTP.value)),
    Trojan(R.string.menu_item_import_config_manually_trojan, MainAction.ImportManually(EConfigType.TROJAN.value)),
    WireGuard(R.string.menu_item_import_config_manually_wireguard, MainAction.ImportManually(EConfigType.WIREGUARD.value)),
    Hysteria2(R.string.menu_item_import_config_manually_hysteria2, MainAction.ImportManually(EConfigType.HYSTERIA2.value))
}

enum class MainMoreMenuAction(@StringRes val labelRes: Int) {
    RestartService(R.string.title_service_restart),
    DeleteAll(R.string.title_del_all_config),
    DeleteDuplicate(R.string.title_del_duplicate_config),
    DeleteInvalid(R.string.title_del_invalid_config),
    ExportAll(R.string.title_export_all),
    LocateSelected(R.string.title_locate_selected_config),
    SortByTestResults(R.string.title_sort_by_test_results),
    TestAll(R.string.title_ping_all_server),
    TestAllRealPing(R.string.title_real_ping_all_server),
    UpdateSubscriptions(R.string.title_sub_update)
}

internal enum class ServerMenuAction(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQRCode(R.string.share_method_qrcode, isShareAction = true, supportsComplexProfiles = false),
    ShareClipboard(R.string.share_method_clipboard, isShareAction = true, supportsComplexProfiles = false),
    ShareFullContent(R.string.share_method_full_content, isShareAction = true, supportsComplexProfiles = true),
    Edit(R.string.action_edit, isShareAction = false, supportsComplexProfiles = true),
    Delete(R.string.action_delete, isShareAction = false, supportsComplexProfiles = true),
}

internal fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
): List<ServerMenuAction> = ServerMenuAction.entries.filter { action ->
    (includeManagementActions || action.isShareAction) && (!isComplexProfile || action.supportsComplexProfiles)
}

@Composable
fun ImportMenuContent(onAction: (MainAction) -> Unit) = AppDropdownMenuItems(
    items = ImportMenuAction.entries,
    labelRes = { it.labelRes },
    onSelected = { onAction(it.action) }
)

/**
 * The add-config menu as a scrollable frosted sheet: an IMPORT section and an ADD CONFIG section.
 */
@Composable
fun ImportOptionsSheet(
    onPaste: () -> Unit,
    onAction: (MainAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val importActions = ImportMenuAction.entries.take(3)
    val configActions = ImportMenuAction.entries.drop(3)
    GlassBottomSheet(onDismissRequest = onDismissRequest) {
        GlassDialogHeader(stringResource(R.string.menu_item_add_config))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            GlassDialogSectionHeader(stringResource(R.string.lion_import_group_import))
            GlassDialogActionRow(
                iconRes = R.drawable.ic_source_code_24dp,
                label = stringResource(R.string.lion_paste),
                onClick = onPaste,
                badgeTint = GlassMenuTokens.skyTint,
                iconTint = GlassMenuTokens.indigoInk
            )
            val importIcons = listOf(
                R.drawable.ic_qu_scan_24dp,
                R.drawable.ic_copy,
                R.drawable.ic_file_24dp
            )
            importActions.forEachIndexed { index, item ->
                GlassDialogActionRow(
                    iconRes = importIcons[index],
                    label = stringResource(item.labelRes),
                    onClick = { onAction(item.action) },
                    badgeTint = GlassMenuTokens.skyTint,
                    iconTint = GlassMenuTokens.indigoInk
                )
            }
            GlassDialogSectionHeader(stringResource(R.string.lion_import_group_config))
            configActions.forEach { item ->
                GlassDialogActionRow(
                    iconRes = R.drawable.ic_add_24dp,
                    label = stringResource(item.labelRes),
                    onClick = { onAction(item.action) },
                    badgeTint = GlassMenuTokens.indigoTint,
                    iconTint = GlassMenuTokens.action
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassDialogPillButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismissRequest
        )
    }
}

@Composable
fun MoreMenuContent(onSelected: (MainMoreMenuAction) -> Unit) = AppDropdownMenuItems(
    items = MainMoreMenuAction.entries,
    labelRes = { it.labelRes },
    onSelected = onSelected,
    destructive = {
        it == MainMoreMenuAction.DeleteAll ||
            it == MainMoreMenuAction.DeleteDuplicate ||
            it == MainMoreMenuAction.DeleteInvalid
    }
)

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
    )
    GlassDialog(onDismissRequest = onDismiss) {
        GlassDialogHeader(stringResource(R.string.app_name), profile.remarks)
        menuActions.forEach { action ->
            GlassDialogActionRow(
                iconRes = when (action) {
                    ServerMenuAction.ShareQRCode -> R.drawable.ic_qu_scan_24dp
                    ServerMenuAction.ShareClipboard -> R.drawable.ic_copy
                    ServerMenuAction.ShareFullContent -> R.drawable.ic_share_24dp
                    ServerMenuAction.Edit -> R.drawable.ic_edit_24dp
                    ServerMenuAction.Delete -> R.drawable.ic_delete_24dp
                },
                label = stringResource(action.labelRes),
                destructive = action == ServerMenuAction.Delete,
                onClick = {
                    onDismiss()
                    when (action) {
                        ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                        ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                        ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                        ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                        ServerMenuAction.Delete -> onRemove(guid, profile.remarks)
                    }
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassDialogPillButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss
        )
    }
}
