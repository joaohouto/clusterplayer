package com.joaohouto.clusterplayer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import com.joaohouto.clusterplayer.R
import com.joaohouto.clusterplayer.data.model.Folder
import com.joaohouto.clusterplayer.ui.components.MetallicButton
import com.joaohouto.clusterplayer.ui.components.MetallicButtonStyle
import com.joaohouto.clusterplayer.ui.components.SquareAlbumArt
import com.joaohouto.clusterplayer.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterplayer.ui.theme.MetallicBorder
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.NeedleRed
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterplayer.ui.theme.TextPrimary
import com.joaohouto.clusterplayer.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val folderTracks by viewModel.folderTracks.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepMetallicBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar com margens adequadas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.header_folders_playlists),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (isScanning) {
                        Spacer(modifier = Modifier.width(16.dp))
                        CircularProgressIndicator(
                            color = NeedleRed,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.status_scanning),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                MetallicButton(
                    onClick = { viewModel.rescan() },
                    icon = Icons.Rounded.Refresh,
                    text = stringResource(R.string.btn_refresh),
                    minSize = 54.dp,
                    contentDescription = stringResource(R.string.desc_refresh)
                )
            }

            // Folders Grid (otimizada para landscape / ultrawide)
            if (folders.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.empty_folders_title),
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_folders_subtitle),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = if (playbackState.currentTrack != null) 116.dp else 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(folders, key = { it.path }, contentType = { "folder_card" }) { folder ->
                        FolderCard(
                            folder = folder,
                            onFolderClick = { viewModel.selectFolder(folder) },
                            onPlayAllClick = {
                                viewModel.playFolder(folder.path)
                                onNavigateToPlayer()
                            }
                        )
                    }
                }
            }
        }

        // Barra inferior de reprodução com TODOS os controles
        if (playbackState.currentTrack != null) {
            FullControlsBottomPlaybackBar(
                track = playbackState.currentTrack!!,
                isPlaying = playbackState.isPlaying,
                isShuffleEnabled = playbackState.isShuffleEnabled,
                repeatMode = playbackState.repeatMode,
                onPlayPause = { viewModel.playPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onBarClick = onNavigateToPlayer,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Selected Folder Track List Dialog
        if (selectedFolder != null) {
            FolderTracksDialog(
                folder = selectedFolder!!,
                tracks = folderTracks,
                onPlayFolder = {
                    viewModel.playFolder(selectedFolder!!.path)
                    viewModel.selectFolder(null)
                    onNavigateToPlayer()
                },
                onPlayTrack = { index ->
                    viewModel.playTrackInFolder(selectedFolder!!.path, index)
                    viewModel.selectFolder(null)
                    onNavigateToPlayer()
                },
                onDismiss = { viewModel.selectFolder(null) }
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
    onFolderClick: () -> Unit,
    onPlayAllClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onFolderClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MetallicIntermediate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = NeedleRed,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.tracks_count, folder.trackCount),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Direct "Reproduzir Todas" button
            MetallicButton(
                onClick = onPlayAllClick,
                style = MetallicButtonStyle.Accent,
                icon = Icons.Rounded.PlayArrow,
                minSize = 56.dp,
                contentDescription = stringResource(R.string.btn_play_all)
            )
        }
    }
}

@Composable
private fun FullControlsBottomPlaybackBar(
    track: com.joaohouto.clusterplayer.data.model.Track,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lado Esquerdo: Capa e Metadados (clicável para abrir o Player)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBarClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareAlbumArt(
                    uriOrPath = track.path.ifEmpty { track.uri },
                    modifier = Modifier.size(66.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Lado Direito: Todos os controles de reprodução
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (Aleatório)
                MetallicButton(
                    onClick = onToggleShuffle,
                    icon = Icons.Rounded.Shuffle,
                    isActive = isShuffleEnabled,
                    minSize = 48.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_shuffle)
                )

                // Anterior (<)
                MetallicButton(
                    onClick = onPrevious,
                    icon = Icons.Rounded.SkipPrevious,
                    minSize = 48.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_previous)
                )

                // Play / Pause (|| / ▶) Centralizado em Destaque
                MetallicButton(
                    onClick = onPlayPause,
                    style = MetallicButtonStyle.Accent,
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    minSize = 54.dp,
                    iconSize = 32.dp,
                    contentDescription = if (isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play)
                )

                // Próximo (>)
                MetallicButton(
                    onClick = onNext,
                    icon = Icons.Rounded.SkipNext,
                    minSize = 48.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_next)
                )

                // Repetir
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                MetallicButton(
                    onClick = onCycleRepeatMode,
                    icon = repeatIcon,
                    isActive = isRepeatActive,
                    minSize = 48.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_repeat)
                )
            }
        }
    }
}

@Composable
private fun FolderTracksDialog(
    folder: Folder,
    tracks: List<com.joaohouto.clusterplayer.data.model.Track>,
    onPlayFolder: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val itemShape = remember { RoundedCornerShape(8.dp) }
    val itemBorder = remember { BorderStroke(1.dp, SurfaceCardBorder) }

    Dialog(onDismissRequest = onDismiss) {
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
                            text = folder.name,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.tracks_count, folder.trackCount),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetallicButton(
                            onClick = onPlayFolder,
                            style = MetallicButtonStyle.Accent,
                            icon = Icons.Rounded.PlayArrow,
                            text = stringResource(R.string.btn_play_all),
                            minSize = 56.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        MetallicButton(
                            onClick = onDismiss,
                            icon = Icons.Rounded.Close,
                            minSize = 56.dp,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { index, track ->
                            if (track.id != 0L) track.id else "${track.uri}_$index"
                        },
                        contentType = { _, _ -> "track_item" }
                    ) { index, track ->
                        TrackListItem(
                            index = index,
                            track = track,
                            shape = itemShape,
                            border = itemBorder,
                            onClick = { onPlayTrack(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListItem(
    index: Int,
    track: com.joaohouto.clusterplayer.data.model.Track,
    shape: RoundedCornerShape,
    border: BorderStroke,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedIndex = remember(index) { String.format(Locale.getDefault(), "%02d", index + 1) }
    val formattedDuration = remember(track.durationMs) { formatDuration(track.durationMs) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeepMetallicBackground)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedIndex,
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formattedDuration,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
