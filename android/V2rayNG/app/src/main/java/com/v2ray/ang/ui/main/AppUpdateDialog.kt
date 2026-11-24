package com.v2ray.ang.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.handler.ApkInstallState
import com.v2ray.ang.handler.plainReleaseNotes
import com.v2ray.ang.ui.compose.GlassDialog
import com.v2ray.ang.ui.compose.GlassDialogPillButton
import com.v2ray.ang.ui.compose.GlassMenuTokens
import com.v2ray.ang.ui.compose.glassDialogSectionLabelColor
import com.v2ray.ang.ui.compose.glassDialogSubtitleColor
import com.v2ray.ang.ui.compose.glassMenuItemInkColor

private val updateGradient = listOf(Color(0xFF38BDF8), Color(0xFF2563EB))

/**
 * Frosted-glass "Update Available" dialog with release notes, in-app download
 * progress, install-permission guidance and a gradient Update Now action.
 */
@Composable
internal fun AppUpdateDialog(
    update: CheckUpdateResult,
    installState: ApkInstallState,
    installPermissionRequired: Boolean,
    installError: String?,
    onAction: (MainAction) -> Unit
) {
    LaunchedEffect(installState) {
        if (installState is ApkInstallState.ReadyToInstall) onAction(MainAction.InstallAppUpdate)
    }
    val version = update.latestVersion.orEmpty()
    val notes = plainReleaseNotes(update.releaseNotes)

    GlassDialog(onDismissRequest = { onAction(MainAction.DismissAppUpdate) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lion_update_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = glassMenuItemInkColor()
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = GlassMenuTokens.actionTint) {
                Text(
                    stringResource(R.string.lion_update_badge, version),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassMenuTokens.action,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            stringResource(R.string.lion_update_installed_version, BuildConfig.VERSION_NAME),
            fontSize = 12.sp,
            color = glassDialogSubtitleColor(),
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )

        NotesBlock(notes, rememberScrollState())

        when (installState) {
            is ApkInstallState.Downloading -> DownloadProgressBlock(progress = installState.progress)

            is ApkInstallState.Failed -> Text(
                stringResource(R.string.lion_update_download_failed),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GlassMenuTokens.destructiveText,
                modifier = Modifier.padding(top = 12.dp)
            )

            is ApkInstallState.ReadyToInstall -> Text(
                stringResource(R.string.lion_update_preparing),
                fontSize = 13.sp,
                color = glassDialogSubtitleColor(),
                modifier = Modifier.padding(top = 12.dp)
            )

            ApkInstallState.Idle -> Unit
        }

        if (installPermissionRequired) {
            PermissionBlock(onOpenSettings = { onAction(MainAction.OpenInstallPermissionSettings) })
        }

        if (!installError.isNullOrBlank()) {
            Text(
                stringResource(R.string.lion_update_install_failed),
                fontSize = 13.sp,
                color = GlassMenuTokens.destructiveText,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        if (installState is ApkInstallState.Downloading) {
            GlassDialogPillButton(
                text = stringResource(R.string.lion_update_download_cancel),
                onClick = { onAction(MainAction.CancelAppUpdateDownload) }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassDialogPillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { onAction(MainAction.DismissAppUpdate) },
                    modifier = Modifier.weight(1f)
                )
                GlassDialogPillButton(
                    text = stringResource(if (installState is ApkInstallState.Failed) R.string.lion_update_retry else R.string.lion_update_now),
                    onClick = { onAction(MainAction.DownloadAppUpdate) },
                    modifier = Modifier.weight(1f),
                    primary = true,
                    gradient = updateGradient
                )
            }
        }
    }
}

@Composable
private fun NotesBlock(notes: String, scrollState: ScrollState) {
    Text(
        stringResource(R.string.lion_update_release_notes).uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = glassDialogSectionLabelColor(),
        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 6.dp)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 168.dp)
            .verticalScroll(scrollState)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            notes.ifBlank { stringResource(R.string.lion_update_no_notes) },
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = glassMenuItemInkColor()
        )
    }
}

@Composable
private fun DownloadProgressBlock(
    progress: com.v2ray.ang.handler.ApkDownloadProgress
) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.lion_update_downloading),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = glassMenuItemInkColor()
            )
            Text(
                progress.label(),
                fontSize = 12.sp,
                color = glassDialogSubtitleColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val fraction = (progress.percent ?: 0) / 100f
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp)
                .clip(CircleShape)
                .background(GlassMenuTokens.skyTint)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(updateGradient))
            )
        }
    }
}

@Composable
private fun PermissionBlock(onOpenSettings: () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            stringResource(R.string.lion_update_permission),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = glassDialogSubtitleColor()
        )
        Spacer(Modifier.height(8.dp))
        GlassDialogPillButton(
            text = stringResource(R.string.lion_update_open_settings),
            onClick = onOpenSettings,
            gradient = updateGradient
        )
    }
}
