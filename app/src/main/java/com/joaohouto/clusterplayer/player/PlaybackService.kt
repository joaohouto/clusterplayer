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
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val POSITION_SAVE_INTERVAL_MS = 10000L
        private const val MAX_LOAD_RETRIES = 3
        private const val NOTIFICATION_CHANNEL_ID = "cluster_player_channel"
        private const val NOTIFICATION_ID = 1001

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

    private var crossfadeSeconds: Int = 0
    private var fadeInJob: Job? = null
    private var fadeTickerJob: Job? = null
    private var fadeOutActive: Boolean = false
    private var masterVolume: Float = 1.0f
    private var currentFadeScale: Float = 1.0f

    private fun applyVolume() {
        if (::player.isInitialized) {
            player.volume = (currentFadeScale * masterVolume).coerceIn(0f, 1f)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating PlaybackService...")

        preferencesDataStore = PreferencesDataStore.getInstance(this)
        repository = MusicRepository.getInstance(this)
        audioEffectsManager = AudioEffectsManager()

        setupNotificationChannel()
        setupNotificationProvider()

        initializePlayer()
        initializeMediaSession()
        setupPlayerListeners()
        startPeriodicPositionSaving()

        serviceScope.launch {
            preferencesDataStore.crossfadeSecondsFlow.collect { seconds ->
                crossfadeSeconds = seconds
                Log.d(TAG, "Crossfade seconds updated: $crossfadeSeconds")
                if (crossfadeSeconds > 0 && ::player.isInitialized && player.isPlaying) {
                    startFadeTicker()
                } else if (crossfadeSeconds <= 0) {
                    stopFadeTicker()
                }
            }
        }

        serviceScope.launch {
            preferencesDataStore.appVolumePercentFlow.collect { percent ->
                masterVolume = (percent.toFloat() / 100f).coerceIn(0.1f, 1.0f)
                applyVolume()
                Log.d(TAG, "Master volume updated: $masterVolume ($percent%)")
            }
        }

        serviceScope.launch {
            preferencesDataStore.loudnessBoostFlow.collect { enabled ->
                audioEffectsManager.setLoudnessEnabled(enabled)
                Log.d(TAG, "Loudness boost updated: $enabled")
            }
        }

        // Trigger autoplay restoration
        serviceScope.launch {
            restoreAutoplayState()
        }
    }

    private fun setupNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(com.joaohouto.clusterplayer.R.string.notification_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(com.joaohouto.clusterplayer.R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun setupNotificationProvider() {
        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(com.joaohouto.clusterplayer.R.string.notification_channel_name)
            .build()
        notificationProvider.setSmallIcon(com.joaohouto.clusterplayer.R.mipmap.ic_launcher)
        setMediaNotificationProvider(notificationProvider)
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
            .setBitmapLoader(AudioArtBitmapLoader(this))
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
                if (isPlaying) {
                    startFadeTicker()
                    if (crossfadeSeconds > 0 && player.volume < 0.2f) {
                        startFadeIn(1200L)
                    }
                } else {
                    stopFadeTicker()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveCurrentStateImmediate()
                if (crossfadeSeconds > 0 && mediaItem != null) {
                    startFadeIn((crossfadeSeconds * 1000L).coerceIn(1000L, 3500L))
                } else {
                    player.volume = 1.0f
                }
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
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                    serviceScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@PlaybackService,
                            getString(com.joaohouto.clusterplayer.R.string.error_cannot_play),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                }
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

    private fun startFadeIn(durationMs: Long) {
        fadeInJob?.cancel()
        fadeOutActive = false
        if (durationMs <= 0L) {
            currentFadeScale = 1.0f
            applyVolume()
            return
        }
        fadeInJob = serviceScope.launch {
            currentFadeScale = 0.0f
            applyVolume()
            val stepInterval = 40L
            val steps = (durationMs / stepInterval).coerceAtLeast(1)
            for (i in 1..steps) {
                delay(stepInterval)
                val linearProgress = i.toFloat() / steps
                currentFadeScale = Math.sin(linearProgress * Math.PI / 2).toFloat().coerceIn(0f, 1f)
                applyVolume()
            }
            currentFadeScale = 1.0f
            applyVolume()
        }
    }

    private fun updateFadeOut(remainingMs: Long, fadeDurationMs: Long) {
        if (fadeInJob?.isActive == true) return
        fadeOutActive = true
        val progress = (remainingMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
        currentFadeScale = Math.sin(progress * Math.PI / 2).toFloat().coerceIn(0f, 1f)
        applyVolume()
    }

    private fun startFadeTicker() {
        fadeTickerJob?.cancel()
        if (crossfadeSeconds <= 0) return
        fadeTickerJob = serviceScope.launch {
            while (isActive) {
                delay(150L)
                if (player.isPlaying && crossfadeSeconds > 0 && player.duration > 0) {
                    val remainingMs = player.duration - player.currentPosition
                    val fadeDurationMs = crossfadeSeconds * 1000L
                    if (remainingMs in 0..fadeDurationMs) {
                        updateFadeOut(remainingMs, fadeDurationMs)
                    } else if (fadeOutActive && fadeInJob?.isActive != true) {
                        fadeOutActive = false
                        currentFadeScale = 1.0f
                        applyVolume()
                    }
                }
            }
        }
    }

    private fun stopFadeTicker() {
        fadeTickerJob?.cancel()
        fadeTickerJob = null
        fadeOutActive = false
    }

    private fun startPeriodicPositionSaving() {
        positionSavingJob?.cancel()
        // Must run on Main dispatcher because player properties (isPlaying, currentPosition) require application thread
        positionSavingJob = serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    if (currentPos > 0) {
                        withContext(Dispatchers.IO) {
                            preferencesDataStore.savePosition(currentPos)
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentStateImmediate() {
        // Read player properties ON MAIN THREAD synchronously
        val currentItem = player.currentMediaItem
        val mediaId = currentItem?.mediaId
        val extras = currentItem?.mediaMetadata?.extras
        val folderPath = extras?.getString("folderPath") ?: ""
        val position = player.currentPosition

        if (!mediaId.isNullOrEmpty()) {
            serviceScope.launch(Dispatchers.IO) {
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
            val snapshot = withContext(Dispatchers.IO) {
                preferencesDataStore.getPlaybackSnapshot()
            }
            val folderPath = snapshot.lastFolderPath
            val trackUri = snapshot.lastTrackUri
            val positionMs = snapshot.lastPositionMs

            player.shuffleModeEnabled = snapshot.isShuffleEnabled
            player.repeatMode = snapshot.repeatMode

            if (!folderPath.isNullOrEmpty()) {
                var tracks = repository.getTracksForFolderSync(folderPath)
                if (tracks.isEmpty()) {
                    // Give scanner a moment or scan if database is not yet populated
                    val scanResult = withContext(Dispatchers.IO) {
                        com.joaohouto.clusterplayer.data.scanner.StorageScanner(this@PlaybackService).scanStorage()
                    }
                    tracks = scanResult.tracks.filter { it.folderPath == folderPath }.map { it.toDomain() }
                }

                val validTracks = tracks.filter { it.path.isEmpty() || File(it.path).exists() }
                if (validTracks.isNotEmpty()) {
                    val mediaItems = validTracks.map { trackToMediaItem(it) }
                    player.setMediaItems(mediaItems)

                    val targetIndex = if (!trackUri.isNullOrEmpty()) {
                        mediaItems.indexOfFirst { it.mediaId == trackUri }.coerceAtLeast(0)
                    } else {
                        0
                    }

                    val seekPos = if (positionMs > 0L) positionMs else 0L
                    player.seekTo(targetIndex, seekPos)
                    player.prepare()
                    if (snapshot.autoplayOnStart) {
                        player.play()
                        Log.d(TAG, "Autoplay restored and playing: folder=$folderPath, index=$targetIndex, pos=${seekPos}ms")
                    } else {
                        Log.d(TAG, "Autoplay restored (paused): folder=$folderPath, index=$targetIndex, pos=${seekPos}ms")
                    }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed (app closed). Stopping playback.")
        saveCurrentStateImmediate()
        if (::player.isInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveCurrentStateImmediate()
        stopFadeTicker()
        fadeInJob?.cancel()
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
