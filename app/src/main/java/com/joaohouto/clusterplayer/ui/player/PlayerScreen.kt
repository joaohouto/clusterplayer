package com.joaohouto.clusterplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.joaohouto.clusterplayer.ui.components.MetallicButton
import com.joaohouto.clusterplayer.ui.components.MetallicButtonStyle
import com.joaohouto.clusterplayer.ui.components.ProgressBarSlider
import com.joaohouto.clusterplayer.ui.components.SquareAlbumArt
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterplayer.ui.theme.NeedleRed
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()
    val currentTrack = state.currentTrack

    var dragAmountX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepMetallicBackground)
            // Gesture: swipe horizontally to skip or go to previous track
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAmountX = 0f },
                    onDragEnd = {
                        val threshold = 120f
                        if (dragAmountX < -threshold) {
                            viewModel.next()
                        } else if (dragAmountX > threshold) {
                            viewModel.previous()
                        }
                        dragAmountX = 0f
                    },
                    onDragCancel = { dragAmountX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAmountX += dragAmount
                    }
                )
            }
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Lado Esquerdo: Capa da música quadrada (1:1, cantos 8dp, sem aros metálicos circulares)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                SquareAlbumArt(
                    uriOrPath = currentTrack?.path?.ifEmpty { currentTrack.uri },
                    modifier = Modifier.fillMaxSize(),
                    onDoubleTap = { viewModel.playPause() }
                )
            }

            // Lado Direito: Topo (Navegação), Centro (Metadados e Progresso), Base (Controles)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Lado Direito Superior: Botão Playlists / Pastas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetallicButton(
                        onClick = onNavigateToHome,
                        icon = Icons.Rounded.Folder,
                        text = "Playlists / Pastas",
                        minSize = 60.dp,
                        contentDescription = "Voltar para Pastas"
                    )
                }

                // Lado Direito Central: Metadados, Letra e Barra de Progresso
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Nome da faixa (destaque 22-24sp, 1-2 linhas, elipse)
                    Text(
                        text = currentTrack?.title ?: "Nenhuma música em reprodução",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artista / Álbum (16sp, #9A9DA6)
                    val artistAlbumText = buildString {
                        append(currentTrack?.artist ?: "Selecione uma pasta para tocar")
                        if (!currentTrack?.album.isNullOrBlank() && currentTrack?.album != "Álbum Desconhecido") {
                            append(" • ")
                            append(currentTrack?.album)
                        }
                    }
                    Text(
                        text = artistAlbumText,
                        color = TextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Linha sincronizada de letras (.LRC) se presente
                    if (!state.currentLyricsLine.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = NeedleRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.currentLyricsLine!!,
                                color = NeedleRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Barra de Progresso Vermelha
                    ProgressBarSlider(
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                    )
                }

                // Base: Controles de Reprodução (Mínimo 60x60 dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle (Aleatório)
                    MetallicButton(
                        onClick = { viewModel.toggleShuffle() },
                        icon = Icons.Rounded.Shuffle,
                        isActive = state.isShuffleEnabled,
                        minSize = 60.dp,
                        contentDescription = "Modo Aleatório"
                    )

                    // Anterior (<) (Muda para anterior ou reinicia se > 3s)
                    MetallicButton(
                        onClick = { viewModel.previous() },
                        icon = Icons.Rounded.SkipPrevious,
                        minSize = 60.dp,
                        contentDescription = "Faixa Anterior"
                    )

                    // Play / Pause (|| / ▶) Centralizado em Destaque
                    MetallicButton(
                        onClick = { viewModel.playPause() },
                        style = MetallicButtonStyle.Accent,
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        minSize = 68.dp,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproduzir"
                    )

                    // Próximo (>)
                    MetallicButton(
                        onClick = { viewModel.next() },
                        icon = Icons.Rounded.SkipNext,
                        minSize = 60.dp,
                        contentDescription = "Próxima Faixa"
                    )

                    // Repetir (Desativado, Repetir Todas, Repetir Uma)
                    val isRepeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
                    val repeatIcon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    }
                    MetallicButton(
                        onClick = { viewModel.cycleRepeatMode() },
                        icon = repeatIcon,
                        isActive = isRepeatActive,
                        minSize = 60.dp,
                        contentDescription = "Modo Repetir"
                    )
                }
            }
        }
    }
}
