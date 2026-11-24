// MusicRepository.kt
package com.gparv.ploy

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} >= 10000"
        val sort = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val songId = c.getLong(id)
                songs += Song(
                    id = songId,
                    title = c.getString(title) ?: "Unknown",
                    artist = c.getString(artist) ?: "Unknown artist",
                    album = c.getString(album) ?: "Unknown album",
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(dur),
                    dateModified = c.getLong(mod),
                    uri = ContentUris.withAppendedId(collection, songId)
                )
            }
        }
        songs
    }
}