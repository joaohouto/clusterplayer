package com.joaohouto.clusterplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.lyrics.LrcLine
import com.joaohouto.clusterplayer.lyrics.LrcParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val currentLyricsLine: String? = null,
    val isConnected: Boolean = false
)

class PlayerController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlayerController"

        @Volatile
        private var INSTANCE: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                val instance = PlayerController(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var positionTickerJob: Job? = null
    private var lyricsLines: List<LrcLine>? = null

    private val repository = MusicRepository.getInstance(context)

    fun initialize() {
        if (mediaController != null || controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
                _uiState.value = _uiState.value.copy(isConnected = true)
                updateStateFromController()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateStateFromController()
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStateFromController()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadCurrentTrackMetadata(mediaItem)
                updateStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(isShuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }
        })

        if (controller.isPlaying) {
            startPositionTicker()
        }
        loadCurrentTrackMetadata(controller.currentMediaItem)
    }

    private fun loadCurrentTrackMetadata(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _uiState.value = _uiState.value.copy(currentTrack = null, currentLyricsLine = null)
            lyricsLines = null
            return
        }

        val mediaId = mediaItem.mediaId
        val extras = mediaItem.mediaMetadata.extras
        val filePath = extras?.getString("filePath") ?: ""
        val folderPath = extras?.getString("folderPath") ?: ""
        val title = mediaItem.mediaMetadata.title?.toString() ?: "Desconhecido"
        val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Artista Desconhecido"
        val album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Álbum Desconhecido"
        val durationMs = extras?.getLong("durationMs") ?: 0L

        val track = Track(
            id = 0,
            uri = mediaId,
            path = filePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            folderPath = folderPath
        )

        _uiState.value = _uiState.value.copy(
            currentTrack = track,
            currentLyricsLine = null
        )

        // Load lyrics asynchronously if .lrc exists
        controllerScope.launch {
            if (filePath.isNotEmpty()) {
                lyricsLines = LrcParser.findAndParseLrc(filePath)
                updateLyricsLine(_uiState.value.currentPositionMs)
            } else {
                lyricsLines = null
            }
        }
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        val duration = if (controller.duration > 0) controller.duration else _uiState.value.currentTrack?.durationMs ?: 0L
        val currentPos = controller.currentPosition.coerceAtLeast(0L)

        _uiState.value = _uiState.value.copy(
            isPlaying = controller.isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            isShuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode
        )
        updateLyricsLine(currentPos)
    }

    private fun updateLyricsLine(positionMs: Long) {
        val lines = lyricsLines
        if (!lines.isNullOrEmpty()) {
            val active = LrcParser.getActiveLine(lines, positionMs)
            if (active != _uiState.value.currentLyricsLine) {
                _uiState.value = _uiState.value.copy(currentLyricsLine = active)
            }
        } else if (_uiState.value.currentLyricsLine != null) {
            _uiState.value = _uiState.value.copy(currentLyricsLine = null)
        }
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        positionTickerJob = controllerScope.launch {
            while (isActive) {
                delay(250)
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = if (controller.duration > 0) controller.duration else _uiState.value.durationMs
                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                    updateLyricsLine(pos)
                }
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun next() {
        mediaController?.seekToNextMediaItem()
    }

    fun previous() {
        val controller = mediaController ?: return
        // Rule: restart current if > 3s, else previous track
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
        updateLyricsLine(positionMs)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val nextMode = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = nextMode
        _uiState.value = _uiState.value.copy(isShuffleEnabled = nextMode)
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _uiState.value = _uiState.value.copy(repeatMode = nextMode)
    }

    fun playTrackAtIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            if (!controller.isPlaying) {
                controller.play()
            }
        }
    }

    fun hasMediaItemsForFolder(folderPath: String): Boolean {
        val controller = mediaController ?: return false
        if (controller.mediaItemCount == 0) return false
        val currentFolder = _uiState.value.currentTrack?.folderPath
        return currentFolder == folderPath
    }

    fun playFolder(folderPath: String, startIndex: Int = 0) {
        controllerScope.launch {
            val tracks = repository.getTracksForFolderSync(folderPath)
            if (tracks.isNotEmpty()) {
                playTracks(tracks, startIndex)
            }
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        val mediaItems = tracks.map { track ->
            val extras = Bundle().apply {
                putString("folderPath", track.folderPath)
                putString("filePath", track.path)
                putLong("durationMs", track.durationMs)
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(Uri.parse(track.uri))
                .setExtras(extras)
                .build()

            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.size - 1), 0L)
        controller.prepare()
        controller.play()
    }

    fun stop() {
        try {
            stopPositionTicker()
            mediaController?.stop()
            mediaController?.clearMediaItems()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player", e)
        }
    }

    fun release() {
        stopPositionTicker()
        mediaController?.release()
        mediaController = null
        controllerFuture = null
    }
}
