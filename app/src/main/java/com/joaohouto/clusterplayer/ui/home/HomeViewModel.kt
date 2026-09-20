package com.joaohouto.clusterplayer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterplayer.data.model.Folder
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import com.joaohouto.clusterplayer.data.model.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BottomBarUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playerController = PlayerController.getInstance(application)

    val isScanning: StateFlow<Boolean> = repository.isScanning

    val folders: StateFlow<List<Folder>> = repository.allFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState

    val bottomBarState: StateFlow<BottomBarUiState> = playerController.uiState
        .map {
            BottomBarUiState(
                currentTrack = it.currentTrack,
                isPlaying = it.isPlaying,
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = it.repeatMode
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BottomBarUiState()
        )

    init {
        playerController.initialize()
        // Run initial scan if needed
        repository.triggerScan()
    }

    fun playFolder(folderPath: String) {
        playerController.playFolder(folderPath)
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

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun rescan(clearOld: Boolean = true) {
        repository.triggerScan(clearOld = clearOld)
    }
}
