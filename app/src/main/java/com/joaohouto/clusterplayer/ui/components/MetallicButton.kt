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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterplayer.ui.theme.MetallicBorder
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.NeedleRed
import com.joaohouto.clusterplayer.ui.theme.NeedleRedDark
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
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(12.dp)

    val backgroundBrush = when (style) {
        MetallicButtonStyle.Accent -> Brush.verticalGradient(
            colors = listOf(NeedleRed, NeedleRedDark)
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
        isActive -> BorderStroke(1.5.dp, NeedleRed)
        style == MetallicButtonStyle.Accent -> BorderStroke(1.dp, Color(0xFFFF4D58))
        else -> BorderStroke(1.dp, SurfaceCardBorder)
    }

    val contentColor = when {
        isActive -> NeedleRed
        style == MetallicButtonStyle.Accent -> TextPrimary
        else -> TextPrimary
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = if (style == MetallicButtonStyle.Accent) Color.White else NeedleRed),
                onClick = onClick
            ),
        shape = shape,
        border = borderStroke,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = minSize, minHeight = minSize)
                .fillMaxWidth()
                .fillMaxHeight()
                .background(backgroundBrush)
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
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (icon != null && !text.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!text.isNullOrEmpty()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
