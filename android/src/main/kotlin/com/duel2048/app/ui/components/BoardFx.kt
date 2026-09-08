package com.duel2048.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duel2048.shared.engine.Cell
import com.duel2048.shared.engine.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class BoardGeometry(val origin: Float, val step: Float, val tile: Float, val boardSize: Float)

data class FloatingText(val id: Long, val text: String, val x: Float, val y: Float, val color: Color, val scale: Float)

/** Per-board effect state: particles, floating texts, shake and flash. */
class BoardFx(private val scope: CoroutineScope) {
    val particles = ParticleSystem()
    val texts = mutableStateListOf<FloatingText>()
    val shake = Animatable(0f)
    var shakeAmp = 1f
    val flash = Animatable(0f)
    var flashColor by mutableStateOf(Color.Transparent)
    var geometry: BoardGeometry? = null
    private var nextId = 0L

    fun cellCenter(cell: Cell): Offset? = geometry?.let { g ->
        Offset(g.origin + cell.col * g.step + g.tile / 2f, g.origin + cell.row * g.step + g.tile / 2f)
    }

    fun burstAt(cell: Cell, color: Color, intensity: Float = 1f) {
        cellCenter(cell)?.let { particles.burst(it.x, it.y, color, intensity) }
    }

    fun text(text: String, color: Color, cell: Cell? = null, scale: Float = 1f) {
        val g = geometry ?: return
        val pos = cell?.let { cellCenter(it) } ?: Offset(g.boardSize / 2f, g.boardSize / 2f)
        texts += FloatingText(nextId++, text, pos.x, pos.y, color, scale)
    }

    fun shake(strength: Float) {
        scope.launch {
            shakeAmp = strength
            shake.snapTo(0f)
            shake.animateTo(1f, tween((200 + 220 * strength).toInt(), easing = LinearEasing))
        }
    }

    fun nudge(@Suppress("UNUSED_PARAMETER") direction: Direction) = shake(0.22f)

    fun flash(color: Color) {
        scope.launch {
            flashColor = color
            flash.snapTo(0.55f)
            flash.animateTo(0f, tween(480))
        }
    }
}

@Composable
fun FloatingTexts(fx: BoardFx) {
    for (ft in fx.texts) {
        key(ft.id) { FloatingTextItem(ft) { fx.texts.remove(ft) } }
    }
}

@Composable
private fun FloatingTextItem(ft: FloatingText, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1150, easing = LinearEasing))
        onDone()
    }
    val p = progress.value
    val alpha = if (p < 0.2f) p / 0.2f else 1f - ((p - 0.2f) / 0.8f)
    val pop = if (p < 0.2f) 0.7f + 0.3f * (p / 0.2f) else 1f
    Box(
        Modifier
            .absoluteOffset { IntOffset(ft.x.roundToInt(), (ft.y - 64.dp.toPx() * p).roundToInt()) }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height / 2) }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = ft.scale * pop
                scaleY = ft.scale * pop
            },
    ) {
        Text(
            ft.text,
            color = ft.color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f)),
        )
    }
}
