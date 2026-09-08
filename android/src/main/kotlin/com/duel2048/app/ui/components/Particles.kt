package com.duel2048.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight particle + shock-ring system drawn on a Canvas (design borrowed from
 * 2048_NEON's board particle layer, reimplemented for Compose).
 * Velocities are in dp/second and scaled by [density].
 */
class ParticleSystem(seed: Int = 1) {

    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val decay: Float, val size: Float, val color: Color,
        val gravity: Float, val rect: Boolean, var rot: Float, val rotV: Float,
    )

    private class R(val x: Float, val y: Float, var radius: Float, val maxRadius: Float, var alpha: Float, val color: Color)

    private val rnd = Random(seed)
    private val particles = ArrayList<P>()
    private val rings = ArrayList<R>()

    var density: Float = 2f

    /** Bumped every animated frame; Canvas reads it to redraw. */
    var frame by mutableLongStateOf(0L)
        private set

    val isActive: Boolean get() = particles.isNotEmpty() || rings.isNotEmpty()

    fun burst(x: Float, y: Float, color: Color, intensity: Float = 1f, ring: Boolean = true) {
        val d = density
        val count = (14 * intensity).roundToInt().coerceIn(6, 70)
        if (ring) rings += R(x, y, 4f * d, (26f * intensity + 14f) * d, 0.9f, color)
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = (rnd.nextFloat() * 150f + 70f) * (if (intensity > 1f) 1.35f else 1f) * d
            particles += P(
                x, y, cos(a) * speed, sin(a) * speed,
                1f, rnd.nextFloat() * 1.7f + 1.5f, (rnd.nextFloat() * 2.4f + 1.6f) * d, color,
                0f, false, 0f, 0f,
            )
        }
    }

    fun confetti(width: Float, height: Float, colors: List<Color>, count: Int = 110) {
        val d = density
        repeat(count) {
            particles += P(
                rnd.nextFloat() * width, -rnd.nextFloat() * height * 0.4f,
                (rnd.nextFloat() - 0.5f) * 90f * d, (rnd.nextFloat() * 120f + 60f) * d,
                1f, 0.16f + rnd.nextFloat() * 0.12f, (rnd.nextFloat() * 5f + 4f) * d, colors[rnd.nextInt(colors.size)],
                260f * d, true, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 720f,
            )
        }
    }

    fun step(dt: Float) {
        val ri = rings.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            r.radius += 110f * density * dt
            r.alpha -= 2.4f * dt
            if (r.alpha <= 0f || r.radius >= r.maxRadius) ri.remove()
        }
        val friction = 0.92f.pow(dt * 60f)
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += p.gravity * dt
            p.vx *= friction
            if (!p.rect) p.vy *= friction
            p.rot += p.rotV * dt
            p.life -= p.decay * dt
            if (p.life <= 0f) pi.remove()
        }
    }

    fun tick(now: Long) {
        frame = now
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (r in rings) {
            drawCircle(r.color.copy(alpha = r.alpha.coerceIn(0f, 1f)), radius = r.radius, center = Offset(r.x, r.y), style = Stroke(width = 2.dp.toPx()))
        }
        for (p in particles) {
            val a = p.life.coerceIn(0f, 1f)
            if (p.rect) {
                rotate(p.rot, pivot = Offset(p.x, p.y)) {
                    drawRect(p.color.copy(alpha = a), topLeft = Offset(p.x - p.size / 2f, p.y - p.size / 4f), size = Size(p.size, p.size / 2f))
                }
            } else {
                drawCircle(p.color.copy(alpha = a), radius = p.size * (0.4f + 0.6f * a), center = Offset(p.x, p.y))
            }
        }
    }
}

@Composable
fun ParticleLayer(system: ParticleSystem, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    SideEffect { system.density = density }
    LaunchedEffect(system) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && system.isActive) {
                    system.step(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f))
                    system.tick(now)
                }
                last = now
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = system.frame
        system.draw(this)
    }
}
