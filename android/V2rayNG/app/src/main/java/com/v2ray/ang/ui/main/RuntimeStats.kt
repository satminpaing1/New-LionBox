package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.core.countryFlag
import com.v2ray.ang.core.formatUptime
import com.v2ray.ang.extension.toSpeedString

@Composable
internal fun RuntimeStats(ui: MainUiState) {
    val stats = ui.runtime
    val unavailable = stringResource(R.string.runtime_unavailable)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.runtime_uptime, if (ui.isRunning && stats != null) formatUptime(stats.uptimeSeconds) else unavailable), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.runtime_speed,
            if (ui.isRunning && stats != null) stats.downloadBytesPerSecond.toSpeedString() else unavailable,
            if (ui.isRunning && stats != null) stats.uploadBytesPerSecond.toSpeedString() else unavailable), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.runtime_network, stringResource(when (stats?.transport) {
            "wifi" -> R.string.runtime_wifi
            "cellular" -> R.string.runtime_cellular
            "ethernet" -> R.string.runtime_ethernet
            else -> R.string.runtime_unavailable
        })), style = MaterialTheme.typography.bodyMedium)
        val endpoint = when {
            !ui.isRunning -> unavailable
            stats == null || stats.ipLookupState == "loading" -> stringResource(R.string.runtime_ip_loading)
            stats.ipLookupState != "ready" -> stringResource(R.string.runtime_ip_error)
            else -> listOfNotNull(countryFlag(stats.countryCode), stats.countryName ?: stats.countryCode, stats.ipAddress).joinToString(" · ")
        }
        Text(stringResource(R.string.runtime_exit_ip, endpoint), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.runtime_ip_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
