package com.joaohouto.clusterplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaohouto.clusterplayer.ui.components.AudioArtExtractor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AudioArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return Futures.submit(Callable<Bitmap> {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                ?: throw IllegalArgumentException("Failed to decode artwork bytes")
        }, executor)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return Futures.submit(Callable<Bitmap> {
            val artBytes = runBlocking {
                AudioArtExtractor.getEmbeddedArt(context, uri.toString())
            } ?: throw IllegalArgumentException("No embedded art found in $uri")

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                ?: throw IllegalArgumentException("Failed to decode artwork bytes")
        }, executor)
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }
        val filePath = metadata.extras?.getString("filePath")
        val uriStr = if (!filePath.isNullOrEmpty()) filePath else metadata.artworkUri?.toString()
        if (uriStr.isNullOrEmpty()) {
            return Futures.immediateFailedFuture(IllegalArgumentException("No artwork URI or filePath"))
        }

        return Futures.submit(Callable<Bitmap> {
            val artBytes = runBlocking {
                AudioArtExtractor.getEmbeddedArt(context, uriStr)
            } ?: throw IllegalArgumentException("No embedded art found for $uriStr")

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                ?: throw IllegalArgumentException("Failed to decode artwork bytes")
        }, executor)
    }
}
