package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.core.countryFlag
import com.v2ray.ang.core.formatUptime
import com.v2ray.ang.extension.toSpeedString

@Composable
internal fun RuntimeStats(ui: MainUiState) {
    val stats = ui.runtime
    val colors = MaterialTheme.colorScheme
    val unavailable = stringResource(R.string.runtime_unavailable)
    val uptime = if (ui.isRunning && stats != null) formatUptime(stats.uptimeSeconds) else unavailable
    val download = if (ui.isRunning && stats != null) stats.downloadBytesPerSecond.toSpeedString() else unavailable
    val upload = if (ui.isRunning && stats != null) stats.uploadBytesPerSecond.toSpeedString() else unavailable
    val network = stringResource(when (if (ui.isRunning) stats?.transport else null) {
        "wifi" -> R.string.runtime_wifi
        "cellular" -> R.string.runtime_cellular
        "ethernet" -> R.string.runtime_ethernet
        else -> R.string.runtime_unavailable
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.runtime_exit_ip_label), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                when {
                    !ui.isRunning -> Text(unavailable, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    stats == null || stats.ipLookupState == "loading" -> Text(stringResource(R.string.runtime_ip_loading), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    stats.ipLookupState != "ready" -> Text(stringResource(R.string.runtime_ip_error), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    else -> {
                        val country = stats.countryName?.takeIf { it.isNotBlank() } ?: stats.countryCode?.takeIf { it.isNotBlank() } ?: unavailable
                        val flag = countryFlag(stats.countryCode).orEmpty()
                        Text(listOf(flag, country).filter { it.isNotBlank() }.joinToString("  "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Text(stats.ipAddress ?: unavailable, style = MaterialTheme.typography.bodyLarge, color = colors.primary)
                    }
                }
                Text(stringResource(R.string.runtime_ip_privacy), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val singleColumn = maxWidth / LocalDensity.current.fontScale < 300.dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (singleColumn) {
                    RuntimeMetric(stringResource(R.string.runtime_download_label), download, colors.secondaryContainer, colors.onSecondaryContainer)
                    RuntimeMetric(stringResource(R.string.runtime_upload_label), upload, colors.primaryContainer, colors.onPrimaryContainer)
                    RuntimeMetric(stringResource(R.string.runtime_uptime_label), uptime, colors.primaryContainer, colors.onPrimaryContainer)
                    RuntimeMetric(stringResource(R.string.runtime_network_label), network, colors.tertiaryContainer, colors.onTertiaryContainer)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RuntimeMetric(stringResource(R.string.runtime_download_label), download, colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f))
                        RuntimeMetric(stringResource(R.string.runtime_upload_label), upload, colors.primaryContainer, colors.onPrimaryContainer, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RuntimeMetric(stringResource(R.string.runtime_uptime_label), uptime, colors.primaryContainer, colors.onPrimaryContainer, Modifier.weight(1f))
                        RuntimeMetric(stringResource(R.string.runtime_network_label), network, colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeMetric(label: String, value: String, tint: Color, foreground: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 104.dp).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp),
        color = tint,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(tint, colors.surfaceContainerLow))).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
    }
}
