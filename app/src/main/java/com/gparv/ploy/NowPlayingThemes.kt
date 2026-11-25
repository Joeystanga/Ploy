package com.gparv.ploy

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

enum class NpLayout { Default, Editorial }
enum class NpBackdrop { Flat, PaletteFlat, Blur, AnimatedBlur }

enum class NowPlayingTheme(val label: String, val layout: NpLayout, val backdrop: NpBackdrop) {
    Default("Default", NpLayout.Default, NpBackdrop.Flat),
    Editorial("Editorial", NpLayout.Editorial, NpBackdrop.PaletteFlat),
    EditorialBlur("Editorial Blur", NpLayout.Editorial, NpBackdrop.AnimatedBlur),
    Blur("Blur", NpLayout.Default, NpBackdrop.Blur),
    GradientBlur("Gradient Blur", NpLayout.Default, NpBackdrop.AnimatedBlur); // Peppers layout comes next

    fun next(): NowPlayingTheme {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

data class NowPlayingActions(
    val onSeek: (Long) -> Unit,
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onClose: () -> Unit
)

@Composable
fun NowPlayingHost(
    theme: NowPlayingTheme,
    powerSaving: Boolean,
    state: PlayerState,
    positionFlow: StateFlow<Long>,
    actions: NowPlayingActions
) {
    val songId = state.mediaId?.toLongOrNull()
    val body: @Composable BoxScope.() -> Unit = {
        when (theme.layout) {
            NpLayout.Default -> NowPlayingScreen(
                state = state,
                positionFlow = positionFlow,
                onSeek = actions.onSeek,
                onToggle = actions.onToggle,
                onNext = actions.onNext,
                onPrevious = actions.onPrevious,
                onShuffle = actions.onShuffle,
                onRepeat = actions.onRepeat,
                onClose = actions.onClose
            )
            NpLayout.Editorial -> EditorialNowPlaying(state, positionFlow, actions)
        }
    }
    val fill = Modifier.fillMaxSize()
    when (theme.backdrop) {
        NpBackdrop.Flat -> Box(fill.background(MaterialTheme.colorScheme.surface), content = body)
        NpBackdrop.PaletteFlat -> PaletteFlatBackdrop(songId, fill, body)
        NpBackdrop.Blur -> BlurBackdrop(songId, animated = false, modifier = fill, content = body)
        NpBackdrop.AnimatedBlur ->
            BlurBackdrop(songId, animated = !powerSaving, modifier = fill, content = body)
    }
}

@Composable
fun PaletteFlatBackdrop(
    songId: Long?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bg = rememberArtColors(songId).bg
    Box(modifier.background(bg), content = content)
}

@Composable
fun BlurBackdrop(
    songId: Long?,
    animated: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bitmap = rememberArtBitmap(songId, 256)
    val palette = rememberArtPalette(songId)
    val base by animateColorAsState(lerp(palette.dark, Color.Black, 0.6f), tween(900), label = "base")

    Box(modifier.background(base)) {
        Crossfade(targetState = bitmap, animationSpec = tween(1000), label = "art") { b ->
            if (b != null) BlurredArt(b, animated)
        }
        // keeps text readable over bright art
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f))
                    )
                )
        )
        content()
    }
}

private val vivid = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

@Composable
private fun BlurredArt(bitmap: Bitmap, animated: Boolean) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val rotA by drift(animated, -12f, 12f, 16_000, "rotA")
    val rotB by drift(animated, 15f, -15f, 21_000, "rotB")
    val panA by drift(animated, -90f, 90f, 19_000, "panA")
    val panB by drift(animated, 70f, -70f, 24_000, "panB")

    Box(Modifier.fillMaxSize().clipToBounds()) {
        Image(
            bitmap = image,
            contentDescription = null,
            colorFilter = vivid,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotA; translationX = panA; scaleX = 1.8f; scaleY = 1.8f }
                .blur(48.dp, BlurredEdgeTreatment.Unbounded)
        )
        Image(
            bitmap = image,
            contentDescription = null,
            colorFilter = vivid,
            contentScale = ContentScale.FillBounds,
            alpha = 0.6f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotB; translationY = panB; scaleX = -1.9f; scaleY = 1.9f }
                .blur(48.dp, BlurredEdgeTreatment.Unbounded)
        )
    }
}

@Composable
private fun drift(animated: Boolean, from: Float, to: Float, ms: Int, label: String): State<Float> {
    if (!animated) return remember { mutableFloatStateOf((from + to) / 2f) }
    val t = rememberInfiniteTransition(label = label)
    return t.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = label
    )
}