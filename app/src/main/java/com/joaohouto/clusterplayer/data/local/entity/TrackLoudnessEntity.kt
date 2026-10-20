package com.joaohouto.clusterplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_loudness")
data class TrackLoudnessEntity(
    @PrimaryKey
    val uri: String,
    val fileSize: Long,
    val lastModified: Long,
    val integratedLufs: Float,
    val truePeak: Float,
    val algorithmVersion: Int,
    val updatedAt: Long
)
