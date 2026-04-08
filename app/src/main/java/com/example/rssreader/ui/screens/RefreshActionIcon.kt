package com.example.rssreader.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@Composable
internal fun RefreshActionIcon(
    isRefreshing: Boolean,
    contentDescription: String
) {
    if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_action_icon")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 900,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "refresh_action_icon_rotation"
        )
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = contentDescription,
            modifier = Modifier.rotate(rotation)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = contentDescription
        )
    }
}

