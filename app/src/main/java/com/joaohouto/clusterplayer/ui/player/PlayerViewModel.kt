package com.joaohouto.clusterplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = PlayerController.getInstance(application)

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState

    init {
        playerController.initialize()
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
