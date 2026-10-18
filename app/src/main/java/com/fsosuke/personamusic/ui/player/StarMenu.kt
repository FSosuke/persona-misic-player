package com.fsosuke.personamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.ui.components.RansomStyle
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.components.unitStarPath
import kotlinx.coroutines.delay

/** The full-screen P5 pause-menu: a giant star on the left and ransom-note entries sliding in. */
@Composable
fun StarMenu(visible: Boolean, onDismiss: () -> Unit, entries: List<Pair<String, () -> Unit>>) {
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val spin = remember { Animatable(-40f) }
            LaunchedEffect(Unit) { spin.animateTo(0f, tween(350)) }
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(Color.White, Offset(0f, h * 0.08f), Offset(w * 0.5f, h), strokeWidth = 6f)
                val star = unitStarPath(innerRatio = 0.42f)
                translate(w * 0.05f, h * 0.6f) {
                    rotate(-18f + spin.value, pivot = Offset.Zero) {
                        for (ring in 0 until 4) {
                            val r = w * 0.55f * (1f - ring * 0.2f)
                            scale(r, r, pivot = Offset.Zero) {
                                drawPath(star, if (ring % 2 == 0) Color.White else Color.Black, style = Stroke(width = 22f / r))
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.Center).padding(start = 80.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                entries.forEachIndexed { index, (label, action) ->
                    val slide = remember { Animatable(-600f) }
                    LaunchedEffect(Unit) {
                        delay(60L * index)
                        slide.animateTo(0f, tween(220))
                    }
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = slide.value + index * 18.dp.toPx()
                                rotationZ = -6f + index * 2f
                            }
                            .background(Color.Black, slantShape(0.08f))
                            .clickable(onClick = action)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        RansomText(label, fontSize = 34.sp, style = if (index % 2 == 0) RansomStyle.Outline else RansomStyle.Collage)
                    }
                }
            }
        }
    }
}
