package com.joaohouto.clusterplayer.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.joaohouto.clusterplayer.data.local.entity.FolderEntity
import com.joaohouto.clusterplayer.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ScanResult(
    val folders: List<FolderEntity>,
    val tracks: List<TrackEntity>
)

class StorageScanner(private val context: Context) {

    companion object {
        private const val TAG = "StorageScanner"
        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg")
    }

    suspend fun scanStorage(): ScanResult = withContext(Dispatchers.IO) {
        val tracksMap = mutableMapOf<String, TrackEntity>() // Key: file path or URI

        // 1. Scan via MediaStore (Fast and reliable for Android indexed media)
        try {
            scanMediaStore(tracksMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }

        // 2. Direct physical storage scan (Critical for USB pendrives and automotive storage)
        try {
            scanPhysicalStorage(tracksMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning physical storage", e)
        }

        // Group tracks by folder
        val folderTracks = tracksMap.values.groupBy { it.folderPath }
        val folders = mutableListOf<FolderEntity>()

        for ((folderPath, tracks) in folderTracks) {
            if (tracks.isNotEmpty()) {
                val folderName = try {
                    val file = File(folderPath)
                    if (file.name.isNotEmpty()) file.name else folderPath
                } catch (e: Exception) {
                    folderPath
                }
                folders.add(
                    FolderEntity(
                        path = folderPath,
                        name = folderName,
                        trackCount = tracks.size,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }
        }

        // Sort folders alphabetically
        folders.sortBy { it.name.lowercase(Locale.ROOT) }

        ScanResult(
            folders = folders,
            tracks = tracksMap.values.toList()
        )
    }

    private fun scanMediaStore(tracksMap: MutableMap<String, TrackEntity>) {
        val contentResolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = if (dataCol != -1) cursor.getString(dataCol) else null
                val title = if (titleCol != -1) cursor.getString(titleCol) else null
                val artist = if (artistCol != -1) cursor.getString(artistCol) else null
                val album = if (albumCol != -1) cursor.getString(albumCol) else null
                val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                val trackNum = if (trackCol != -1) cursor.getInt(trackCol) else 0

                val contentUri = ContentUris.withAppendedId(uri, id).toString()
                val filePath = path ?: ""
                val file = if (filePath.isNotEmpty()) File(filePath) else null
                val ext = file?.extension?.lowercase(Locale.ROOT) ?: ""

                if (filePath.isEmpty() || ext in SUPPORTED_EXTENSIONS) {
                    val folderPath = file?.parentFile?.absolutePath ?: "Root"
                    val displayName = title ?: file?.nameWithoutExtension ?: "Faixa Desconhecida"

                    val entity = TrackEntity(
                        id = 0,
                        uri = contentUri,
                        path = filePath,
                        title = displayName,
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Artista Desconhecido" else artist,
                        album = if (album.isNullOrBlank() || album == "<unknown>") "Álbum Desconhecido" else album,
                        durationMs = duration,
                        folderPath = folderPath,
                        trackNumber = trackNum
                    )
                    tracksMap[filePath.ifEmpty { contentUri }] = entity
                }
            }
        }
    }

    private fun scanPhysicalStorage(tracksMap: MutableMap<String, TrackEntity>) {
        val searchRoots = mutableListOf<File>()

        // 1. External storage directory
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists() && extStorage.canRead()) {
            searchRoots.add(extStorage)
        }

        // 2. /storage directory (for USB drives, external SD cards like /storage/XXXX-XXXX)
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.canRead()) {
            storageDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead() && !file.name.equals("emulated", ignoreCase = true) && !file.name.equals("self", ignoreCase = true)) {
                    searchRoots.add(file)
                }
            }
        }

        // 3. /mnt/media_rw or /mnt/usb (common on automotive Android ROMs)
        val mntDir = File("/mnt")
        if (mntDir.exists() && mntDir.canRead()) {
            mntDir.listFiles()?.forEach { file ->
                if (file.isDirectory && (file.name.contains("usb", ignoreCase = true) || file.name.contains("media", ignoreCase = true) || file.name.contains("sdcard", ignoreCase = true))) {
                    searchRoots.add(file)
                }
            }
        }

        for (root in searchRoots) {
            scanDirectoryRecursively(root, tracksMap)
        }
    }

    private fun scanDirectoryRecursively(dir: File, tracksMap: MutableMap<String, TrackEntity>) {
        if (!dir.exists() || !dir.canRead()) return

        // Skip system/hidden folders
        val dirName = dir.name
        if (dirName.startsWith(".") || dirName.equals("Android", ignoreCase = true)) {
            return
        }

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(file, tracksMap)
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in SUPPORTED_EXTENSIONS) {
                    val filePath = file.absolutePath
                    if (!tracksMap.containsKey(filePath)) {
                        // Extract metadata from file directly
                        val track = extractTrackMetadata(file)
                        tracksMap[filePath] = track
                    }
                }
            }
        }
    }

    private fun extractTrackMetadata(file: File): TrackEntity {
        val retriever = MediaMetadataRetriever()
        var title = file.nameWithoutExtension
        var artist = "Artista Desconhecido"
        var album = "Álbum Desconhecido"
        var durationMs = 0L
        var trackNumber = 0

        try {
            retriever.setDataSource(file.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val metaTrackNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            if (!metaDuration.isNullOrBlank()) durationMs = metaDuration.toLongOrNull() ?: 0L
            if (!metaTrackNum.isNullOrBlank()) {
                trackNumber = metaTrackNum.split("/").firstOrNull()?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ID3 from ${file.absolutePath}: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        val uri = Uri.fromFile(file).toString()
        val folderPath = file.parentFile?.absolutePath ?: "Root"

        return TrackEntity(
            id = 0,
            uri = uri,
            path = file.absolutePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            folderPath = folderPath,
            trackNumber = trackNumber
        )
    }
}
