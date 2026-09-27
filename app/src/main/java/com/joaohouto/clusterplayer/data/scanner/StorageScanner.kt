package com.joaohouto.clusterplayer.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
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

        // 1. Scan via MediaStore (Multi-volume support for Android 10+ and standard indexed storage)
        try {
            scanMediaStore(tracksMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }

        // 2. Direct physical storage scan (Comprehensive scan for USB pendrives, SD cards and automotive head units)
        try {
            scanPhysicalStorage(tracksMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning physical storage", e)
        }

        // Group tracks by canonical folder path
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

                foldersMap[folderPath] = FolderEntity(
                    path = folderPath,
                    name = folderName,
                    trackCount = tracks.size,
                    lastModified = System.currentTimeMillis()
                )
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
        val urisToQuery = mutableListOf<Uri>()

        // Multi-volume support for Android 10+ (API 29+) to discover USB drives in MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val volumeNames = MediaStore.getExternalVolumeNames(context)
                for (volume in volumeNames) {
                    urisToQuery.add(MediaStore.Audio.Media.getContentUri(volume))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting external volume names from MediaStore", e)
            }
        }
        if (urisToQuery.isEmpty()) {
            urisToQuery.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
            projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        for (contentUri in urisToQuery) {
            try {
                contentResolver.query(contentUri, projection.toTypedArray(), selection, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1
                    val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val path = if (dataCol != -1) cursor.getString(dataCol) else null
                        val title = if (titleCol != -1) cursor.getString(titleCol) else null
                        val artist = if (artistCol != -1) cursor.getString(artistCol) else null
                        val album = if (albumCol != -1) cursor.getString(albumCol) else null
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                        val trackNum = if (trackCol != -1) cursor.getInt(trackCol) else 0
                        val relPath = if (relPathCol != -1) cursor.getString(relPathCol) else null
                        val bucketName = if (bucketCol != -1) cursor.getString(bucketCol) else null

                        val itemUri = ContentUris.withAppendedId(contentUri, id).toString()
                        val filePath = path ?: ""
                        val file = if (filePath.isNotEmpty()) File(filePath) else null
                        val ext = file?.extension?.lowercase(Locale.ROOT) ?: ""

                        if (filePath.isEmpty() || ext in SUPPORTED_EXTENSIONS) {
                            val canonicalPath = try { file?.canonicalPath ?: filePath } catch (e: Exception) { filePath }
                            val folderPath = when {
                                file?.parentFile != null -> try { file.parentFile?.canonicalPath ?: file.parentFile?.absolutePath ?: "Root" } catch (e: Exception) { "Root" }
                                !relPath.isNullOrBlank() -> relPath.trimEnd('/')
                                !bucketName.isNullOrBlank() -> bucketName
                                else -> "Root"
                            }
                            val unknownTrackStr = context.getString(R.string.unknown_track)
                            val unknownArtistStr = context.getString(R.string.unknown_artist)
                            val unknownAlbumStr = context.getString(R.string.unknown_album)
                            val displayName = title ?: file?.nameWithoutExtension ?: unknownTrackStr

                            val entity = TrackEntity(
                                id = 0,
                                uri = itemUri,
                                path = canonicalPath,
                                title = displayName,
                                artist = if (artist.isNullOrBlank() || artist == "<unknown>") unknownArtistStr else artist,
                                album = if (album.isNullOrBlank() || album == "<unknown>") unknownAlbumStr else album,
                                durationMs = duration,
                                folderPath = folderPath,
                                trackNumber = trackNum
                            )
                            val key = canonicalPath.ifEmpty { itemUri }
                            tracksMap[key] = entity
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore uri: $contentUri", e)
            }
        }
    }

    private fun getStorageRoots(): List<File> {
        val roots = LinkedHashSet<File>()

        // 1. Standard external storage (internal shared storage: /storage/emulated/0)
        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                roots.add(extStorage.canonicalFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving external storage directory", e)
        }

        // 2. ContextCompat.getExternalFilesDirs / getExternalCacheDirs
        // Guaranteed Android API that resolves app directories on ALL mounted storage volumes (internal, SD, USB).
        // e.g. /storage/1234-5678/Android/data/com.joaohouto.clusterplayer/files -> root: /storage/1234-5678
        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            externalDirs.forEach { dir ->
                if (dir != null) {
                    val path = dir.absolutePath
                    val androidIndex = path.indexOf("/Android")
                    if (androidIndex > 0) {
                        val rootPath = path.substring(0, androidIndex)
                        val rootFile = File(rootPath)
                        if (rootFile.exists() && rootFile.isDirectory) {
                            roots.add(try { rootFile.canonicalFile } catch (_: Exception) { rootFile })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving externalFilesDirs", e)
        }

        // 3. StorageManager storageVolumes (API 24+)
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageManager.storageVolumes.forEach { volume ->
                        volume.directory?.let { dir ->
                            if (dir.exists()) {
                                roots.add(try { dir.canonicalFile } catch (_: Exception) { dir })
                            }
                        }
                    }
                } else {
                    storageManager.storageVolumes.forEach { volume ->
                        try {
                            val getPath = volume.javaClass.getMethod("getPath")
                            (getPath.invoke(volume) as? String)?.let { path ->
                                val file = File(path)
                                if (file.exists()) {
                                    roots.add(try { file.canonicalFile } catch (_: Exception) { file })
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Reflection for getVolumeList (standard across automotive AOSP builds)
                try {
                    val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
                    val volumes = getVolumeList.invoke(storageManager) as? Array<*>
                    volumes?.forEach { vol ->
                        if (vol != null) {
                            try {
                                val getPath = vol.javaClass.getMethod("getPath")
                                (getPath.invoke(vol) as? String)?.let { path ->
                                    val f = File(path)
                                    if (f.exists()) {
                                        roots.add(try { f.canonicalFile } catch (_: Exception) { f })
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving StorageManager volumes", e)
        }

        // 4. Directly list /storage (DO NOT check storageDir.canRead() as it is false on standard Android 0711)
        try {
            val storageDir = File("/storage")
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.isDirectory &&
                        !file.name.equals("emulated", ignoreCase = true) &&
                        !file.name.equals("self", ignoreCase = true) &&
                        !file.name.startsWith(".")
                    ) {
                        roots.add(try { file.canonicalFile } catch (_: Exception) { file })
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error listing /storage", e)
        }

        // 5. Parse /proc/mounts (always world-readable on Linux/Android)
        try {
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                mountsFile.forEachLine { line ->
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val mountPoint = parts[1]
                        val isCandidate = mountPoint.startsWith("/storage/") ||
                                mountPoint.startsWith("/mnt/") ||
                                mountPoint.startsWith("/media/")
                        val isIgnored = mountPoint.contains("/emulated") ||
                                mountPoint.contains("/self") ||
                                mountPoint.contains("/knox") ||
                                mountPoint.contains("/asec") ||
                                mountPoint.contains("/obb") ||
                                mountPoint.contains("/secure") ||
                                mountPoint.contains("/runtime") ||
                                mountPoint.contains("/data/") ||
                                mountPoint.endsWith("/media_rw")

                        if (isCandidate && !isIgnored) {
                            val f = File(mountPoint)
                            if (f.exists() && f.isDirectory) {
                                roots.add(try { f.canonicalFile } catch (_: Exception) { f })
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing /proc/mounts", e)
        }

        // 6. Automotive known mount paths (FYT/Syu, Topway, Allwinner, Rockchip, Microntek)
        val automotivePaths = listOf(
            "/mnt/usb_storage",
            "/mnt/usb_storage1",
            "/mnt/usb_storage2",
            "/mnt/udisk",
            "/mnt/udisk0",
            "/mnt/udisk1",
            "/mnt/udisk2",
            "/mnt/usb",
            "/mnt/usb1",
            "/mnt/usb2",
            "/mnt/sdcard",
            "/mnt/extsd",
            "/mnt/external_sd",
            "/storage/usb",
            "/storage/udisk",
            "/storage/usbotg"
        )
        automotivePaths.forEach { path ->
            try {
                val f = File(path)
                if (f.exists() && f.isDirectory) {
                    roots.add(try { f.canonicalFile } catch (_: Exception) { f })
                    // Also check subdirectories of mount point if it's a hub/controller folder
                    f.listFiles()?.forEach { sub ->
                        if (sub.isDirectory && !sub.name.startsWith(".")) {
                            roots.add(try { sub.canonicalFile } catch (_: Exception) { sub })
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 7. Deduplicate redundant mount aliases
        // If a drive is mounted at both /storage/XXXX-XXXX and /mnt/media_rw/XXXX-XXXX,
        // prefer /storage/XXXX-XXXX (which has proper user permissions).
        val filtered = mutableListOf<File>()
        val storageVolumeNames = roots
            .filter { it.path.startsWith("/storage/") }
            .map { it.name.lowercase(Locale.ROOT) }
            .toSet()

        for (root in roots) {
            val path = root.path
            if (path.startsWith("/mnt/media_rw/")) {
                val name = root.name.lowercase(Locale.ROOT)
                if (storageVolumeNames.contains(name)) {
                    continue // Skip /mnt/media_rw if /storage exists for same volume
                }
            }
            if (!filtered.any { it.path == root.path }) {
                filtered.add(root)
            }
        }

        Log.d(TAG, "Resolved ${filtered.size} storage roots: ${filtered.map { it.path }}")
        return filtered
    }

    private fun scanPhysicalStorage(tracksMap: MutableMap<String, TrackEntity>) {
        val searchRoots = getStorageRoots()

        // Reuse a single MediaMetadataRetriever instance across all physical files for massive speedup
        val retriever = MediaMetadataRetriever()
        try {
            for (root in searchRoots) {
                scanDirectoryRecursively(root, tracksMap, retriever, 0)
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
        retriever: MediaMetadataRetriever,
        depth: Int = 0
    ) {
        if (depth > 15) return
        if (!dir.exists()) return

        // Skip system/hidden folders
        val dirName = dir.name
        if (dirName.startsWith(".") ||
            dirName.equals("Android", ignoreCase = true) ||
            dirName.equals("lost.dir", ignoreCase = true) ||
            dirName.equals("System Volume Information", ignoreCase = true) ||
            dirName.equals("\$RECYCLE.BIN", ignoreCase = true)
        ) {
            return
        }

        // Directly call listFiles without dir.canRead() check (which returns false on Android FAT32/exFAT mounts)
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list files in ${dir.absolutePath}: ${e.message}")
            null
        } ?: return

        for (file in files) {
            try {
                if (file.isDirectory) {
                    scanDirectoryRecursively(file, tracksMap, retriever, depth + 1)
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
            } catch (e: Exception) {
                Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
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
