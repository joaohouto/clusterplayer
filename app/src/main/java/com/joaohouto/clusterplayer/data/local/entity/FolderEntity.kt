package com.joaohouto.clusterplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.joaohouto.clusterplayer.data.model.Folder

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val path: String,
    val name: String,
    val trackCount: Int,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun toDomain(): Folder = Folder(
        path = path,
        name = name,
        trackCount = trackCount
    )
}
