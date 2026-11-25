package com.gparv.ploy

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    // Position is separate so ticking 4x/sec doesn't recompose the whole UI
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private var ticker: Job? = null

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
            mediaId = p.currentMediaItem?.mediaId,
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            isPlaying = p.isPlaying,
            hasMedia = p.currentMediaItem != null,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode
        )
        if (p.isPlaying) {
            if (ticker?.isActive != true) startTicker()
        } else {
            ticker?.cancel()
            _position.value = p.currentPosition
        }
    }

    private fun startTicker() {
        ticker = viewModelScope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(250)
            }
        }
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

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _position.value = ms
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }   // restarts track if >3s in, like most players

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        controller?.release()
    }
}