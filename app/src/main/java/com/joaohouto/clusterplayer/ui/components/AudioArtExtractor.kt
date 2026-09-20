package com.joaohouto.clusterplayer.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AudioArtExtractor {
    // Memory cache for embedded artwork bytes: max 8MB of byte data
    private val artCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun getEmbeddedArt(context: Context, uriOrPath: String): ByteArray? = withContext(Dispatchers.IO) {
        if (uriOrPath.isBlank()) return@withContext null

        synchronized(artCache) {
            val cached = artCache.get(uriOrPath)
            if (cached != null) return@withContext cached
        }

        val retriever = MediaMetadataRetriever()
        var picture: ByteArray? = null

        try {
            if (uriOrPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(uriOrPath))
            } else {
                val file = File(uriOrPath)
                if (file.exists()) {
                    retriever.setDataSource(file.absolutePath)
                }
            }
            picture = retriever.embeddedPicture
            if (picture != null) {
                synchronized(artCache) {
                    artCache.put(uriOrPath, picture)
                }
            }
        } catch (ignored: Exception) {
            // Some formats might not have art or throw on malformed ID3
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        picture
    }
}
