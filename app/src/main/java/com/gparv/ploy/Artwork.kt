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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            } catch (e: Exception) {
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
fun PaletteBackdrop(
    songId: Long?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = rememberArtPalette(songId)
    val top by animateColorAsState(lerp(p.muted, Color.Black, 0.25f), tween(900), label = "top")
    val mid by animateColorAsState(lerp(p.dominant, Color.Black, 0.55f), tween(900), label = "mid")
    val bottom by animateColorAsState(lerp(p.dark, Color.Black, 0.7f), tween(900), label = "bottom")
    Box(modifier.background(Brush.verticalGradient(listOf(top, mid, bottom))), content = content)
}