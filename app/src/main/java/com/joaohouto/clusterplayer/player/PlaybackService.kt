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
    private lateinit var volumeNormalizer: VolumeNormalizerAudioProcessor
    private lateinit var loudnessManager: LoudnessAnalysisManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSavingJob: Job? = null
    private var retryCount = 0

    private var masterVolume: Float = 1.0f
    private var currentAnalyzedMediaId: String? = null

    private fun applyVolume() {
        if (::player.isInitialized) {
            player.volume = masterVolume.coerceIn(0f, 1f)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating PlaybackService...")

        preferencesDataStore = PreferencesDataStore.getInstance(this)
        repository = MusicRepository.getInstance(this)
        audioEffectsManager = AudioEffectsManager()
        volumeNormalizer = VolumeNormalizerAudioProcessor()
        loudnessManager = LoudnessAnalysisManager.getInstance(this)

        setupNotificationChannel()
        setupNotificationProvider()

        initializePlayer()
        initializeMediaSession()
        setupPlayerListeners()
        startPeriodicPositionSaving()

        serviceScope.launch {
            preferencesDataStore.appVolumePercentFlow.collect { percent ->
                masterVolume = (percent.toFloat() / 100f).coerceIn(0.1f, 1.0f)
                applyVolume()
                Log.d(TAG, "Master volume updated: $masterVolume ($percent%)")
            }
        }

        serviceScope.launch {
            preferencesDataStore.loudnessBoostFlow.collect { enabled ->
                volumeNormalizer.isNormalizerEnabled = enabled
                Log.d(TAG, "Volume normalizer updated: enabled=$enabled")
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
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(volumeNormalizer))
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
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
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val transitionMs = android.os.SystemClock.elapsedRealtime()
                saveCurrentStateImmediate()
                PlayerController.getInstance(this@PlaybackService)
                    .onDirectTrackTransition(mediaItem, transitionMs)
                applyVolume()
                scheduleLoudnessAnalysisForCurrentAndNext(mediaItem)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val tagGainDb = EbuR128Scanner.extractTagGainFromMetadata(format.metadata)
                        if (tagGainDb != null) {
                            volumeNormalizer.setTrackReplayGain(
                                gainDb = tagGainDb,
                                rampDurationMs = VolumeNormalizerAudioProcessor.BOUNDARY_RAMP_MS
                            )
                            Log.d(TAG, "Applied ReplayGain/R128 tag from format metadata: $tagGainDb dB")
                            return
                        }
                    }
                }
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val tagGainDb = EbuR128Scanner.extractTagGainFromMetadata(metadata)
                if (tagGainDb != null) {
                    volumeNormalizer.setTrackReplayGain(
                        gainDb = tagGainDb,
                        rampDurationMs = VolumeNormalizerAudioProcessor.BOUNDARY_RAMP_MS
                    )
                    Log.d(TAG, "Applied ReplayGain/R128 tag from stream metadata: $tagGainDb dB")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    PlayerController.getInstance(this@PlaybackService).onDirectTrackTransition(
                        player.currentMediaItem,
                        android.os.SystemClock.elapsedRealtime()
                    )
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

    private fun scheduleLoudnessAnalysisForCurrentAndNext(currentItem: MediaItem?) {
        val mediaId = currentItem?.mediaId
        if (mediaId.isNullOrEmpty()) {
            currentAnalyzedMediaId = null
            volumeNormalizer.resetTrackGain()
            return
        }

        currentAnalyzedMediaId = mediaId
        // Start neutral (0 dB) with a short boundary micro-ramp until cache or scan resolves
        volumeNormalizer.resetTrackGain(rampDurationMs = VolumeNormalizerAudioProcessor.BOUNDARY_RAMP_MS)

        val currentFilePath = currentItem.mediaMetadata.extras?.getString("filePath")
        val nextIndex = player.nextMediaItemIndex
        val nextItem = if (nextIndex != C.INDEX_UNSET && nextIndex in 0 until player.mediaItemCount) {
            player.getMediaItemAt(nextIndex)
        } else {
            null
        }
        val nextUri = nextItem?.mediaId
        val nextFilePath = nextItem?.mediaMetadata?.extras?.getString("filePath")

        loudnessManager.analyzeCurrentAndNextTrack(
            currentUri = mediaId,
            currentFilePath = currentFilePath,
            nextUri = nextUri,
            nextFilePath = nextFilePath
        ) { measuredUri, measurement, fromCache ->
            // Guard against race if track changed while scanning
            if (currentAnalyzedMediaId == measuredUri) {
                val gainDb = measurement.computeGainDb()
                val rampMs = if (fromCache) {
                    VolumeNormalizerAudioProcessor.BOUNDARY_RAMP_MS
                } else {
                    VolumeNormalizerAudioProcessor.ASYNC_SCAN_RAMP_MS
                }
                volumeNormalizer.setTrackGainDb(
                    gainDb = gainDb,
                    rampDurationMs = rampMs,
                    fromTag = false
                )
                Log.d(
                    TAG,
                    "Loudness gain applied for $measuredUri: ${"%.2f".format(gainDb)} dB (lufs=${"%.2f".format(measurement.integratedLufs)}, fromCache=$fromCache, ramp=${rampMs}ms)"
                )
            }
        }
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
                    tracks = scanResult.tracks
                        .filter { it.folderPath == folderPath }
                        .map { it.toDomain() }
                        .sortedWith(compareBy<Track> { it.trackNumber }.thenBy { it.title.lowercase() })
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
                    scheduleLoudnessAnalysisForCurrentAndNext(player.currentMediaItem)
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
