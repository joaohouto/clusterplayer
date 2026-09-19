package com.joaohouto.clusterplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent

class AutoMediaButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AutoMediaButton"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Received automotive steering wheel media button: keyCode=${keyEvent.keyCode}")
                val session = PlaybackService.getMediaSessionInstance()
                val player = session?.player

                if (player != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> player.seekToNextMediaItem()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (player.currentPosition > 3000L) {
                                player.seekTo(0L)
                            } else {
                                player.seekToPreviousMediaItem()
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> player.stop()
                    }
                }
            }
        }
    }
}
