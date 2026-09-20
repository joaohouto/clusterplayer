package com.joaohouto.clusterplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = PlayerController.getInstance(application)
    private val repository = MusicRepository.getInstance(application)

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState
    val currentPosition: StateFlow<Long> = playerController.currentPosition

    // Automatically and reactively pre-loads the tracks of the currently playing folder on Dispatchers.IO
    // so that opening the queue dialog is completely instant and has 0 wait time.
    val currentFolderTracks: StateFlow<List<Track>> = playbackState
        .map { it.currentTrack?.folderPath }
        .distinctUntilChanged()
        .flatMapLatest { folderPath ->
            if (folderPath.isNullOrEmpty()) {
                flowOf(emptyList())
            } else {
                repository.getTracksForFolder(folderPath)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        playerController.initialize()
    }

    fun loadTracksForCurrentFolder() {
        // Pre-loaded reactively by currentFolderTracks
    }

    fun playTrackInCurrentFolder(trackIndex: Int) {
        val tracks = currentFolderTracks.value
        val track = tracks.getOrNull(trackIndex)
        if (track != null && track.path.isNotEmpty() && !File(track.path).exists()) {
            playerController.showCannotPlayToast()
            return
        }
        val folderPath = playbackState.value.currentTrack?.folderPath
        if (!folderPath.isNullOrEmpty() && playerController.hasMediaItemsForFolder(folderPath)) {
            // Fast path: seek directly within current ExoPlayer playlist (0ms latency, no DB query or MediaItem recreation)
            playerController.playTrackAtIndex(trackIndex)
        } else if (!folderPath.isNullOrEmpty()) {
            playerController.playFolder(folderPath, trackIndex)
        }
    }

    fun playPause() {
        playerController.playPause()
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }
}
