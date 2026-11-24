package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.core.countryFlag
import com.v2ray.ang.core.formatUptime
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.compose.GlassCard
import com.v2ray.ang.ui.compose.glassTextLabel
import com.v2ray.ang.ui.compose.glassTextPrimary
import com.v2ray.ang.ui.compose.glassTextSecondary

@Composable
internal fun RuntimeStats(ui: MainUiState, selected: ServerRowUiModel?) {
    val stats = ui.runtime
    val unavailable = stringResource(R.string.runtime_unavailable)
    val uptime = if (ui.isRunning && stats != null) formatUptime(stats.uptimeSeconds) else unavailable
    val protocol = dashboardProtocol(selected?.profile)
    val delay = dashboardLatencyMillis(ui.status, ui.isRunning, selected?.testDelayMillis)
    val latency = when {
        ui.isTesting -> stringResource(R.string.connection_test_testing)
        delay != null && delay >= 0 -> stringResource(R.string.server_test_delay_value, delay)
        delay != null -> stringResource(R.string.latency_test_fail)
        else -> stringResource(R.string.connection_test_pending)
    }
    val network = stringResource(when (if (ui.isRunning) stats?.transport else null) {
        "wifi" -> R.string.runtime_wifi
        "cellular" -> R.string.runtime_cellular
        "ethernet" -> R.string.runtime_ethernet
        else -> R.string.runtime_unavailable
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.runtime_exit_ip_label), fontSize = 14.sp, color = glassTextLabel())
                when {
                    !ui.isRunning -> Text(unavailable, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary())
                    stats == null || stats.ipLookupState == "loading" -> Text(stringResource(R.string.runtime_ip_loading), fontSize = 14.sp, color = glassTextSecondary())
                    stats.ipLookupState != "ready" -> Text(stringResource(R.string.runtime_ip_error), fontSize = 14.sp, color = glassTextSecondary())
                    else -> {
                        val country = stats.countryName?.takeIf { it.isNotBlank() } ?: stats.countryCode?.takeIf { it.isNotBlank() } ?: unavailable
                        val flag = countryFlag(stats.countryCode).orEmpty()
                        Text(
                            listOf(flag, country).filter { it.isNotBlank() }.joinToString("  "),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTextPrimary()
                        )
                        Text(stats.ipAddress ?: unavailable, fontSize = 15.sp, color = glassTextLabel())
                    }
                }
                Text(stringResource(R.string.runtime_ip_privacy), fontSize = 12.sp, color = glassTextSecondary())
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val singleColumn = maxWidth / LocalDensity.current.fontScale < 300.dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (singleColumn) {
                    RuntimeMetric(stringResource(R.string.runtime_uptime_label), uptime)
                    RuntimeMetric(stringResource(R.string.lion_latency), latency)
                    RuntimeMetric(stringResource(R.string.lion_protocol), protocol)
                    RuntimeMetric(stringResource(R.string.runtime_network_label), network)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RuntimeMetric(stringResource(R.string.runtime_uptime_label), uptime, Modifier.weight(1f))
                        RuntimeMetric(stringResource(R.string.lion_latency), latency, Modifier.weight(1f))
                    }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RuntimeMetric(stringResource(R.string.lion_protocol), protocol, Modifier.weight(1f).fillMaxHeight())
                        RuntimeMetric(stringResource(R.string.runtime_network_label), network, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrafficStats(ui: MainUiState) {
    val stats = ui.runtime
    val unavailable = stringResource(R.string.runtime_unavailable)
    val download = if (ui.isRunning && stats != null) stats.downloadBytesPerSecond.toSpeedString() else unavailable
    val upload = if (ui.isRunning && stats != null) stats.uploadBytesPerSecond.toSpeedString() else unavailable
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeMetric(stringResource(R.string.runtime_download_label), download)
                RuntimeMetric(stringResource(R.string.runtime_upload_label), upload)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeMetric(stringResource(R.string.runtime_download_label), download, Modifier.weight(1f))
                RuntimeMetric(stringResource(R.string.runtime_upload_label), upload, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RuntimeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth().heightIn(min = 96.dp), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 14.sp, color = glassTextLabel())
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = glassTextPrimary(),
                maxLines = 1
            )
        }
    }
}
