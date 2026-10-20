package com.joaohouto.clusterplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joaohouto.clusterplayer.data.local.entity.TrackLoudnessEntity

@Dao
interface TrackLoudnessDao {
    @Query("SELECT * FROM track_loudness WHERE uri = :uri LIMIT 1")
    suspend fun getLoudnessByUri(uri: String): TrackLoudnessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoudness(entity: TrackLoudnessEntity)

    @Query("SELECT uri FROM track_loudness WHERE algorithmVersion = :version")
    suspend fun getAllCachedUrisForVersion(version: Int): List<String>
}
