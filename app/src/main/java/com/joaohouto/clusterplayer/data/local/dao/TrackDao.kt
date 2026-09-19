package com.joaohouto.clusterplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joaohouto.clusterplayer.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY trackNumber ASC, title ASC")
    fun getTracksByFolder(folderPath: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY trackNumber ASC, title ASC")
    suspend fun getTracksByFolderSync(folderPath: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE folderPath = :folderPath")
    suspend fun deleteTracksByFolder(folderPath: String)

    @Query("DELETE FROM tracks")
    suspend fun clearAll()
}
