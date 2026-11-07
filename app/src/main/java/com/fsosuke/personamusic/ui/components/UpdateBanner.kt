package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.update.UpdateState

/** "NEW MISSION" calling card shown on Home when a newer release is out. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updates = LocalAppContainer.current.updates
    val context = LocalContext.current
    val colors = LocalPersonaColors.current
    val state by updates.state.collectAsStateWithLifecycle()

    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Ready -> s.update
        else -> return
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .rotate(-1.5f)
            .background(Color.White, slantShape(0.02f))
            .padding(4.dp)
            .background(Color.Black, slantShape(0.02f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RansomText("New mission", fontSize = 22.sp)
        Text("Version ${update.version} is ready to infiltrate.", color = Color.White, style = PersonaType.body)
        if (update.notes.isNotBlank()) {
            Text(update.notes.trim(), color = colors.muted, style = PersonaType.caption, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        when (val s = state) {
            is UpdateState.Downloading -> LinearProgressIndicator(
                progress = { s.progress },
                color = colors.accent,
                trackColor = Color(0xFF333333),
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            is UpdateState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaButton("Install", onClick = { updates.install(context, s.file) })
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonaButton("Update", onClick = { updates.download(update) })
                Box(Modifier.clickable { updates.dismiss(update) }.padding(8.dp)) {
                    Text("LATER", color = Color(0xFFAAAAAA), style = PersonaType.label)
                }
            }
        }
    }
}
