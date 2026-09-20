package com.joaohouto.clusterplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterplayer.ui.theme.LocalClusterAccent
import com.joaohouto.clusterplayer.ui.theme.MetallicBorder
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary

enum class MetallicButtonStyle {
    Standard,
    Accent,
    Outlined
}

@Composable
fun MetallicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MetallicButtonStyle = MetallicButtonStyle.Standard,
    icon: ImageVector? = null,
    text: String? = null,
    isActive: Boolean = false,
    minSize: Dp = 60.dp,
    iconSize: Dp = 28.dp,
    textSize: TextUnit = 16.sp,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = LocalClusterAccent.current

    val backgroundBrush = when (style) {
        MetallicButtonStyle.Accent -> Brush.verticalGradient(
            colors = listOf(accent.primary, accent.dark)
        )
        MetallicButtonStyle.Standard -> if (isActive) {
            Brush.verticalGradient(listOf(MetallicIntermediate, SurfaceCard))
        } else {
            Brush.verticalGradient(listOf(SurfaceCard, Color(0xFF101215)))
        }
        MetallicButtonStyle.Outlined -> Brush.verticalGradient(
            colors = listOf(SurfaceCard, SurfaceCard)
        )
    }

    val borderStroke = when {
        isActive -> BorderStroke(1.5.dp, accent.primary)
        style == MetallicButtonStyle.Accent -> BorderStroke(1.dp, accent.primary.copy(alpha = 0.8f))
        else -> BorderStroke(1.dp, SurfaceCardBorder)
    }

    val contentColor = when {
        isActive -> accent.primary
        style == MetallicButtonStyle.Accent -> TextPrimary
        else -> TextPrimary
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = if (style == MetallicButtonStyle.Accent) Color.White else accent.primary),
                onClick = onClick
            ),
        shape = shape,
        border = borderStroke,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .defaultMinSize(minWidth = minSize, minHeight = minSize)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription ?: text,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
                if (icon != null && !text.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!text.isNullOrEmpty()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = textSize,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
