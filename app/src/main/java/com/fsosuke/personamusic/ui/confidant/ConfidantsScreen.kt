package com.fsosuke.personamusic.ui.confidant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Confidant
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.LocalOpenArtist
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

/** Every artist you listen to as a confidant: arcana, rank and how far to the next one. */
@Composable
fun ConfidantsScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val confidants by container.repository.confidants.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Confidants")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (confidants.isEmpty()) {
            item { EmptyState("No bonds yet", "Listen to an artist and they become your confidant. Their rank grows with every play.") }
        } else {
            item {
                Text(
                    "${confidants.size} confidants · ${confidants.count { it.isMax }} at MAX",
                    color = LocalPersonaColors.current.muted,
                    style = PersonaType.caption,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        itemsIndexed(confidants, key = { _, c -> c.artist }) { index, confidant ->
            ConfidantRow(confidant, index)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ConfidantRow(confidant: Confidant, index: Int) {
    val colors = LocalPersonaColors.current
    val openArtist = LocalOpenArtist.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openArtist(confidant.artist, null) }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .rotate(if (index % 2 == 0) -0.8f else 0.8f)
            .background(Color.White, slantShape(0.02f))
            .padding(3.dp)
            .background(Color.Black, slantShape(0.02f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcanaCard(confidant.arcana, Modifier.width(56.dp), textScale = 0.6f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(confidant.artist, color = Color.White, style = PersonaType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${confidant.arcana.title} · " + if (confidant.isMax) "RANK MAX" else "Rank ${confidant.rank}",
                color = colors.accent,
                style = PersonaType.label.copy(fontSize = 13.sp),
            )
            RankPips(confidant.rank, pip = 7.dp)
            if (!confidant.isMax) {
                LinearProgressIndicator(
                    progress = { confidant.progress },
                    color = colors.accent,
                    trackColor = Color(0xFF333333),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Text(
                    "${confidant.plays} plays · ${confidant.playsToNext} more to rank ${confidant.rank + 1}",
                    color = colors.muted,
                    style = PersonaType.caption.copy(fontSize = 11.sp),
                )
            } else {
                Text("${confidant.plays} plays · bond complete", color = colors.muted, style = PersonaType.caption.copy(fontSize = 11.sp))
            }
        }
    }
}
