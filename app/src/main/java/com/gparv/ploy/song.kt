// Song.kt
package com.gparv.ploy

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateModified: Long,
    val uri: Uri
)