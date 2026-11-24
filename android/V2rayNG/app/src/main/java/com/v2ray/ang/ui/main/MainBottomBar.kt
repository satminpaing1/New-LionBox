package com.v2ray.ang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.KeyConsoleTokens

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusPill(displayText = displayText, isRunning = isRunning)
            PowerButton(isRunning = isRunning) { onAction(MainAction.ToggleService) }
        }
    }
}

@Composable
private fun StatusPill(displayText: String, isRunning: Boolean) {
    Surface(
        color = if (isRunning) KeyConsoleTokens.accent.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 32.dp)
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) KeyConsoleTokens.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PowerButton(isRunning: Boolean, onClick: () -> Unit) {
    val ringColor = if (isRunning) KeyConsoleTokens.accent else MaterialTheme.colorScheme.outlineVariant
    val fillColor = if (isRunning) KeyConsoleTokens.accent else MaterialTheme.colorScheme.surface
    val symbolColor = if (isRunning) Color(0xFF14100A) else MaterialTheme.colorScheme.onSurfaceVariant

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(96.dp)
            .background(fillColor, CircleShape)
            .border(width = 4.dp, color = ringColor, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
            val center = Offset(size.width / 2f, size.height * 0.56f)
            val radius = size.minDimension * 0.36f
            // open arc around the top (the gap where the stem passes through)
            drawArc(
                color = symbolColor,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = stroke
            )
            // vertical stem
            drawLine(
                color = symbolColor,
                start = Offset(center.x, center.y - radius - size.height * 0.13f),
                end = Offset(center.x, center.y - radius * 0.25f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}
