//PlayerViewModel.kt

package com.gparv.ploy

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            sync(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        val md = p.mediaMetadata
        _state.value = PlayerState(
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            isPlaying = p.isPlaying,
            hasMedia = p.currentMediaItem != null
        )
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun onCleared() {
        controller?.release()
    }
}