package com.gparv.ploy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    positionFlow: StateFlow<Long>,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    val duration = state.durationMs.coerceAtLeast(1L)

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else position.toFloat().coerceIn(0f, duration.toFloat())

    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
        }

        Spacer(Modifier.height(8.dp))

        ArtworkImage(
            songId = state.mediaId?.toLongOrNull(),
            sizePx = 1024,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(16.dp)
        )


        Spacer(Modifier.height(32.dp))

        Text(
            state.title ?: "",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(state.artist, state.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = inactive,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(16.dp))

        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
            valueRange = 0f..duration.toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong()), style = MaterialTheme.typography.labelMedium, color = inactive)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = inactive)
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) active else inactive)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play or pause", modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onRepeat) {
                val off = state.repeatMode == Player.REPEAT_MODE_OFF
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat", tint = if (off) inactive else active
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}