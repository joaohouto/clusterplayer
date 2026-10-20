package com.joaohouto.clusterplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.SurfaceCardBorder
import java.io.File
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.joaohouto.clusterplayer.R
import com.joaohouto.clusterplayer.ui.components.MetallicButton
import com.joaohouto.clusterplayer.ui.components.MetallicButtonStyle
import com.joaohouto.clusterplayer.ui.components.ProgressBarSlider
import com.joaohouto.clusterplayer.ui.components.SquareAlbumArt
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterplayer.ui.theme.LocalClusterAccent
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    showLyrics: Boolean = true,
    onNavigateToHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()
    val currentTrack = state.currentTrack
    val folderTracks by viewModel.currentFolderTracks.collectAsState()
    var showTracksDialog by remember { mutableStateOf(false) }

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
        // Linha Superior: Fileira de botões no topo (Selecionar pasta à esquerda; Fila e Ajustes à direita)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lado Esquerdo: Navegação de Pastas ("Selecionar pasta")
            MetallicButton(
                onClick = onNavigateToHome,
                icon = Icons.Rounded.Folder,
                text = stringResource(R.string.header_folders_playlists),
                minSize = 72.dp,
                iconSize = 32.dp,
                textSize = 16.sp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                contentDescription = stringResource(R.string.desc_back)
            )

            // Lado Direito: Fila de Reprodução e Configurações
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fila de reprodução (Músicas da pasta atual)
                MetallicButton(
                    onClick = { showTracksDialog = true },
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    text = stringResource(R.string.btn_folder_tracks),
                    minSize = 72.dp,
                    iconSize = 32.dp,
                    textSize = 16.sp,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    contentDescription = stringResource(R.string.desc_folder_tracks)
                )

                // Botão de Configurações
                MetallicButton(
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.Settings,
                    minSize = 72.dp,
                    iconSize = 32.dp,
                    contentDescription = stringResource(R.string.desc_settings)
                )
            }
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
                // Espaço FIXO para letras sincronizadas (.LRC) - Posicionado ACIMA do título
                // Garante que o título, artista e barra de progresso nunca se movam
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val lyricsLine = state.currentLyricsLine
                    val accentColor = LocalClusterAccent.current.primary
                    AnimatedContent(
                        targetState = if (showLyrics) lyricsLine else null,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                        },
                        label = "LyricsTransition"
                    ) { activeText ->
                        if (!activeText.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeText,
                                    color = accentColor,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Nome da faixa (Título com tamanho aumentado)
                Text(
                    text = currentTrack?.title ?: stringResource(R.string.no_track_playing),
                    color = TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artista / Álbum (com tamanho aumentado)
                val unknownAlbumStr = stringResource(R.string.unknown_album)
                val artistAlbumText = buildString {
                    append(currentTrack?.artist ?: stringResource(  R.string.select_folder_to_play))
                    if (!currentTrack?.album.isNullOrBlank() &&
                        currentTrack?.album != unknownAlbumStr &&
                        currentTrack?.album != "Álbum Desconhecido" &&
                        currentTrack?.album != "Unknown Album"
                    ) {
                        append(" • ")
                        append(currentTrack?.album)
                    }
                }
                Text(
                    text = artistAlbumText,
                    color = TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Barra de Progresso com StateFlow de posição isolado e projeção fluida
                TrackProgressSection(
                    viewModel = viewModel,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    trackKey = currentTrack?.uri
                )
            }
        }

        // Espaçamento entre a área central e os botões inferiores de controle
        Spacer(modifier = Modifier.height(12.dp))

        // ÚLTIMA LINHA DO LAYOUT: Linha exclusiva com os botões de controle de reprodução com altura ampliada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 6. Diminuir Volume (-) nas colunas finais
            MetallicButton(
                onClick = { viewModel.volumeDown() },
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                minSize = 64.dp,
                iconSize = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_volume_down)
            )

            // 1. Shuffle (Aleatório)
            MetallicButton(
                onClick = { viewModel.toggleShuffle() },
                icon = Icons.Rounded.Shuffle,
                isActive = state.isShuffleEnabled,
                minSize = 64.dp,
                iconSize = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_shuffle)
            )

            // 3. Anterior (<)
            MetallicButton(
                onClick = { viewModel.previous() },
                icon = Icons.Rounded.SkipPrevious,
                minSize = 64.dp,
                iconSize = 40.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_previous)
            )

            // 4. Play / Pause (|| / ▶) Centralizado em Destaque
            MetallicButton(
                onClick = { viewModel.playPause() },
                style = MetallicButtonStyle.Accent,
                icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                minSize = 78.dp,
                iconSize = 48.dp,
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight(),
                contentDescription = if (state.isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play)
            )

            // 5. Próximo (>)
            MetallicButton(
                onClick = { viewModel.next() },
                icon = Icons.Rounded.SkipNext,
                minSize = 64.dp,
                iconSize = 40.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_next)
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
                minSize = 64.dp,
                iconSize = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_repeat)
            )

            // 7. Aumentar Volume (+) nas colunas finais
            MetallicButton(
                onClick = { viewModel.volumeUp() },
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                minSize = 64.dp,
                iconSize = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentDescription = stringResource(R.string.desc_volume_up)
            )
        }
    }

    // Estabilização de lambdas para que o FolderTracksDialog não recomponha a cada tick (200ms) de posição
    val onSelectTrackLambda: (Int) -> Unit = remember(viewModel) {
        { index ->
            viewModel.playTrackInCurrentFolder(index)
            showTracksDialog = false
        }
    }
    val onDismissDialogLambda: () -> Unit = remember {
        { showTracksDialog = false }
    }

    // Diálogo com as músicas da pasta atual
    if (showTracksDialog) {
        val folderPath = currentTrack?.folderPath ?: ""
        val folderName = if (folderPath.isNotEmpty()) {
            File(folderPath).name.ifEmpty { stringResource(R.string.current_folder_tracks) }
        } else {
            stringResource(R.string.current_folder_tracks)
        }

        FolderTracksDialog(
            folderName = folderName,
            tracks = folderTracks,
            currentTrackUri = currentTrack?.uri,
            onSelectTrack = onSelectTrackLambda,
            onDismiss = onDismissDialogLambda
        )
    }
}

@Composable
private fun FolderTracksDialog(
    folderName: String,
    tracks: List<com.joaohouto.clusterplayer.data.model.Track>,
    currentTrackUri: String?,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val itemShape = remember { RoundedCornerShape(8.dp) }
    val itemBorder = remember { BorderStroke(1.dp, SurfaceCardBorder) }
    val activeBorder = remember(accent) { BorderStroke(1.dp, accent) }

    // Rola instantaneamente a lista para posicionar na música atual que está tocando
    val currentTrackIndex = remember(tracks, currentTrackUri) {
        tracks.indexOfFirst { it.uri == currentTrackUri }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentTrackIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.tracks_count, tracks.size),
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }

                    MetallicButton(
                        onClick = onDismiss,
                        icon = Icons.Rounded.Close,
                        minSize = 52.dp,
                        contentDescription = stringResource(R.string.btn_close)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = tracks.size,
                        key = { index ->
                            val track = tracks[index]
                            if (track.id != 0L) track.id else track.uri
                        },
                        contentType = { "track_item" }
                    ) { index ->
                        val track = tracks[index]
                        TrackQueueItem(
                            index = index,
                            track = track,
                            isCurrent = track.uri == currentTrackUri,
                            itemShape = itemShape,
                            itemBorder = itemBorder,
                            activeBorder = activeBorder,
                            onClick = { onSelectTrack(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackQueueItem(
    index: Int,
    track: com.joaohouto.clusterplayer.data.model.Track,
    isCurrent: Boolean,
    itemShape: Shape,
    itemBorder: BorderStroke,
    activeBorder: BorderStroke,
    onClick: () -> Unit
) {
    val formattedIndex = remember(index) { formatTrackIndex(index) }
    val formattedDuration = remember(track.durationMs) { formatTrackDuration(track.durationMs) }

    val accent = LocalClusterAccent.current.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(if (isCurrent) SurfaceCard else DeepMetallicBackground)
            .border(if (isCurrent) activeBorder else itemBorder, itemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedIndex,
            color = if (isCurrent) accent else TextSecondary,
            fontSize = 16.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.width(36.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) accent else TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formattedDuration,
            color = if (isCurrent) accent else TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTrackIndex(index: Int): String {
    val num = index + 1
    return if (num < 10) "0$num" else num.toString()
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minStr = if (minutes < 10) "0$minutes" else minutes.toString()
    val secStr = if (seconds < 10) "0$seconds" else seconds.toString()
    return "$minStr:$secStr"
}

@Composable
private fun TrackProgressSection(
    viewModel: PlayerViewModel,
    durationMs: Long,
    isPlaying: Boolean,
    trackKey: String? = null,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.currentPosition.collectAsState()
    ProgressBarSlider(
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        trackKey = trackKey,
        onSeek = { targetMs -> viewModel.seekTo(targetMs) },
        modifier = modifier
    )
}

