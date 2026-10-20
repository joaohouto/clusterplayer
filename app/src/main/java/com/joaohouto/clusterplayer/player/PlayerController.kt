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
import com.joaohouto.clusterplayer.data.local.PreferencesDataStore
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private var lastSeekTimestamp = 0L
    private var lastSeekTargetMs = -1L
    private var anchorPositionMs = 0L
    private var anchorRealtimeMs = 0L
    private var lastDirectTransitionRealtimeMs = 0L

    private fun getActivePlayer(): Player? {
        return PlaybackService.getMediaSessionInstance()?.player ?: mediaController
    }

    private val repository = MusicRepository.getInstance(context)
    private val preferencesDataStore = PreferencesDataStore.getInstance(context)
    private var userLyricsOffsetMs: Long = 0L

    val lyricsOffsetMs: StateFlow<Int> = preferencesDataStore.lyricsOffsetMsFlow
        .stateIn(
            scope = controllerScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    init {
        controllerScope.launch {
            preferencesDataStore.lyricsOffsetMsFlow.collect { offset: Int ->
                userLyricsOffsetMs = offset.toLong()
                updateLyricsLine(_currentPosition.value)
            }
        }
    }

    fun setLyricsOffset(offsetMs: Int) {
        controllerScope.launch {
            preferencesDataStore.saveLyricsOffsetMs(offsetMs)
        }
    }

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

    fun onDirectTrackTransition(mediaItem: MediaItem?, transitionRealtimeMs: Long = android.os.SystemClock.elapsedRealtime()) {
        controllerScope.launch(Dispatchers.Main.immediate) {
            val now = android.os.SystemClock.elapsedRealtime()
            lastDirectTransitionRealtimeMs = now
            lastSeekTimestamp = 0L
            lastSeekTargetMs = -1L
            anchorPositionMs = 0L
            anchorRealtimeMs = now
            Log.d(
                TAG,
                "TrackTransition [Direct]: mediaId=${mediaItem?.mediaId}, title=${mediaItem?.mediaMetadata?.title}, dispatchDeltaMs=${now - transitionRealtimeMs}"
            )
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
                    val activePlayer = getActivePlayer() ?: player
                    val currentItem = activePlayer.currentMediaItem
                    if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                        loadCurrentTrackMetadata(currentItem)
                        anchorPositionMs = 0L
                        anchorRealtimeMs = android.os.SystemClock.elapsedRealtime()
                        _currentPosition.value = 0L
                    }
                    updateStateFromController()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateStateFromController()
                val activePlaying = getActivePlayer()?.isPlaying ?: isPlaying
                if (activePlaying) {
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
                val now = android.os.SystemClock.elapsedRealtime()
                val activeItem = getActivePlayer()?.currentMediaItem ?: mediaItem
                Log.d(
                    TAG,
                    "TrackTransition [Controller IPC]: ipcMediaId=${mediaItem?.mediaId}, activeMediaId=${activeItem?.mediaId}, reason=$reason, sinceDirectMs=${now - lastDirectTransitionRealtimeMs}"
                )
                lastSeekTimestamp = 0L
                lastSeekTargetMs = -1L
                if (activeItem?.mediaId != _uiState.value.currentTrack?.uri) {
                    loadCurrentTrackMetadata(activeItem)
                    anchorPositionMs = 0L
                    anchorRealtimeMs = now
                    _currentPosition.value = 0L
                }
                updateStateFromController()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val activePlayer = getActivePlayer() ?: controller
                val currentItem = activePlayer.currentMediaItem
                val now = android.os.SystemClock.elapsedRealtime()
                if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                    lastSeekTimestamp = 0L
                    lastSeekTargetMs = -1L
                    anchorPositionMs = 0L
                    anchorRealtimeMs = now
                    loadCurrentTrackMetadata(currentItem)
                    _currentPosition.value = 0L
                    updateStateFromController()
                } else {
                    val pos = (activePlayer.currentPosition.takeIf { it >= 0L } ?: newPosition.positionMs).coerceAtLeast(0L)
                    lastSeekTimestamp = 0L
                    lastSeekTargetMs = -1L
                    anchorPositionMs = pos
                    anchorRealtimeMs = now
                    _currentPosition.value = pos
                    val duration = if (activePlayer.duration > 0) activePlayer.duration else _uiState.value.durationMs
                    _uiState.value = _uiState.value.copy(
                        isPlaying = activePlayer.isPlaying,
                        currentPositionMs = pos,
                        durationMs = duration
                    )
                    updateLyricsLine(pos)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                val activeShuffle = getActivePlayer()?.shuffleModeEnabled ?: shuffleModeEnabled
                _uiState.value = _uiState.value.copy(isShuffleEnabled = activeShuffle)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val activeRepeat = getActivePlayer()?.repeatMode ?: repeatMode
                _uiState.value = _uiState.value.copy(repeatMode = activeRepeat)
            }
        })

        val initialPlayer = getActivePlayer() ?: controller
        if (initialPlayer.isPlaying) {
            startPositionTicker()
        }
        loadCurrentTrackMetadata(initialPlayer.currentMediaItem)
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
        lyricsLines = null

        // Pré-carrega a capa do álbum diretamente no cache de memória para exibição em 0ms
        val artPath = if (filePath.isNotEmpty()) filePath else mediaId
        if (artPath.isNotEmpty()) {
            controllerScope.launch(Dispatchers.IO) {
                AudioArtExtractor.extractArtDirect(context, artPath)
            }
        }

        // Load lyrics asynchronously if .lrc exists
        controllerScope.launch {
            var targetPath = filePath
            if (targetPath.isEmpty() && mediaId.isNotEmpty() && mediaId.startsWith("content://")) {
                val dbTrack = repository.getTrackByUri(mediaId)
                if (dbTrack != null && dbTrack.path.isNotEmpty()) {
                    targetPath = dbTrack.path
                }
            }

            val loadedLines = if (targetPath.isNotEmpty()) {
                LrcParser.findAndParseLrc(targetPath)
            } else {
                null
            }

            // Only apply if the track has not changed during async loading
            if (_uiState.value.currentTrack?.uri == mediaId) {
                lyricsLines = loadedLines
                updateLyricsLine(_currentPosition.value)
            }
        }
    }

    private fun getSanitizedPosition(controller: MediaController): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val isSeekingDebounced = (now - lastSeekTimestamp) < 800L
        val activePlayer = getActivePlayer() ?: controller
        val rawPos = activePlayer.currentPosition.coerceAtLeast(0L)
        val resolvedPos = if (isSeekingDebounced && lastSeekTargetMs >= 0L) {
            if (kotlin.math.abs(rawPos - lastSeekTargetMs) < 1500L) {
                lastSeekTimestamp = 0L
                lastSeekTargetMs = -1L
                rawPos
            } else {
                lastSeekTargetMs
            }
        } else {
            rawPos
        }
        anchorPositionMs = resolvedPos
        anchorRealtimeMs = now
        return resolvedPos
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        val activePlayer = getActivePlayer() ?: controller
        val playerDuration = activePlayer.duration.takeIf { it > 0 }
            ?: controller.duration.takeIf { it > 0 }
            ?: 0L
        val duration = if (playerDuration > 0) playerDuration else _uiState.value.currentTrack?.durationMs ?: 0L
        val currentPos = getSanitizedPosition(controller)

        _currentPosition.value = currentPos
        _uiState.value = _uiState.value.copy(
            isPlaying = activePlayer.isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            isShuffleEnabled = activePlayer.shuffleModeEnabled,
            repeatMode = activePlayer.repeatMode
        )
        updateLyricsLine(currentPos)
    }

    private fun updateLyricsLine(positionMs: Long) {
        val lines = lyricsLines
        if (!lines.isNullOrEmpty()) {
            val adjustedPosition = (positionMs + userLyricsOffsetMs).coerceAtLeast(0L)
            val active = LrcParser.getActiveLine(lines, adjustedPosition)
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
                delay(50) // Ressincronização de âncora e letras com o directPlayer
                val controller = mediaController ?: continue
                val activePlayer = getActivePlayer() ?: controller
                if (activePlayer.isPlaying) {
                    val currentItem = activePlayer.currentMediaItem
                    // Sempre consulta o directPlayer primeiro para nunca reverter a faixa por atraso IPC
                    if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        Log.d(
                            TAG,
                            "TrackTransition [Ticker Sync]: mediaId=${currentItem.mediaId}, sinceDirectMs=${now - lastDirectTransitionRealtimeMs}"
                        )
                        lastSeekTimestamp = 0L
                        lastSeekTargetMs = -1L
                        anchorPositionMs = 0L
                        anchorRealtimeMs = now
                        loadCurrentTrackMetadata(currentItem)
                        _currentPosition.value = 0L
                        updateStateFromController()
                    } else {
                        val pos = getSanitizedPosition(controller)
                        _currentPosition.value = pos

                        val pDuration = activePlayer.duration.takeIf { it > 0 }
                            ?: controller.duration.takeIf { it > 0 }
                            ?: 0L
                        if (pDuration > 0 && pDuration != _uiState.value.durationMs) {
                            _uiState.value = _uiState.value.copy(durationMs = pDuration, currentPositionMs = pos)
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
        lastSeekTimestamp = android.os.SystemClock.elapsedRealtime()
        lastSeekTargetMs = positionMs
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

    fun playTrackFromQueue(track: Track, queueTracks: List<Track>, fallbackIndex: Int) {
        val activePlayer = getActivePlayer() ?: return
        if (!track.uri.startsWith("content://") && track.path.isNotEmpty() && !File(track.path).exists() && !File(track.path).canRead()) {
            Log.w(TAG, "Selected track file does not exist: ${track.path}")
            showCannotPlayToast()
            return
        }

        val currentFolder = _uiState.value.currentTrack?.folderPath
        val isSameFolder = !track.folderPath.isEmpty() && currentFolder == track.folderPath
        var matchedIndex = -1

        if (isSameFolder && activePlayer.mediaItemCount > 0) {
            for (i in 0 until activePlayer.mediaItemCount) {
                if (activePlayer.getMediaItemAt(i).mediaId == track.uri) {
                    matchedIndex = i
                    break
                }
            }
        }

        if (matchedIndex >= 0 && activePlayer.mediaItemCount == queueTracks.size) {
            val item = activePlayer.getMediaItemAt(matchedIndex)
            val now = android.os.SystemClock.elapsedRealtime()
            lastSeekTimestamp = 0L
            lastSeekTargetMs = -1L
            anchorPositionMs = 0L
            anchorRealtimeMs = now
            activePlayer.seekTo(matchedIndex, 0L)
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            if (!activePlayer.isPlaying) {
                activePlayer.play()
            }
            loadCurrentTrackMetadata(item)
            _currentPosition.value = 0L
            updateStateFromController()
        } else if (queueTracks.isNotEmpty()) {
            playTracks(queueTracks, fallbackIndex)
        } else if (track.folderPath.isNotEmpty()) {
            playFolder(track.folderPath, fallbackIndex)
        } else {
            showCannotPlayToast()
        }
    }

    fun playTrackAtIndex(index: Int) {
        val activePlayer = getActivePlayer() ?: return
        if (index in 0 until activePlayer.mediaItemCount) {
            val item = activePlayer.getMediaItemAt(index)
            val filePath = item.mediaMetadata.extras?.getString("filePath") ?: ""
            if (filePath.isNotEmpty() && !File(filePath).exists() && !File(filePath).canRead()) {
                Log.w(TAG, "File no longer exists for track at index $index: $filePath")
                showCannotPlayToast()
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            lastSeekTimestamp = 0L
            lastSeekTargetMs = -1L
            anchorPositionMs = 0L
            anchorRealtimeMs = now
            activePlayer.seekTo(index, 0L)
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            if (!activePlayer.isPlaying) {
                activePlayer.play()
            }
            loadCurrentTrackMetadata(item)
            _currentPosition.value = 0L
            updateStateFromController()
        } else {
            showCannotPlayToast()
        }
    }

    fun hasMediaItemsForFolder(folderPath: String): Boolean {
        val activePlayer = getActivePlayer() ?: return false
        if (activePlayer.mediaItemCount == 0) return false
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
            val adjustedIndex = if (targetTrack != null) {
                validTracks.indexOfFirst { it.uri == targetTrack.uri }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            playTracks(validTracks, adjustedIndex)
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val activePlayer = getActivePlayer() ?: return
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
        val adjustedIndex = if (targetTrack != null) {
            validTracks.indexOfFirst { it.uri == targetTrack.uri }.takeIf { it >= 0 } ?: 0
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

        val safeIndex = adjustedIndex.coerceIn(0, mediaItems.size - 1)
        val now = android.os.SystemClock.elapsedRealtime()
        lastSeekTimestamp = 0L
        lastSeekTargetMs = -1L
        anchorPositionMs = 0L
        anchorRealtimeMs = now
        activePlayer.setMediaItems(mediaItems, safeIndex, 0L)
        activePlayer.prepare()
        activePlayer.play()
        loadCurrentTrackMetadata(mediaItems[safeIndex])
        _currentPosition.value = 0L
        updateStateFromController()
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
