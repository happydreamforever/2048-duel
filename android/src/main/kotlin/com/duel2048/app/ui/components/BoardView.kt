package com.duel2048.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.duel2048.app.game.BoardUi
import com.duel2048.app.ui.input.swipeInput
import com.duel2048.app.ui.theme.LocalLowEffects
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.engine.Direction
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a [BoardUi]. Tiles are keyed by engine id so each one animates from its previous
 * cell (the approach used by alexjlockwood/compose-multiplatform-2048), with particles,
 * floating texts, shake and flash layered on top when a [BoardFx] is supplied.
 */
@Composable
fun BoardView(
    board: BoardUi,
    modifier: Modifier = Modifier,
    fx: BoardFx? = null,
    showValues: Boolean = true,
    dimmed: Boolean = false,
    onSwipe: ((Direction) -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val lowEffects = LocalLowEffects.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val n = board.size
        val boardDp = maxWidth
        val padding = boardDp * 0.035f
        val gap = boardDp * 0.028f
        val tileDp = (boardDp - padding * 2 - gap * (n - 1)) / n
        val stepPx = with(density) { (tileDp + gap).toPx() }
        val originPx = with(density) { padding.toPx() }
        val tilePx = with(density) { tileDp.toPx() }
        val boardPx = with(density) { boardDp.toPx() }
        val corner = boardDp * 0.07f
        if (fx != null) {
            SideEffect { fx.geometry = BoardGeometry(originPx, stepPx, tilePx, boardPx) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (fx != null) Modifier.graphicsLayer {
                        val s = fx.shake.value
                        if (s > 0f && s < 1f) {
                            val amp = fx.shakeAmp * 9.dp.toPx() * (1f - s)
                            translationX = sin(s * PI.toFloat() * 8f) * amp
                            translationY = cos(s * PI.toFloat() * 6f) * amp * 0.5f
                        }
                    } else Modifier,
                )
                .then(if (onSwipe != null) Modifier.swipeInput(onSwipe = onSwipe) else Modifier),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cr = CornerRadius(corner.toPx())
                drawRoundRect(color = palette.boardBg, cornerRadius = cr)
                drawRoundRect(color = palette.surfaceBorder, cornerRadius = cr, style = Stroke(1.dp.toPx()))
                for (r in 0 until n) {
                    for (c in 0 until n) {
                        drawRoundRect(
                            color = palette.cellBg,
                            topLeft = Offset(originPx + c * stepPx, originPx + r * stepPx),
                            size = Size(tilePx, tilePx),
                            cornerRadius = CornerRadius(tilePx * 0.17f),
                        )
                    }
                }
                if (fx != null) {
                    val f = fx.flash.value
                    if (f > 0f) drawRoundRect(color = fx.flashColor.copy(alpha = f), cornerRadius = cr)
                }
            }
            for (tile in board.tiles.sortedBy { it.z }) {
                key(tile.id) {
                    TileView(tile, tileDp, stepPx, originPx, palette, showValues, glowEnabled = !lowEffects)
                }
            }
            if (fx != null) {
                if (!lowEffects) ParticleLayer(fx.particles, Modifier.fillMaxSize())
                FloatingTexts(fx)
            }
            if (dimmed) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(corner)))
            }
        }
    }
}
