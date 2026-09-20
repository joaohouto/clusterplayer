package com.joaohouto.clusterplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaohouto.clusterplayer.ui.components.AudioArtExtractor
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AudioArtBitmapLoader(private val context: Context) : BitmapLoader {

    companion object {
        // Fast-path memory cache for decoded notification bitmaps (~2MB total RAM)
        private val bitmapCache = object : LruCache<String, Bitmap>(16) {}
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val key = data.hashCode().toString()
        val cached = bitmapCache.get(key)
        if (cached != null && !cached.isRecycled) {
            return Futures.immediateFuture(cached)
        }

        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            if (bmp != null) {
                bitmapCache.put(key, bmp)
                Futures.immediateFuture(bmp)
            } else {
                Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode artwork bytes"))
            }
        } catch (e: Exception) {
            Futures.immediateFailedFuture(e)
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return loadBitmapInternal(uri.toString())
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

        return loadBitmapInternal(uriStr)
    }

    private fun loadBitmapInternal(uriStr: String): ListenableFuture<Bitmap> {
        // 1. Fast-path: already decoded bitmap in memory cache (immediate synchronous return)
        val cachedBmp = bitmapCache.get(uriStr)
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            return Futures.immediateFuture(cachedBmp)
        }

        // 2. Medium-path: bytes already extracted in AudioArtExtractor memory cache
        val cachedBytes = AudioArtExtractor.getCachedArt(uriStr)
        if (cachedBytes != null) {
            return try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size, opts)
                if (bmp != null) {
                    bitmapCache.put(uriStr, bmp)
                    Futures.immediateFuture(bmp)
                } else {
                    Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode cached bytes"))
                }
            } catch (e: Exception) {
                Futures.immediateFailedFuture(e)
            }
        }

        // 3. Fallback: extract on executor, cache in memory and return
        return Futures.submit(Callable<Bitmap> {
            val artBytes = AudioArtExtractor.extractArtDirect(context, uriStr)
                ?: throw IllegalArgumentException("No embedded art found for $uriStr")

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                ?: throw IllegalArgumentException("Failed to decode artwork bytes")

            bitmapCache.put(uriStr, bmp)
            bmp
        }, executor)
    }
}
