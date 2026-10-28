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
                val controller = PlayerController.getInstance(context)
                val session = PlaybackService.getMediaSessionInstance()
                val player = session?.player

                if (player != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                player.prepare()
                            } else if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                player.seekToDefaultPosition()
                            }
                            player.play()
                            controller.onDirectPlaybackStateChanged()
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.pause()
                            controller.onDirectPlaybackStateChanged()
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            controller.playPause()
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> controller.next()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controller.previous()
                        KeyEvent.KEYCODE_MEDIA_STOP -> controller.stop()
                    }
                }
            }
        }
    }
}
