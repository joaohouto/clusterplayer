package com.joaohouto.clusterplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joaohouto.clusterplayer.data.model.Track

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["folderPath"]),
        Index(value = ["uri"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderPath: String,
    val trackNumber: Int = 0
) {
    fun toDomain(): Track = Track(
        id = id,
        uri = uri,
        path = path,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        folderPath = folderPath,
        trackNumber = trackNumber
    )
}
