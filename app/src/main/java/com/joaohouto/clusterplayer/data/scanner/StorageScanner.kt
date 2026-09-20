package com.joaohouto.clusterplayer.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.joaohouto.clusterplayer.R
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
        val tracksMap = mutableMapOf<String, TrackEntity>() // Key: canonical path or content URI

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

        // Group tracks by canonical folder path to prevent duplicates caused by symlinks (/sdcard, /storage/self/primary)
        val folderTracks = tracksMap.values.groupBy { track ->
            getCanonicalFolderPath(track.folderPath)
        }

        val foldersMap = mutableMapOf<String, FolderEntity>()

        for ((folderPath, tracks) in folderTracks) {
            if (tracks.isNotEmpty()) {
                val folderName = try {
                    val file = File(folderPath)
                    if (file.name.isNotEmpty()) file.name else folderPath
                } catch (e: Exception) {
                    folderPath
                }

                // Deduplicate check: if a folder with the same name and same track count already exists,
                // prefer the public standard path (/storage/...) over system daemon mount paths (/mnt/media_rw/...)
                val existing = foldersMap.values.find { it.name.equals(folderName, ignoreCase = true) && it.trackCount == tracks.size }
                if (existing != null) {
                    if (folderPath.startsWith("/storage/") && !existing.path.startsWith("/storage/")) {
                        foldersMap.remove(existing.path)
                        foldersMap[folderPath] = FolderEntity(
                            path = folderPath,
                            name = folderName,
                            trackCount = tracks.size,
                            lastModified = System.currentTimeMillis()
                        )
                    }
                } else {
                    foldersMap[folderPath] = FolderEntity(
                        path = folderPath,
                        name = folderName,
                        trackCount = tracks.size,
                        lastModified = System.currentTimeMillis()
                    )
                }
            }
        }

        val sortedFolders = foldersMap.values.toMutableList()
        sortedFolders.sortBy { it.name.lowercase(Locale.ROOT) }

        ScanResult(
            folders = sortedFolders,
            tracks = tracksMap.values.toList()
        )
    }

    private fun getCanonicalFolderPath(path: String): String {
        return try {
            val file = File(path)
            if (file.exists()) file.canonicalPath else path
        } catch (e: Exception) {
            path
        }
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
                    val canonicalPath = try { file?.canonicalPath ?: filePath } catch (e: Exception) { filePath }
                    val folderPath = try { file?.parentFile?.canonicalPath ?: "Root" } catch (e: Exception) { "Root" }
                    val unknownTrackStr = context.getString(R.string.unknown_track)
                    val unknownArtistStr = context.getString(R.string.unknown_artist)
                    val unknownAlbumStr = context.getString(R.string.unknown_album)
                    val displayName = title ?: file?.nameWithoutExtension ?: unknownTrackStr

                    val entity = TrackEntity(
                        id = 0,
                        uri = contentUri,
                        path = canonicalPath,
                        title = displayName,
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") unknownArtistStr else artist,
                        album = if (album.isNullOrBlank() || album == "<unknown>") unknownAlbumStr else album,
                        durationMs = duration,
                        folderPath = folderPath,
                        trackNumber = trackNum
                    )
                    tracksMap[canonicalPath.ifEmpty { contentUri }] = entity
                }
            }
        }
    }

    private fun scanPhysicalStorage(tracksMap: MutableMap<String, TrackEntity>) {
        val searchRoots = mutableListOf<File>()
        val foundUsbNames = mutableSetOf<String>()

        // 1. External storage directory (Standard internal storage)
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists() && extStorage.canRead()) {
            val canonical = try { extStorage.canonicalFile } catch (e: Exception) { extStorage }
            searchRoots.add(canonical)
        }

        // 2. /storage directory (for USB drives, external SD cards like /storage/XXXX-XXXX)
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.canRead()) {
            storageDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead() &&
                    !file.name.equals("emulated", ignoreCase = true) &&
                    !file.name.equals("self", ignoreCase = true)) {
                    val canonical = try { file.canonicalFile } catch (e: Exception) { file }
                    if (!searchRoots.any { it.path == canonical.path }) {
                        searchRoots.add(canonical)
                        foundUsbNames.add(file.name.lowercase(Locale.ROOT))
                    }
                }
            }
        }

        // 3. /mnt/media_rw or /mnt/usb (common on automotive Android ROMs)
        // ONLY scan if the drive was not already found in /storage to prevent duplicate folder listings
        val mntDir = File("/mnt")
        if (mntDir.exists() && mntDir.canRead()) {
            mntDir.listFiles()?.forEach { file ->
                if (file.isDirectory && (file.name.contains("usb", ignoreCase = true) || file.name.contains("media", ignoreCase = true) || file.name.contains("sdcard", ignoreCase = true))) {
                    val canonical = try { file.canonicalFile } catch (e: Exception) { file }
                    val dirNameLower = file.name.lowercase(Locale.ROOT)
                    val isDuplicate = searchRoots.any { it.path == canonical.path } || foundUsbNames.any { dirNameLower.contains(it) }
                    if (!isDuplicate) {
                        searchRoots.add(canonical)
                    }
                }
            }
        }

        // Reuse a single MediaMetadataRetriever instance across all physical files for massive speedup
        val retriever = MediaMetadataRetriever()
        try {
            for (root in searchRoots) {
                scanDirectoryRecursively(root, tracksMap, retriever)
            }
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        tracksMap: MutableMap<String, TrackEntity>,
        retriever: MediaMetadataRetriever
    ) {
        if (!dir.exists() || !dir.canRead()) return

        // Skip system/hidden folders
        val dirName = dir.name
        if (dirName.startsWith(".") || dirName.equals("Android", ignoreCase = true)) {
            return
        }

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(file, tracksMap, retriever)
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in SUPPORTED_EXTENSIONS) {
                    val canonicalPath = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                    if (!tracksMap.containsKey(canonicalPath) && !tracksMap.containsKey(file.absolutePath)) {
                        val track = extractTrackMetadata(file, canonicalPath, retriever)
                        tracksMap[canonicalPath] = track
                    }
                }
            }
        }
    }

    private fun extractTrackMetadata(
        file: File,
        canonicalPath: String,
        retriever: MediaMetadataRetriever
    ): TrackEntity {
        var title = file.nameWithoutExtension
        var artist = context.getString(R.string.unknown_artist)
        var album = context.getString(R.string.unknown_album)
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
        }

        val uri = Uri.fromFile(file).toString()
        val folderPath = try { file.parentFile?.canonicalPath ?: (file.parentFile?.absolutePath ?: "Root") } catch (e: Exception) { "Root" }

        return TrackEntity(
            id = 0,
            uri = uri,
            path = canonicalPath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            folderPath = folderPath,
            trackNumber = trackNumber
        )
    }
}
