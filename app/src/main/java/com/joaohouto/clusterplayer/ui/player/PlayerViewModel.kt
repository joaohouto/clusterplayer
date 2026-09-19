package com.joaohouto.clusterplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = PlayerController.getInstance(application)
    private val repository = MusicRepository.getInstance(application)

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState

    private val _currentFolderTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentFolderTracks: StateFlow<List<Track>> = _currentFolderTracks.asStateFlow()

    init {
        playerController.initialize()
    }

    fun loadTracksForCurrentFolder() {
        val folderPath = playbackState.value.currentTrack?.folderPath
        if (!folderPath.isNullOrEmpty()) {
            viewModelScope.launch {
                _currentFolderTracks.value = repository.getTracksForFolderSync(folderPath)
            }
        }
    }

    fun playTrackInCurrentFolder(trackIndex: Int) {
        val folderPath = playbackState.value.currentTrack?.folderPath
        if (!folderPath.isNullOrEmpty()) {
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
