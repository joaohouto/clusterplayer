package com.joaohouto.clusterplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterplayer.ui.theme.NeedleRed
import com.joaohouto.clusterplayer.ui.theme.TextSecondary
import com.joaohouto.clusterplayer.ui.theme.TrackRail
import java.util.Locale

@Composable
fun ProgressBarSlider(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val validDuration = if (durationMs > 0) durationMs else 1L
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (currentPositionMs.toFloat() / validDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayedElapsedMs = if (isDragging) {
        (dragFraction * validDuration).toLong()
    } else {
        currentPositionMs
    }

    val remainingMs = (validDuration - displayedElapsedMs).coerceAtLeast(0L)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Interactive Automotive Progress Rail
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp) // Generous touch target for driver safety
                .pointerInput(validDuration) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (newFraction * validDuration).toLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(validDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetMs = (dragFraction * validDuration).toLong()
                            onSeek(targetMs)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            val railHeight = 6.dp.toPx()
            val centerY = size.height / 2f
            val topY = centerY - (railHeight / 2f)
            val cornerRadius = CornerRadius(railHeight / 2f, railHeight / 2f)

            // Background Rail: dark metallic gray #262930
            drawRoundRect(
                color = TrackRail,
                topLeft = Offset(0f, topY),
                size = Size(size.width, railHeight),
                cornerRadius = cornerRadius
            )

            // Progress Fill: Sporty Needle Red #E61924
            val fillWidth = size.width * currentFraction
            if (fillWidth > 0) {
                drawRoundRect(
                    color = NeedleRed,
                    topLeft = Offset(0f, topY),
                    size = Size(fillWidth, railHeight),
                    cornerRadius = cornerRadius
                )
            }

            // Needle thumb indicator
            val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(fillWidth.coerceIn(thumbRadius, size.width - thumbRadius), centerY)
            )
            drawCircle(
                color = NeedleRed,
                radius = thumbRadius - 2.dp.toPx(),
                center = Offset(fillWidth.coerceIn(thumbRadius, size.width - thumbRadius), centerY)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time labels: elapsed left, remaining right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(displayedElapsedMs),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "-${formatTime(remainingMs)}",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
