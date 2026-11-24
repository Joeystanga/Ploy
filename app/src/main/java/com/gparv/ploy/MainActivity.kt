package com.gparv.ploy

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val libraryVm: LibraryViewModel by viewModels()
    private val playerVm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { libraryVm.refresh() }

                    LaunchedEffect(Unit) {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        )
                    }

                    val songs by libraryVm.songs.collectAsStateWithLifecycle()
                    val player by playerVm.state.collectAsStateWithLifecycle()

                    Column(Modifier.fillMaxSize()) {
                        SongList(
                            songs = songs,
                            modifier = Modifier.weight(1f),
                            onSongClick = { index -> playerVm.playSongs(songs, index) }
                        )
                        MiniPlayer(player, onToggle = playerVm::togglePlay)
                    }
                }
            }
        }
    }
}

@Composable
fun SongList(songs: List<Song>, modifier: Modifier = Modifier, onSongClick: (Int) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            ListItem(
                modifier = Modifier.clickable { onSongClick(index) },
                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${song.artist} · ${formatDuration(song.durationMs)}", maxLines = 1)
                }
            )
        }
    }
}

@Composable
fun MiniPlayer(state: PlayerState, onToggle: () -> Unit) {
    if (!state.hasMedia) return
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or pause"
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}