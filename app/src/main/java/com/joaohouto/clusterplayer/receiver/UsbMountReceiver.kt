package com.joaohouto.clusterplayer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.joaohouto.clusterplayer.data.repository.MusicRepository
import com.joaohouto.clusterplayer.player.PlaybackService

class UsbMountReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbMountReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val uri = intent.data
        Log.d(TAG, "Storage event received: action=$action, data=$uri")

        when (action) {
            Intent.ACTION_MEDIA_MOUNTED -> {
                Log.i(TAG, "External storage mounted: $uri. Triggering background media scan.")
                MusicRepository.getInstance(context).triggerScan(clearOld = false)
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> {
                Log.w(TAG, "External media unmounted or ejected: $uri. Rescanning storage to prune missing paths.")
                MusicRepository.getInstance(context).triggerScan(clearOld = true)
            }
        }
    }
}
