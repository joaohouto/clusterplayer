package com.joaohouto.clusterplayer.data.repository

import android.content.Context
import android.util.Log
import com.joaohouto.clusterplayer.data.local.AppDatabase
import com.joaohouto.clusterplayer.data.local.entity.FolderEntity
import com.joaohouto.clusterplayer.data.local.entity.TrackEntity
import com.joaohouto.clusterplayer.data.model.Folder
import com.joaohouto.clusterplayer.data.model.Track
import com.joaohouto.clusterplayer.data.scanner.StorageScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import kotlinx.coroutines.withContext

class MusicRepository private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val scanner: StorageScanner
) {
    companion object {
        private const val TAG = "MusicRepository"

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val scanner = StorageScanner(context)
                val instance = MusicRepository(context.applicationContext, db, scanner)
                INSTANCE = instance
                instance
            }
        }
    }

    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val allFolders: Flow<List<Folder>> = database.folderDao()
        .getAllFolders()
        .map { list -> list.map { it.toDomain() } }

    val allTracks: Flow<List<Track>> = database.trackDao()
        .getAllTracks()
        .map { list -> list.map { it.toDomain() } }

    fun getTracksForFolder(folderPath: String): Flow<List<Track>> {
        return database.trackDao()
            .getTracksByFolder(folderPath)
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun getTracksForFolderSync(folderPath: String): List<Track> = withContext(Dispatchers.IO) {
        database.trackDao()
            .getTracksByFolderSync(folderPath)
            .map { it.toDomain() }
    }

    suspend fun getTrackByUri(uri: String): Track? = withContext(Dispatchers.IO) {
        database.trackDao().getTrackByUri(uri)?.toDomain()
    }

    suspend fun getTrackByPath(path: String): Track? = withContext(Dispatchers.IO) {
        database.trackDao().getTrackByPath(path)?.toDomain()
    }

    fun triggerScan(clearOld: Boolean = false, onComplete: (() -> Unit)? = null) {
        scanScope.launch {
            scanMutex.withLock {
                _isScanning.value = true
                try {
                    Log.d(TAG, "Starting media scan (clearOld=$clearOld)...")
                    val result = scanner.scanStorage()
                    Log.d(TAG, "Found ${result.folders.size} folders and ${result.tracks.size} tracks")

                    database.withTransaction {
                        if (clearOld) {
                            Log.d(TAG, "Clearing old records from database before inserting updated list")
                            database.trackDao().clearAll()
                            database.folderDao().clearAll()
                        }

                        // Insert folders
                        database.folderDao().insertFolders(result.folders)

                        // Insert tracks in chunks of 200 to prevent SQLite statement limits and UI jank
                        result.tracks.chunked(200).forEach { chunk ->
                            database.trackDao().insertTracks(chunk)
                        }
                    }

                    Log.d(TAG, "Scan completed and synced to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during media scan", e)
                } finally {
                    _isScanning.value = false
                    onComplete?.invoke()
                }
            }
        }
    }

    suspend fun deleteFolderAndTracks(folderPath: String) = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                database.trackDao().deleteTracksByFolder(folderPath)
                database.folderDao().deleteFolder(folderPath)
            }
            Log.d(TAG, "Deleted missing folder and tracks for: $folderPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting missing folder and tracks", e)
        }
    }
}
