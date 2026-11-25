@file:OptIn(ExperimentalMaterial3Api::class)

package com.gparv.ploy

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private val MonoFont = FontFamily.Monospace

@Composable
fun EditorialNowPlaying(
    state: PlayerState,
    positionFlow: StateFlow<Long>,
    actions: NowPlayingActions
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    val songId = state.mediaId?.toLongOrNull()
    val colors = rememberArtColors(songId)
    val volume = rememberSystemVolume()

    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else position.toFloat().coerceIn(0f, duration.toFloat())

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top bar (chip, cast and overflow are visual only until their sheets exist)
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onClose) {
                Icon(Icons.Filled.ChevronLeft, "Close", tint = colors.text)
            }
            Spacer(Modifier.weight(1f))
            OutlinePill(colors.border, onClick = {}) {
                Icon(Icons.Filled.Speaker, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Text("Phone speaker", color = colors.text, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Filled.Cast, "Cast", tint = colors.text) }
            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, "More", tint = colors.text) }
        }

        ArtworkImage(
            songId = songId,
            sizePx = 1024,
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            state.title ?: "",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            buildAnnotatedString {
                append(state.artist ?: "")
                state.album?.let {
                    append("   ·   ")
                    withStyle(SpanStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 0.5.sp)) {
                        append(it.uppercase())
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        ThinSlider(
            value = shown,
            range = 0f..duration.toFloat(),
            onChange = { dragging = true; dragValue = it },
            onFinished = { actions.onSeek(dragValue.toLong()); dragging = false },
            modifier = Modifier.height(36.dp),
            active = colors.text,
            inactive = colors.text.copy(alpha = 0.30f)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong()), fontFamily = MonoFont, fontSize = 12.sp, color = colors.text)
            Text(formatDuration(state.durationMs), fontFamily = MonoFont, fontSize = 12.sp, color = colors.textMuted)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransportButton(
                Icons.Filled.SkipPrevious, "Previous", actions.onPrevious,
                container = colors.soft, tint = colors.text, borderColor = colors.border,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp)
            )
            TransportButton(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Play or pause", actions.onToggle,
                container = colors.accent, tint = colors.onAccent, borderColor = Color.Transparent,
                modifier = Modifier.weight(1.25f), shape = RoundedCornerShape(32.dp)
            )
            TransportButton(
                Icons.Filled.SkipNext, "Next", actions.onNext,
                container = colors.soft, tint = colors.text, borderColor = colors.border,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.soft)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThinSlider(
                value = volume.fraction,
                range = 0f..1f,
                onChange = volume.onChange,
                onFinished = {},
                modifier = Modifier.weight(1f).height(36.dp),
                active = colors.text,
                inactive = colors.text.copy(alpha = 0.30f)
            )
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = colors.text,
                modifier = Modifier.padding(start = 8.dp).size(22.dp)
            )
            Box(
                Modifier.padding(horizontal = 10.dp).width(1.dp).height(32.dp)
                    .background(colors.text.copy(alpha = 0.15f))
            )
            SmallToggle(colors, Icons.Filled.Shuffle, "Shuffle", state.shuffle, actions.onShuffle)
            SmallToggle(
                colors,
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Repeat", state.repeatMode != Player.REPEAT_MODE_OFF, actions.onRepeat
            )
            SmallToggle(colors, Icons.Filled.FavoriteBorder, "Favourite", false) {}   // wired up with the database
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinePill(colors.border, onClick = {}) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Text("Playing queue", color = colors.text, fontSize = 14.sp)
            }
            OutlinePill(colors.border, onClick = {}) {
                Icon(Icons.Filled.Lyrics, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Text("Lyrics", color = colors.text, fontSize = 14.sp)
            }
            OutlinePill(colors.border, onClick = {}) {
                Icon(Icons.Filled.Bedtime, null, tint = colors.text, modifier = Modifier.size(18.dp))
                Text("Sleep timer", color = colors.text, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ThinSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    active: Color = Color.White,
    inactive: Color = Color.White.copy(alpha = 0.30f)
) {
    Slider(
        value = value,
        onValueChange = onChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
        modifier = modifier,
        thumb = {
            Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(active))
        },
        track = { s ->
            val span = s.valueRange.endInclusive - s.valueRange.start
            val frac = if (span > 0f) ((s.value - s.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(inactive)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(active))
            }
        }
    )
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    container: Color,
    tint: Color,
    borderColor: Color,
    modifier: Modifier,
    shape: Shape
) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun SmallToggle(
    colors: ArtColors,
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(44.dp)
            .clip(shape)
            .background(if (active) colors.text.copy(alpha = 0.28f) else Color.Transparent)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = colors.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun OutlinePill(
    borderColor: Color,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

class SystemVolume(val fraction: Float, val onChange: (Float) -> Unit)

@Composable
fun rememberSystemVolume(): SystemVolume {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var current by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    return SystemVolume(current / max.toFloat()) { f ->
        val v = (f * max).roundToInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        current = v
    }
}