package com.duel2048.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.duel2048.app.ui.theme.DuelPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Orb(val x: Float, val y: Float, val r: Float, val ax: Float, val ay: Float, val speed: Float, val phase: Float, val color: Color)
private class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

/** Living background: gradient, faint grid, drifting glow orbs and twinkling stars. */
@Composable
fun AmbientBackground(palette: DuelPalette, modifier: Modifier = Modifier.fillMaxSize(), animated: Boolean = true) {
    val orbs = remember(palette.id) {
        val rnd = Random(palette.id.hashCode())
        List(11) {
            Orb(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                r = 0.16f + rnd.nextFloat() * 0.22f,
                ax = 0.03f + rnd.nextFloat() * 0.07f, ay = 0.03f + rnd.nextFloat() * 0.07f,
                speed = 0.12f + rnd.nextFloat() * 0.22f, phase = rnd.nextFloat() * 6.28f,
                color = palette.orbColors[it % palette.orbColors.size],
            )
        }
    }
    val stars = remember(palette.id) {
        val rnd = Random(palette.id.hashCode() + 99)
        List(46) { Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 6.28f) }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(palette.id, animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)))
        val step = 44.dp.toPx()
        val gridColor = Color.White.copy(alpha = 0.035f)
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        if (!animated) return@Canvas
        for (o in orbs) {
            val cx = (o.x + sin(t * o.speed + o.phase) * o.ax) * size.width
            val cy = (o.y + cos(t * o.speed * 0.8f + o.phase) * o.ay) * size.height
            val r = o.r * size.minDimension
            drawCircle(
                Brush.radialGradient(listOf(o.color.copy(alpha = 0.30f), o.color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        for (s in stars) {
            val a = 0.2f + 0.4f * (0.5f + 0.5f * sin(t * 1.6f + s.phase))
            drawCircle(Color.White.copy(alpha = a), radius = s.size * density, center = Offset(s.x * size.width, s.y * size.height))
        }
    }
}
