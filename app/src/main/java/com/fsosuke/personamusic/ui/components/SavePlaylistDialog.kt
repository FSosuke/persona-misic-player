package com.fsosuke.personamusic.ui.components

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Save as playlist": copies [songs] into a new local playlist. */
@Composable
fun SavePlaylistDialog(songs: List<Song>, suggestedName: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        shape = RectangleShape,
        modifier = Modifier.border(3.dp, Color.White),
        title = { RansomText("Save playlist", fontSize = 22.sp) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    onDismiss()
                    container.appScope.launch {
                        container.repository.createPlaylistFrom(trimmed, songs)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved \"$trimmed\" with ${songs.size} songs", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
