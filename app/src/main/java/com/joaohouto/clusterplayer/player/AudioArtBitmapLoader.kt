package com.joaohouto.clusterplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaohouto.clusterplayer.ui.components.AudioArtExtractor
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class AudioArtBitmapLoader(private val context: Context) : BitmapLoader {

    companion object {
        const val EXTRA_HIGH_RES_ART = "cluster_high_res_art"
        const val EXTRA_HIGH_RES_MEDIA_ID = "cluster_high_res_media_id"
        private const val HIGH_RES_MAX_DIM = 800

        // Fast-path memory cache for decoded notification bitmaps
        private val bitmapCache = object : LruCache<String, Bitmap>(16) {}
        private val highResJpegCache = object : LruCache<String, ByteArray>(16) {}
        private val mainHandler = Handler(Looper.getMainLooper())

        fun publishHighResArt(cacheKey: String, bmp: Bitmap?) {
            if (bmp == null || bmp.isRecycled) {
                clearHighResArt()
                return
            }
            var jpegBytes = highResJpegCache.get(cacheKey)
            if (jpegBytes == null) {
                val maxDim = max(bmp.width, bmp.height)
                val scaled = if (maxDim > HIGH_RES_MAX_DIM) {
                    val ratio = HIGH_RES_MAX_DIM.toFloat() / maxDim.toFloat()
                    val w = (bmp.width * ratio).roundToInt().coerceAtLeast(1)
                    val h = (bmp.height * ratio).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bmp, w, h, true)
                } else {
                    bmp
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                jpegBytes = out.toByteArray()
                if (jpegBytes.size <= 250 * 1024) {
                    highResJpegCache.put(cacheKey, jpegBytes)
                } else {
                    val out2 = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, out2)
                    jpegBytes = out2.toByteArray()
                    highResJpegCache.put(cacheKey, jpegBytes)
                }
            }
            val finalBytes = jpegBytes
            mainHandler.post {
                val session = PlaybackService.getMediaSessionInstance() ?: return@post
                val extras = Bundle().apply {
                    putByteArray(EXTRA_HIGH_RES_ART, finalBytes)
                    putString(EXTRA_HIGH_RES_MEDIA_ID, cacheKey)
                }
                session.setSessionExtras(extras)
            }
        }

        fun clearHighResArt() {
            mainHandler.post {
                PlaybackService.getMediaSessionInstance()?.setSessionExtras(Bundle.EMPTY)
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val key = data.hashCode().toString()
        val cached = bitmapCache.get(key)
        if (cached != null && !cached.isRecycled) {
            publishHighResArt(key, cached)
            return Futures.immediateFuture(cached)
        }

        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            if (bmp != null) {
                bitmapCache.put(key, bmp)
                publishHighResArt(key, bmp)
                Futures.immediateFuture(bmp)
            } else {
                clearHighResArt()
                Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode artwork bytes"))
            }
        } catch (e: Exception) {
            clearHighResArt()
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
            clearHighResArt()
            return Futures.immediateFailedFuture(IllegalArgumentException("No artwork URI or filePath"))
        }

        return loadBitmapInternal(uriStr)
    }

    private fun loadBitmapInternal(uriStr: String): ListenableFuture<Bitmap> {
        // 1. Fast-path: already decoded bitmap in memory cache (immediate synchronous return)
        val cachedBmp = bitmapCache.get(uriStr)
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            publishHighResArt(uriStr, cachedBmp)
            return Futures.immediateFuture(cachedBmp)
        }

        // 2. Medium-path: bytes already extracted in AudioArtExtractor memory cache
        val cachedBytes = AudioArtExtractor.getCachedArt(uriStr)
        if (cachedBytes != null) {
            return try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size, opts)
                if (bmp != null) {
                    bitmapCache.put(uriStr, bmp)
                    publishHighResArt(uriStr, bmp)
                    Futures.immediateFuture(bmp)
                } else {
                    clearHighResArt()
                    Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode cached bytes"))
                }
            } catch (e: Exception) {
                clearHighResArt()
                Futures.immediateFailedFuture(e)
            }
        }

        // 3. Fallback: extract on executor, cache in memory and return
        return Futures.submit(Callable<Bitmap> {
            val artBytes = AudioArtExtractor.extractArtDirect(context, uriStr)
            if (artBytes == null) {
                clearHighResArt()
                throw IllegalArgumentException("No embedded art found for $uriStr")
            }

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
            if (bmp == null) {
                clearHighResArt()
                throw IllegalArgumentException("Failed to decode artwork bytes")
            }

            bitmapCache.put(uriStr, bmp)
            publishHighResArt(uriStr, bmp)
            bmp
        }, executor)
    }
}
