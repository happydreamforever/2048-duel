package com.duel2048.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duel2048.app.game.TileKind
import com.duel2048.app.game.TileUi
import com.duel2048.app.ui.theme.DuelPalette
import com.duel2048.shared.engine.Cell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SLIDE_MS = 120

/**
 * One animated tile. Slides with a tween, merged tiles pop with an overshoot and a glow
 * flash, spawned tiles spring in, garbage slams down (LANDED).
 */
@Composable
fun TileView(tile: TileUi, tileDp: Dp, stepPx: Float, originPx: Float, palette: DuelPalette, showValue: Boolean, glowEnabled: Boolean = true) {
    fun px(cell: Cell) = Offset(originPx + cell.col * stepPx, originPx + cell.row * stepPx)

    val offset = remember { Animatable(px(tile.from), Offset.VectorConverter) }
    val scale = remember { Animatable(if (tile.kind == TileKind.SPAWN || tile.kind == TileKind.MERGED) 0f else 1f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(tile.version, tile.kind, tile.to, tile.from, stepPx) {
        val target = px(tile.to)
        when (tile.kind) {
            TileKind.SLIDE -> {
                offset.snapTo(px(tile.from))
                if (scale.value != 1f) scale.snapTo(1f)
                offset.animateTo(target, tween(SLIDE_MS, easing = FastOutSlowInEasing))
            }
            TileKind.STATIC -> {
                offset.snapTo(target)
                if (scale.value != 1f) scale.snapTo(1f)
            }
            TileKind.MERGED -> {
                offset.snapTo(target)
                scale.snapTo(0f)
                glow.snapTo(1f)
                delay(SLIDE_MS - 25L)
                launch { glow.animateTo(0f, tween(520)) }
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 240
                        0f at 0
                        1.24f at 120
                        1f at 240
                    },
                )
            }
            TileKind.SPAWN -> {
                offset.snapTo(target)
                scale.snapTo(0f)
                delay(SLIDE_MS.toLong())
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            TileKind.LANDED -> {
                offset.snapTo(target)
                scale.snapTo(1.75f)
                glow.snapTo(1f)
                launch { glow.animateTo(0f, tween(650)) }
                scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 650f))
            }
        }
    }

    val style = if (tile.isGarbage) null else palette.tileStyle(tile.value)
    val glowColor = if (tile.isGarbage) palette.danger else style!!.end
    val big = tile.value >= 128
    val text = if (tile.isGarbage) "" else tile.value.toString()
    val fontSize = (tileDp.value * when (text.length) {
        0, 1, 2 -> 0.46f
        3 -> 0.40f
        4 -> 0.32f
        else -> 0.26f
    }).sp

    Box(
        Modifier
            .size(tileDp)
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawBehind {
                val r = size.width * 0.17f
                val g = glow.value
                val baseGlow = if (tile.isGarbage) 0.22f else if (big) 0.24f else 0.09f
                val a = (baseGlow + 0.75f * g).coerceAtMost(1f)
                for (i in 1..(if (glowEnabled) 3 else 0)) {
                    val pad = i * size.width * 0.06f
                    drawRoundRect(
                        glowColor.copy(alpha = a / (i * 2.2f)),
                        topLeft = Offset(-pad, -pad),
                        size = Size(size.width + 2 * pad, size.height + 2 * pad),
                        cornerRadius = CornerRadius(r + pad),
                    )
                }
                if (tile.isGarbage) {
                    drawRoundRect(
                        Brush.linearGradient(listOf(palette.garbageStart, palette.garbageEnd), start = Offset.Zero, end = Offset(size.width, size.height)),
                        cornerRadius = CornerRadius(r),
                    )
                    drawCracks(tile, palette)
                } else {
                    drawRoundRect(
                        Brush.linearGradient(listOf(style!!.start, style.end), start = Offset.Zero, end = Offset(size.width, size.height)),
                        cornerRadius = CornerRadius(r),
                    )
                }
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.55f),
                    cornerRadius = CornerRadius(r),
                )
                if (g > 0f) drawRoundRect(Color.White.copy(alpha = 0.55f * g), cornerRadius = CornerRadius(r))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showValue && !tile.isGarbage) {
            Text(
                text,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = style!!.text,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 4f)),
            )
        }
        if (tile.isGarbage) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = tileDp * 0.1f)) {
                repeat(tile.garbageHp) { i ->
                    if (i > 0) Spacer(Modifier.width(tileDp * 0.06f))
                    Box(Modifier.size(tileDp * 0.1f).background(palette.danger, CircleShape))
                }
            }
        }
    }
}

private fun DrawScope.drawCracks(tile: TileUi, palette: DuelPalette) {
    val rnd = Random(tile.id * 31 + 7)
    val lines = if (tile.garbageHp >= 2) 4 else 8
    val cx = size.width / 2f
    val cy = size.height / 2f
    repeat(lines) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val len = size.width * (0.22f + rnd.nextFloat() * 0.26f)
        val sx = cx + (rnd.nextFloat() - 0.5f) * size.width * 0.35f
        val sy = cy + (rnd.nextFloat() - 0.5f) * size.height * 0.35f
        drawLine(
            palette.garbageCrack.copy(alpha = 0.85f),
            Offset(sx, sy),
            Offset(sx + cos(a) * len, sy + sin(a) * len),
            strokeWidth = size.width * 0.035f,
            cap = StrokeCap.Round,
        )
    }
}
