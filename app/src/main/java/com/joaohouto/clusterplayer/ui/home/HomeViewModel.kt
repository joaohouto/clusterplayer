package com.joaohouto.clusterplayer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterplayer.data.model.Folder
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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

    fun rescan() {
        repository.triggerScan()
    }
}
