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
import androidx.compose.foundation.layout.heightIn
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

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()
    val currentTrack = state.currentTrack

    var dragAmountX by remember { mutableFloatStateOf(0f) }

    Column(
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Linha Superior: Logo/Título e Botão de navegação "Playlists / Pastas"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLUSTER PLAYER",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            MetallicButton(
                onClick = onNavigateToHome,
                icon = Icons.Rounded.Folder,
                text = "Playlists / Pastas",
                minSize = 48.dp,
                contentDescription = "Voltar para Pastas"
            )
        }

        // Linha Central: Capa de Álbum (Tamanho Médio flexível até 200dp) + Metadados e Barra de Progresso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa quadrada com altura adaptativa até 200dp (nunca estoura a tela)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .heightIn(max = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                SquareAlbumArt(
                    uriOrPath = currentTrack?.path?.ifEmpty { currentTrack.uri },
                    modifier = Modifier.fillMaxSize(),
                    onDoubleTap = { viewModel.playPause() }
                )
            }

            // Metadados, Letra LRC e Barra de Progresso
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Nome da faixa
                Text(
                    text = currentTrack?.title ?: "Nenhuma música em reprodução",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Artista / Álbum
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
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Linha sincronizada de letras (.LRC) se presente
                if (!state.currentLyricsLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Progresso Vermelha
                ProgressBarSlider(
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                )
            }
        }

        // ÚLTIMA LINHA DO LAYOUT: Linha exclusiva com os botões de controle de reprodução RESPONSIVOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle (Aleatório)
            MetallicButton(
                onClick = { viewModel.toggleShuffle() },
                icon = Icons.Rounded.Shuffle,
                isActive = state.isShuffleEnabled,
                minSize = 48.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = "Modo Aleatório"
            )

            // Anterior (<)
            MetallicButton(
                onClick = { viewModel.previous() },
                icon = Icons.Rounded.SkipPrevious,
                minSize = 48.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = "Faixa Anterior"
            )

            // Play / Pause (|| / ▶) Centralizado em Destaque
            MetallicButton(
                onClick = { viewModel.playPause() },
                style = MetallicButtonStyle.Accent,
                icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                minSize = 52.dp,
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight(),
                contentDescription = if (state.isPlaying) "Pausar" else "Reproduzir"
            )

            // Próximo (>)
            MetallicButton(
                onClick = { viewModel.next() },
                icon = Icons.Rounded.SkipNext,
                minSize = 48.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = "Próxima Faixa"
            )

            // Repetir
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
                minSize = 48.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = "Modo Repetir"
            )
        }
    }
}
