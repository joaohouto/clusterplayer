package com.joaohouto.clusterplayer.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int
)

@Immutable
data class Track(
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
    val contentUri: Uri
        get() = Uri.parse(uri)
}
