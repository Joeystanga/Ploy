package com.gparv.ploy

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val dark: Color
) {
    companion object {
        val Default = ArtPalette(
            dominant = Color(0xFF2A2A2E),
            vibrant = Color(0xFF9BB1FF),
            muted = Color(0xFF3A3A40),
            dark = Color(0xFF121212)
        )
    }
}

private fun Palette.toArtPalette(): ArtPalette {
    fun c(rgb: Int?) = rgb?.let { Color(it) }
    val dominant = c(dominantSwatch?.rgb) ?: ArtPalette.Default.dominant
    return ArtPalette(
        dominant = dominant,
        vibrant = c(vibrantSwatch?.rgb) ?: c(lightVibrantSwatch?.rgb) ?: dominant,
        muted = c(mutedSwatch?.rgb) ?: dominant,
        dark = c(darkMutedSwatch?.rgb) ?: c(darkVibrantSwatch?.rgb) ?: ArtPalette.Default.dark
    )
}

object ArtworkLoader {
    private val bitmaps = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val palettes = LruCache<Long, ArtPalette>(200)

    suspend fun load(context: Context, songId: Long, sizePx: Int): Bitmap? {
        val key = "$songId:$sizePx"
        bitmaps.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId
                )
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } catch (_: Exception) {
                null   // no embedded or folder art for this track
            }
        }
        if (bmp != null) bitmaps.put(key, bmp)
        return bmp
    }

    suspend fun palette(context: Context, songId: Long): ArtPalette {
        palettes.get(songId)?.let { return it }
        val bmp = load(context, songId, 256) ?: return ArtPalette.Default
        val result = withContext(Dispatchers.Default) {
            Palette.from(bmp).maximumColorCount(16).generate().toArtPalette()
        }
        palettes.put(songId, result)
        return result
    }
}

@Composable
fun ArtworkImage(
    songId: Long?,
    sizePx: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, songId, sizePx) {
        value = if (songId == null) null else ArtworkLoader.load(context, songId, sizePx)
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun rememberArtPalette(songId: Long?): ArtPalette {
    val context = LocalContext.current.applicationContext
    val result by produceState(ArtPalette.Default, songId) {
        value = if (songId == null) ArtPalette.Default else ArtworkLoader.palette(context, songId)
    }
    return result
}

@Composable
fun rememberArtBitmap(songId: Long?, sizePx: Int): Bitmap? {
    val context = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(null, songId, sizePx) {
        value = if (songId == null) null else ArtworkLoader.load(context, songId, sizePx)
    }
    return bmp
}

@Immutable
data class ArtColors(val bg: Color, val accent: Color, val text: Color) {
    val onAccent: Color get() = bg
    val textMuted: Color get() = text.copy(alpha = 0.62f)
    val soft: Color get() = text.copy(alpha = 0.10f)
    val border: Color
        get() = if (bg.luminance() > 0.5f) Color.Black.copy(alpha = 0.25f)
        else text.copy(alpha = 0.20f)
}

@Composable
fun rememberArtColors(songId: Long?): ArtColors {
    val p = rememberArtPalette(songId)
    val hsl = remember(p) {
        // take whichever of dominant/muted is more colourful so a grey-ish dominant doesn't flatten the theme
        val a = FloatArray(3)
        val b = FloatArray(3)
        ColorUtils.colorToHSL(p.dominant.toArgb(), a)
        ColorUtils.colorToHSL(p.muted.toArgb(), b)
        if (b[1] > a[1]) b else a
    }
    val sat = hsl[1].coerceIn(0.18f, 0.65f)
    fun tone(s: Float, l: Float) =
        Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], s.coerceIn(0f, 1f), l)))

    val bg by animateColorAsState(tone(sat * 0.75f, 0.17f), tween(900), label = "bg")
    val accent by animateColorAsState(tone(sat * 0.8f, 0.87f), tween(900), label = "accent")
    val text by animateColorAsState(tone(sat * 0.5f, 0.94f), tween(900), label = "text")
    return ArtColors(bg, accent, text)
}
