package com.joaohouto.clusterplayer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterplayer.data.model.Folder
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackUiState
import com.joaohouto.clusterplayer.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // State for viewing tracks of a selected folder
    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _folderTracks = MutableStateFlow<List<Track>>(emptyList())
    val folderTracks: StateFlow<List<Track>> = _folderTracks.asStateFlow()

    init {
        playerController.initialize()
        // Run initial scan if needed
        repository.triggerScan()
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        if (folder != null) {
            viewModelScope.launch {
                _folderTracks.value = repository.getTracksForFolderSync(folder.path)
            }
        } else {
            _folderTracks.value = emptyList()
        }
    }

    fun playFolder(folderPath: String) {
        playerController.playFolder(folderPath)
    }

    fun playTrackInFolder(folderPath: String, trackIndex: Int) {
        playerController.playFolder(folderPath, trackIndex)
    }

    fun playPause() {
        playerController.playPause()
    }

    fun rescan() {
        repository.triggerScan()
    }
}
