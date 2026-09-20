package com.joaohouto.clusterplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterplayer.R
import com.joaohouto.clusterplayer.ui.components.MetallicButton
import com.joaohouto.clusterplayer.ui.components.MetallicButtonStyle
import com.joaohouto.clusterplayer.ui.theme.ALL_ACCENT_THEMES
import com.joaohouto.clusterplayer.ui.theme.ClusterAccent
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterplayer.ui.theme.LocalClusterAccent
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentAccentTheme: ClusterAccent,
    currentCrossfadeSeconds: Int,
    onSelectAccent: (String) -> Unit,
    onSelectCrossfade: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalClusterAccent.current
    var crossfadeSliderValue by remember(currentCrossfadeSeconds) {
        mutableFloatStateOf(currentCrossfadeSeconds.toFloat())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 680.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = DeepMetallicBackground,
            border = BorderStroke(1.5.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = accent.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }

                    MetallicButton(
                        onClick = onDismiss,
                        icon = Icons.Rounded.Close,
                        minSize = 44.dp,
                        iconSize = 22.dp,
                        contentDescription = stringResource(R.string.btn_close)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 1: Cluster Lighting Color (Cor de Iluminação)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = accent.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_accent_title),
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.settings_accent_subtitle),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Color Badges Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ALL_ACCENT_THEMES.forEach { theme ->
                                val isSelected = theme.id == currentAccentTheme.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onSelectAccent(theme.id) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(theme.primary, theme.dark)
                                                )
                                            )
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color.White else SurfaceCardBorder,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = stringResource(theme.nameRes),
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Gradual Transition / Fade (Transição Gradual)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Timeline,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_crossfade_title),
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_crossfade_subtitle),
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Current Value Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accent.primary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.5f))
                            ) {
                                val seconds = crossfadeSliderValue.roundToInt()
                                val label = if (seconds == 0) {
                                    stringResource(R.string.settings_crossfade_disabled)
                                } else {
                                    stringResource(R.string.settings_crossfade_seconds, seconds)
                                }
                                Text(
                                    text = label,
                                    color = accent.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Slider
                        Slider(
                            value = crossfadeSliderValue,
                            onValueChange = { newValue ->
                                crossfadeSliderValue = newValue
                            },
                            onValueChangeFinished = {
                                onSelectCrossfade(crossfadeSliderValue.roundToInt())
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = accent.primary,
                                inactiveTrackColor = MetallicIntermediate
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "0s", 2 to "2s", 3 to "3s", 5 to "5s", 8 to "8s").forEach { (sec, label) ->
                                val isSelected = crossfadeSliderValue.roundToInt() == sec
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) accent.primary else MetallicIntermediate,
                                    border = BorderStroke(1.dp, if (isSelected) accent.primary else SurfaceCardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            crossfadeSliderValue = sec.toFloat()
                                            onSelectCrossfade(sec)
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.settings_crossfade_hint),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: About App
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${stringResource(R.string.settings_about_app)} v1.1.0",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.settings_about_desc),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
