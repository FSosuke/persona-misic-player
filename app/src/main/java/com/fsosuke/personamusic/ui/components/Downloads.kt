package com.fsosuke.personamusic.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors

/**
 * Returns a "download these songs" action. The first time it asks for a download folder
 * (the system folder picker can also create a new one), then queues the songs.
 */
@Composable
fun rememberDownloadAction(): (List<Song>) -> Unit {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Song>>(emptyList()) }

    fun start(songs: List<Song>) {
        val queued = container.downloads.enqueue(songs)
        val message = when {
            queued == 0 && songs.none { it.source == SongSource.YOUTUBE } -> "Only YouTube songs can be downloaded"
            queued == 0 -> "Already downloaded"
            queued == 1 -> "Downloading \"${songs.first { it.source == SongSource.YOUTUBE }.title}\"…"
            else -> "Downloading $queued songs…"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            container.downloads.setFolder(uri)
            start(pending)
        }
        pending = emptyList()
    }

    return { songs ->
        if (container.settings.downloadFolder.value == null) {
            pending = songs
            Toast.makeText(context, "Pick (or create) a folder for your downloads", Toast.LENGTH_LONG).show()
            picker.launch(null)
        } else {
            start(songs)
        }
    }
}

/** Small marker next to a song: spinner while downloading, check when saved. */
@Composable
fun DownloadBadge(song: Song, modifier: Modifier = Modifier) {
    if (song.source != SongSource.YOUTUBE) return
    val downloads = LocalAppContainer.current.downloads
    val saved by downloads.downloaded.collectAsStateWithLifecycle()
    val tasks by downloads.tasks.collectAsStateWithLifecycle()
    val colors = LocalPersonaColors.current
    val task = tasks[song.id]
    when {
        song.id in saved -> Icon(Icons.Filled.DownloadDone, "Downloaded", tint = colors.accent, modifier = modifier.size(18.dp))
        task != null && task.error == null -> {
            val progress = task.progress
            if (progress == null) {
                CircularProgressIndicator(modifier = modifier.size(16.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(progress = { progress }, modifier = modifier.size(16.dp), color = colors.accent, strokeWidth = 2.dp)
            }
        }
    }
}
