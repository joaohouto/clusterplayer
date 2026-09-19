package com.joaohouto.clusterplayer.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

class AudioEffectsManager {
    companion object {
        private const val TAG = "AudioEffectsManager"
        // Target gain in millibels (mB). 800 mB = +0.8 dB (moderate boost that prevents digital clipping)
        private const val DEFAULT_TARGET_GAIN_MB = 800
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    fun updateAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        release()

        // 1. Setup LoudnessEnhancer
        try {
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain(DEFAULT_TARGET_GAIN_MB)
            enhancer.enabled = true
            loudnessEnhancer = enhancer
            Log.d(TAG, "LoudnessEnhancer attached to session $audioSessionId with gain ${DEFAULT_TARGET_GAIN_MB}mB")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize LoudnessEnhancer on session $audioSessionId: ${e.message}")
        }

        // 2. Setup Equalizer
        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = true
            equalizer = eq
            Log.d(TAG, "Equalizer attached to session $audioSessionId with ${eq.numberOfBands} bands")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Equalizer on session $audioSessionId: ${e.message}")
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
