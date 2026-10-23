package com.fsosuke.personamusic.ui.confidant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.phantomSlashes
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.delay

/** "RANK UP!" – the arcana card spins in when a confidant levels up. Tap to dismiss. */
@Composable
fun RankUpOverlay() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val rankUp by container.pendingRankUp.collectAsStateWithLifecycle()

    AnimatedVisibility(rankUp != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
        val event = rankUp ?: return@AnimatedVisibility
        val spin = remember(event) { Animatable(540f) }
        val scale = remember(event) { Animatable(0.2f) }
        LaunchedEffect(event) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        LaunchedEffect(event) {
            spin.animateTo(0f, tween(900))
            delay(5_000)
            container.pendingRankUp.value = null
        }
        Box(
            Modifier
                .fillMaxSize()
                .phantomSlashes(colors)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { container.pendingRankUp.value = null },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                RansomText(if (event.confidant.isMax) "Rank MAX" else "Rank up!", fontSize = 40.sp)
                ArcanaCard(
                    event.confidant.arcana,
                    Modifier
                        .width(170.dp)
                        .graphicsLayer {
                            rotationY = spin.value
                            scaleX = scale.value
                            scaleY = scale.value
                            cameraDistance = 12 * density
                        },
                    textScale = 1.6f,
                )
                Text(
                    event.confidant.artist,
                    color = Color.White,
                    style = PersonaType.title.copy(fontSize = 24.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Box(Modifier.background(Color.Black).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(
                        "${event.confidant.arcana.title} · Rank ${event.previousRank} → " +
                            if (event.confidant.isMax) "MAX" else "${event.confidant.rank}",
                        color = colors.accent,
                        style = PersonaType.label.copy(fontSize = 16.sp),
                    )
                }
                RankPips(event.confidant.rank, pip = 12.dp)
                Text("Your bond has deepened…", color = Color.White.copy(alpha = 0.8f), style = PersonaType.caption)
            }
        }
    }
}
