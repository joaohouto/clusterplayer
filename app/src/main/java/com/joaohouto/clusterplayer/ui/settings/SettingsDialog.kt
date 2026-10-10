package com.joaohouto.clusterplayer.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joaohouto.clusterplayer.R
import com.joaohouto.clusterplayer.ui.theme.ALL_ACCENT_THEMES
import com.joaohouto.clusterplayer.ui.theme.ClusterAccent
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterplayer.ui.theme.LocalClusterAccent
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterplayer.ui.theme.TextDisabled
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary
import kotlin.math.roundToInt

private const val GITHUB_PAGES_URL = "https://joaohouto.github.io/clusterplayer/"

private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    AUDIO(R.string.tab_audio, Icons.AutoMirrored.Rounded.VolumeUp),
    LYRICS(R.string.tab_lyrics, Icons.Rounded.GraphicEq),
    APPEARANCE(R.string.tab_appearance, Icons.Rounded.Settings)
}

/**
 * Modal de configurações padronizado com o design e layout do ClusterLauncher.
 * Navegação em barra lateral esquerda e painel de seções à direita.
 */
@Composable
fun SettingsDialog(
    currentAccentTheme: ClusterAccent,
    currentCrossfadeSeconds: Int,
    currentAppVolumePercent: Int,
    isLoudnessEnabled: Boolean,
    isKeepScreenOn: Boolean,
    isShowLyrics: Boolean,
    currentLyricsOffsetMs: Int = 0,
    isAutoplayOnStart: Boolean,
    isFullScreen: Boolean = false,
    onSelectAccent: (String) -> Unit,
    onSelectCrossfade: (Int) -> Unit,
    onSelectAppVolume: (Int) -> Unit,
    onToggleLoudness: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleShowLyrics: (Boolean) -> Unit,
    onSelectLyricsOffset: (Int) -> Unit = {},
    onToggleAutoplayOnStart: (Boolean) -> Unit,
    onToggleFullScreen: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val accent = LocalClusterAccent.current
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(SettingsTab.AUDIO) }

    var crossfadeSliderValue by remember(currentCrossfadeSeconds) {
        mutableFloatStateOf(currentCrossfadeSeconds.toFloat())
    }
    var volumeSliderValue by remember(currentAppVolumePercent) {
        mutableFloatStateOf(currentAppVolumePercent.toFloat())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                if (isFullScreen) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp)),
            color = DeepMetallicBackground
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Barra Lateral de Navegação (Menu Esquerdo no padrão ClusterLauncher)
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF0A0C0F))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cabeçalho do App com versão
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_settings),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E222B)
                            ) {
                                Text(
                                    text = "v1.2.0",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botões de Abas Principais
                        SettingsTab.entries.forEach { tab ->
                            val isSelected = activeTab == tab
                            val primaryAccent = accent.primary
                            val bgColor = if (isSelected) primaryAccent.copy(alpha = 0.12f) else Color.Transparent
                            val contentColor = if (isSelected) primaryAccent else TextSecondary
                            val borderColor = if (isSelected) primaryAccent.copy(alpha = 0.4f) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.titleRes),
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(tab.titleRes),
                                    color = contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Botão Fechar no rodapé da barra lateral
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = TextDisabled,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.btn_close),
                            color = TextDisabled,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Divisor Vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SurfaceCardBorder)
                )

                // Painel de Conteúdo (Lado Direito)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    when (activeTab) {
                        SettingsTab.AUDIO -> AudioSettingsPanel(
                            currentCrossfadeSeconds = currentCrossfadeSeconds,
                            currentAppVolumePercent = currentAppVolumePercent,
                            isLoudnessEnabled = isLoudnessEnabled,
                            isAutoplayOnStart = isAutoplayOnStart,
                            crossfadeSliderValue = crossfadeSliderValue,
                            onCrossfadeChange = { crossfadeSliderValue = it },
                            onCrossfadeChangeFinished = { onSelectCrossfade(crossfadeSliderValue.roundToInt()) },
                            volumeSliderValue = volumeSliderValue,
                            onVolumeChange = { volumeSliderValue = it },
                            onVolumeChangeFinished = { onSelectAppVolume(volumeSliderValue.roundToInt()) },
                            onToggleLoudness = onToggleLoudness,
                            onToggleAutoplayOnStart = onToggleAutoplayOnStart
                        )

                        SettingsTab.LYRICS -> LyricsSettingsPanel(
                            isShowLyrics = isShowLyrics,
                            currentLyricsOffsetMs = currentLyricsOffsetMs,
                            onToggleShowLyrics = onToggleShowLyrics,
                            onSelectLyricsOffset = onSelectLyricsOffset
                        )

                        SettingsTab.APPEARANCE -> AppearanceSettingsPanel(
                            currentAccentTheme = currentAccentTheme,
                            isKeepScreenOn = isKeepScreenOn,
                            isFullScreen = isFullScreen,
                            onSelectAccent = onSelectAccent,
                            onToggleKeepScreenOn = onToggleKeepScreenOn,
                            onToggleFullScreen = onToggleFullScreen,
                            onOpenWebsite = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PAGES_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Aba 1: Ajustes de Áudio e Reprodução
 */
@Composable
private fun AudioSettingsPanel(
    currentCrossfadeSeconds: Int,
    currentAppVolumePercent: Int,
    isLoudnessEnabled: Boolean,
    isAutoplayOnStart: Boolean,
    crossfadeSliderValue: Float,
    onCrossfadeChange: (Float) -> Unit,
    onCrossfadeChangeFinished: () -> Unit,
    volumeSliderValue: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onToggleLoudness: (Boolean) -> Unit,
    onToggleAutoplayOnStart: (Boolean) -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_audio_category),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Parâmetros de processamento de som e comportamento do tocador.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Normalização de Volume (Loudness Boost)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_loudness_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_loudness_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isLoudnessEnabled,
                    onCheckedChange = onToggleLoudness,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 2. Volume Geral do App
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_volume_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_volume_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_volume_percent, volumeSliderValue.roundToInt()),
                        color = accent.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = volumeSliderValue,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange = 10f..100f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Transição Gradual Crossfade
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_crossfade_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_crossfade_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    val seconds = crossfadeSliderValue.roundToInt()
                    Text(
                        text = if (seconds == 0) stringResource(R.string.settings_crossfade_disabled) else stringResource(R.string.settings_crossfade_seconds, seconds),
                        color = if (seconds > 0) accent.primary else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = crossfadeSliderValue,
                    onValueChange = onCrossfadeChange,
                    onValueChangeFinished = onCrossfadeChangeFinished,
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.primary,
                        activeTrackColor = accent.primary,
                        inactiveTrackColor = Color(0xFF1E222B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.settings_crossfade_hint),
                    color = TextDisabled,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // 4. Autoplay on Launch
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_autoplay_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_autoplay_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isAutoplayOnStart,
                    onCheckedChange = onToggleAutoplayOnStart,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }
    }
}

/**
 * Aba 2: Letras & Sincronização
 */
@Composable
private fun LyricsSettingsPanel(
    isShowLyrics: Boolean,
    currentLyricsOffsetMs: Int,
    onToggleShowLyrics: (Boolean) -> Unit,
    onSelectLyricsOffset: (Int) -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Letras & Sincronia",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Exibição de letras .LRC e calibração de timing para áudio veicular.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Switch Exibir Letras
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_lyrics_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_lyrics_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isShowLyrics,
                    onCheckedChange = onToggleShowLyrics,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 2. Ajuste Fino de Sincronia / Latência (Offset em ms)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_lyrics_sync_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_lyrics_sync_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    val offsetText = if (currentLyricsOffsetMs > 0) "+$currentLyricsOffsetMs ms" else "$currentLyricsOffsetMs ms"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF101215),
                        border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = offsetText,
                            color = accent.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Botões de Calibração Táteis Veiculares
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(currentLyricsOffsetMs - 100) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "-100 ms (Adiantar)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(0) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "0 ms (Zerar)",
                            color = if (currentLyricsOffsetMs == 0) accent.primary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLyricsOffset(currentLyricsOffsetMs + 100) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text(
                            text = "+100 ms (Atrasar)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // 3. Informações sobre Arquivos LRC
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Suporte a Arquivos de Letras (.LRC)",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_lyrics_lrc_info),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Aba 3: Aparência & Sistema
 */
@Composable
private fun AppearanceSettingsPanel(
    currentAccentTheme: ClusterAccent,
    isKeepScreenOn: Boolean,
    isFullScreen: Boolean,
    onSelectAccent: (String) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleFullScreen: (Boolean) -> Unit,
    onOpenWebsite: () -> Unit
) {
    val accent = LocalClusterAccent.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Aparência & Interface",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Personalize as cores de iluminação dos ponteiros e a exibição de tela.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // 1. Tonalidade de Iluminação (Temas de Cores com contraste corrigido)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_accent_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_accent_subtitle),
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Fileira de badges circulares horizontais
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ALL_ACCENT_THEMES.forEach { theme ->
                        val isSelected = theme.id.equals(currentAccentTheme.id, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccent(theme.id) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
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
                                        tint = theme.onAccent, // Correção do contraste no Pure Silver!
                                        modifier = Modifier.size(22.dp)
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

        // 2. Manter Tela Ligada
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_screen_on_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_screen_on_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isKeepScreenOn,
                    onCheckedChange = onToggleKeepScreenOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 3. Modo Tela Cheia
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_fullscreen_title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_fullscreen_subtitle),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isFullScreen,
                    onCheckedChange = onToggleFullScreen,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = MetallicIntermediate,
                        uncheckedBorderColor = SurfaceCardBorder
                    )
                )
            }
        }

        // 4. Sobre o Cluster Player
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_about_app),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.settings_about_desc),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenWebsite)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.desc_visit_website),
                        tint = accent.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_visit_website),
                        color = accent.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
