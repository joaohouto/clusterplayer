package com.joaohouto.clusterplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.joaohouto.clusterplayer.R
import com.joaohouto.clusterplayer.data.model.Track
import java.io.File
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.lyrics.LrcLine
import com.joaohouto.clusterplayer.lyrics.LrcParser
import com.joaohouto.clusterplayer.ui.components.AudioArtExtractor
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

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

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

    private var lastToastTime = 0L

    fun showCannotPlayToast() {
        val now = System.currentTimeMillis()
        if (now - lastToastTime < 1500L) return
        lastToastTime = now
        controllerScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.error_cannot_play),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onDirectTrackTransition(mediaItem: MediaItem?) {
        controllerScope.launch(Dispatchers.Main) {
            loadCurrentTrackMetadata(mediaItem)
            _currentPosition.value = 0L
            updateStateFromController()
        }
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )
                ) {
                    val currentItem = player.currentMediaItem
                    if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                        loadCurrentTrackMetadata(currentItem)
                        _currentPosition.value = 0L
                    }
                    updateStateFromController()
                }
            }

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

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error encountered in PlayerController: ${error.errorCodeName} (${error.errorCode})", error)
                showCannotPlayToast()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadCurrentTrackMetadata(mediaItem)
                _currentPosition.value = 0L
                updateStateFromController()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val currentItem = controller.currentMediaItem
                if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                    loadCurrentTrackMetadata(currentItem)
                    _currentPosition.value = 0L
                }
                updateStateFromController()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
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
        var filePath = extras?.getString("filePath") ?: ""
        val folderPath = extras?.getString("folderPath") ?: ""
        var title = mediaItem.mediaMetadata.title?.toString() ?: ""
        var artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
        var album = mediaItem.mediaMetadata.albumTitle?.toString() ?: ""
        val durationMs = extras?.getLong("durationMs") ?: 0L

        if (filePath.isEmpty() && mediaId.isNotEmpty()) {
            if (mediaId.startsWith("file://")) {
                filePath = Uri.parse(mediaId).path ?: mediaId
            } else if (mediaId.startsWith("/")) {
                filePath = mediaId
            }
        }

        if (title.isBlank() || title == "Desconhecido") {
            title = if (filePath.isNotEmpty()) {
                File(filePath).nameWithoutExtension
            } else {
                "Desconhecido"
            }
        }
        if (artist.isBlank()) artist = "Artista Desconhecido"
        if (album.isBlank()) album = "Álbum Desconhecido"

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

        // Pré-carrega a capa do álbum diretamente no cache de memória para exibição em 0ms
        val artPath = if (filePath.isNotEmpty()) filePath else mediaId
        if (artPath.isNotEmpty()) {
            controllerScope.launch(Dispatchers.IO) {
                AudioArtExtractor.extractArtDirect(context, artPath)
            }
        }

        // Load lyrics asynchronously if .lrc exists
        controllerScope.launch {
            if (filePath.isNotEmpty()) {
                lyricsLines = LrcParser.findAndParseLrc(filePath)
                updateLyricsLine(_currentPosition.value)
            } else {
                lyricsLines = null
            }
        }
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        val playerDuration = if (controller.duration > 0) controller.duration else {
            PlaybackService.getMediaSessionInstance()?.player?.duration?.takeIf { it > 0 } ?: 0L
        }
        val duration = if (playerDuration > 0) playerDuration else _uiState.value.currentTrack?.durationMs ?: 0L
        val currentPos = controller.currentPosition.coerceAtLeast(0L)

        _currentPosition.value = currentPos
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
                delay(200)
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val currentItem = controller.currentMediaItem
                    // Se o item mudou (ex: transição gapless ou automática), sincroniza imediatamente os metadados!
                    if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                        loadCurrentTrackMetadata(currentItem)
                        _currentPosition.value = 0L
                        updateStateFromController()
                    } else {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        _currentPosition.value = pos

                        // Atualiza dinamicamente a duração precisa caso demuxer tenha finalizado
                        val pDuration = if (controller.duration > 0) controller.duration else {
                            PlaybackService.getMediaSessionInstance()?.player?.duration?.takeIf { it > 0 } ?: 0L
                        }
                        if (pDuration > 0 && pDuration != _uiState.value.durationMs) {
                            _uiState.value = _uiState.value.copy(durationMs = pDuration)
                        }

                        updateLyricsLine(pos)
                    }
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
        _currentPosition.value = positionMs
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
            val item = controller.getMediaItemAt(index)
            val filePath = item.mediaMetadata.extras?.getString("filePath") ?: ""
            if (filePath.isNotEmpty() && !File(filePath).exists()) {
                Log.w(TAG, "File no longer exists for track at index $index: $filePath")
                showCannotPlayToast()
                return
            }
            controller.seekToDefaultPosition(index)
            if (!controller.isPlaying) {
                controller.play()
            }
        } else {
            showCannotPlayToast()
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
            if (tracks.isEmpty()) {
                showCannotPlayToast()
                return@launch
            }

            val validTracks = tracks.filter { track ->
                track.uri.startsWith("content://") || track.path.isEmpty() || File(track.path).exists() || File(track.path).canRead()
            }

            if (validTracks.isEmpty()) {
                Log.w(TAG, "No valid/readable tracks found in folder: $folderPath")
                showCannotPlayToast()
                return@launch
            }

            val targetTrack = tracks.getOrNull(startIndex)
            val adjustedIndex = if (targetTrack != null && validTracks.contains(targetTrack)) {
                validTracks.indexOf(targetTrack)
            } else {
                0
            }

            playTracks(validTracks, adjustedIndex)
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (tracks.isEmpty()) {
            showCannotPlayToast()
            return
        }

        val validTracks = tracks.filter { track ->
            track.uri.startsWith("content://") || track.path.isEmpty() || File(track.path).exists() || File(track.path).canRead()
        }
        if (validTracks.isEmpty()) {
            showCannotPlayToast()
            return
        }

        val targetTrack = tracks.getOrNull(startIndex)
        val adjustedIndex = if (targetTrack != null && validTracks.contains(targetTrack)) {
            validTracks.indexOf(targetTrack)
        } else {
            0
        }

        val mediaItems = validTracks.map { track ->
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

        controller.setMediaItems(mediaItems, adjustedIndex.coerceIn(0, mediaItems.size - 1), 0L)
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
