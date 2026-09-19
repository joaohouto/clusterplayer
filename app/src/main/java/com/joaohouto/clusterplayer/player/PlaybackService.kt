package com.joaohouto.clusterplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.joaohouto.clusterplayer.MainActivity
import com.joaohouto.clusterplayer.data.local.PreferencesDataStore
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val POSITION_SAVE_INTERVAL_MS = 3000L
        private const val MAX_LOAD_RETRIES = 3

        @Volatile
        private var mediaSessionInstance: MediaSession? = null

        fun getMediaSessionInstance(): MediaSession? = mediaSessionInstance
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var repository: MusicRepository
    private lateinit var audioEffectsManager: AudioEffectsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSavingJob: Job? = null
    private var retryCount = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating PlaybackService...")

        preferencesDataStore = PreferencesDataStore.getInstance(this)
        repository = MusicRepository.getInstance(this)
        audioEffectsManager = AudioEffectsManager()

        initializePlayer()
        initializeMediaSession()
        setupPlayerListeners()
        startPeriodicPositionSaving()

        // Trigger autoplay restoration
        serviceScope.launch {
            restoreAutoplayState()
        }
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Attach audio effects immediately if audioSessionId is already known
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffectsManager.updateAudioSession(player.audioSessionId)
        }
    }

    private fun initializeMediaSession() {
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        mediaSessionInstance = mediaSession
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Log.d(TAG, "Audio session ID changed: $audioSessionId")
                audioEffectsManager.updateAudioSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        retryCount = 0 // Reset retry counter on successful playback readiness
                        saveCurrentStateImmediate()
                    }
                    Player.STATE_ENDED -> {
                        saveCurrentStateImmediate()
                    }
                    else -> {}
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveCurrentStateImmediate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveCurrentStateImmediate()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                serviceScope.launch(Dispatchers.IO) {
                    preferencesDataStore.saveShuffleMode(shuffleModeEnabled)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                serviceScope.launch(Dispatchers.IO) {
                    preferencesDataStore.saveRepeatMode(repeatMode)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error encountered: ${error.errorCodeName} (${error.errorCode})", error)
                handlePlayerErrorWithRetry(error)
            }
        })
    }

    private fun handlePlayerErrorWithRetry(error: PlaybackException) {
        if (retryCount < MAX_LOAD_RETRIES) {
            retryCount++
            val backoffDelay = (retryCount * 1000L) // 1s, 2s, 3s exponential-ish backoff
            Log.w(TAG, "Attempting retry $retryCount of $MAX_LOAD_RETRIES in ${backoffDelay}ms...")

            serviceScope.launch {
                delay(backoffDelay)
                val currentMediaItemIndex = player.currentMediaItemIndex
                val currentPosition = player.currentPosition
                if (currentMediaItemIndex >= 0 && currentMediaItemIndex < player.mediaItemCount) {
                    player.prepare()
                    player.seekTo(currentMediaItemIndex, currentPosition)
                    player.play()
                }
            }
        } else {
            Log.e(TAG, "Max retries reached. Playback stopped due to persistent error.")
        }
    }

    private fun startPeriodicPositionSaving() {
        positionSavingJob?.cancel()
        positionSavingJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    if (currentPos > 0) {
                        preferencesDataStore.savePosition(currentPos)
                    }
                }
            }
        }
    }

    private fun saveCurrentStateImmediate() {
        serviceScope.launch(Dispatchers.IO) {
            val currentItem = player.currentMediaItem
            val mediaId = currentItem?.mediaId
            val extras = currentItem?.mediaMetadata?.extras
            val folderPath = extras?.getString("folderPath") ?: ""
            val position = player.currentPosition

            if (!mediaId.isNullOrEmpty()) {
                preferencesDataStore.saveLastPlayback(
                    trackUri = mediaId,
                    folderPath = folderPath,
                    positionMs = position
                )
            }
        }
    }

    private suspend fun restoreAutoplayState() {
        try {
            val snapshot = preferencesDataStore.getPlaybackSnapshot()
            val folderPath = snapshot.lastFolderPath
            val trackUri = snapshot.lastTrackUri
            val positionMs = snapshot.lastPositionMs

            player.shuffleModeEnabled = snapshot.isShuffleEnabled
            player.repeatMode = snapshot.repeatMode

            if (!folderPath.isNullOrEmpty()) {
                var tracks = repository.getTracksForFolderSync(folderPath)
                if (tracks.isEmpty()) {
                    // Give scanner a moment or scan if database is not yet populated
                    val scanResult = com.joaohouto.clusterplayer.data.scanner.StorageScanner(this@PlaybackService).scanStorage()
                    tracks = scanResult.tracks.filter { it.folderPath == folderPath }.map { it.toDomain() }
                }

                if (tracks.isNotEmpty()) {
                    val mediaItems = tracks.map { trackToMediaItem(it) }
                    player.setMediaItems(mediaItems)

                    val targetIndex = if (!trackUri.isNullOrEmpty()) {
                        mediaItems.indexOfFirst { it.mediaId == trackUri }.coerceAtLeast(0)
                    } else {
                        0
                    }

                    val seekPos = if (positionMs > 0L) positionMs else 0L
                    player.seekTo(targetIndex, seekPos)
                    player.prepare()
                    player.play()
                    Log.d(TAG, "Autoplay restored: folder=$folderPath, index=$targetIndex, pos=${seekPos}ms")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring autoplay state", e)
        }
    }

    private fun trackToMediaItem(track: Track): MediaItem {
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

        return MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveCurrentStateImmediate()
        positionSavingJob?.cancel()
        serviceScope.cancel()

        audioEffectsManager.release()

        mediaSession?.run {
            player.release()
            release()
            mediaSessionInstance = null
        }
        super.onDestroy()
    }
}
