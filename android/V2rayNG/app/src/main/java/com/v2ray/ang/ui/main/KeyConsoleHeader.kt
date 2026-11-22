package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.KeyConsoleTokens

@Composable
fun KeyConsoleHeader(isLoading: Boolean, onAction: (MainAction) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(KeyConsoleTokens.radius),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(KeyConsoleTokens.space)
        ) {
            Text(
                text = stringResource(R.string.key_console_eyebrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.key_console_headline),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.key_console_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onAction(MainAction.ImportClipboard) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.key_console_import))
            }
        }
    }
}
