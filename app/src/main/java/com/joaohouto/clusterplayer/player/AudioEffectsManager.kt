package com.joaohouto.clusterplayer.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

class AudioEffectsManager {
    companion object {
        private const val TAG = "AudioEffectsManager"
        // Target gain in millibels (mB). 250 mB = +2.5 dB when enabled (safe boost)
        private const val DEFAULT_LOUDNESS_GAIN_MB = 250
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0
    private var isLoudnessEnabled: Boolean = false

    fun updateAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        release()

        // 1. Setup LoudnessEnhancer (Disabled by default to provide 0 dB clean unity gain)
        try {
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain(DEFAULT_LOUDNESS_GAIN_MB)
            enhancer.enabled = isLoudnessEnabled
            loudnessEnhancer = enhancer
            Log.d(TAG, "LoudnessEnhancer attached to session $audioSessionId (enabled=$isLoudnessEnabled)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize LoudnessEnhancer on session $audioSessionId: ${e.message}")
        }

        // 2. Setup Equalizer (Disabled by default)
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = false
            equalizer = eq
            Log.d(TAG, "Equalizer attached to session $audioSessionId with ${eq.numberOfBands} bands (disabled)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Equalizer on session $audioSessionId: ${e.message}")
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        isLoudnessEnabled = enabled
        try {
            loudnessEnhancer?.enabled = enabled
            Log.d(TAG, "LoudnessEnhancer enabled set to: $enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle LoudnessEnhancer: ${e.message}")
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set LoudnessEnhancer gain: ${e.message}")
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LoudnessEnhancer", e)
        } finally {
            loudnessEnhancer = null
        }

        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer", e)
        } finally {
            equalizer = null
        }
    }
}
