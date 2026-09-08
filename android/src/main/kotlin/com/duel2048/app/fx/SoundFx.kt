package com.duel2048.app.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class GameFx(val haptics: Haptics, val sound: SoundFx)

val LocalGameFx = staticCompositionLocalOf<GameFx> { error("GameFx not provided") }

/**
 * Procedural sound effects: no audio assets are shipped. A few short samples are
 * synthesized on first launch, written to the cache dir as WAV and played with SoundPool
 * (pitch-shifted per tile value, an idea taken from 2048_NEON's Web Audio design).
 */
class SoundFx(context: Context, private val enabled: () -> Boolean) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private val appContext = context.applicationContext

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
        Thread {
            val dir = File(appContext.cacheDir, "sfx").apply { mkdirs() }
            for ((name, samples) in Synth.library()) {
                val file = File(dir, "$name.wav")
                if (!file.exists()) file.writeBytes(Synth.toWav(samples))
                val id = pool.load(file.absolutePath, 1)
                synchronized(ids) { ids[name] = id }
            }
        }.start()
    }

    fun merge(value: Int) {
        val level = if (value <= 0) 1 else (31 - Integer.numberOfLeadingZeros(value))
        play("merge", rate = (0.85f + 0.07f * level).coerceIn(0.6f, 2f), volume = 0.45f)
    }

    fun combo(count: Int) = play("combo", rate = (1f + 0.08f * (count - 2)).coerceIn(0.8f, 1.6f), volume = 0.6f)
    fun attack() = play("attack", volume = 0.7f)
    fun hit() = play("hit", volume = 0.9f)
    fun shatter() = play("shatter", volume = 0.6f)
    fun tick() = play("tick", volume = 0.5f)
    fun go() = play("go", volume = 0.8f)
    fun win() = play("win", volume = 0.9f)
    fun lose() = play("lose", volume = 0.8f)
    fun bump() = play("tick", rate = 0.6f, volume = 0.35f)

    private fun play(name: String, rate: Float = 1f, volume: Float = 0.6f) {
        if (!enabled()) return
        val id = synchronized(ids) { ids[name] } ?: return
        if (synchronized(loaded) { id !in loaded }) return
        pool.play(id, volume, volume, 1, 0, rate)
    }

    fun release() = pool.release()
}

/** Very small additive synthesizer producing 16-bit mono PCM. */
object Synth {
    const val RATE = 22050

    enum class Wave { SINE, TRIANGLE, SQUARE, NOISE }

    /** One voice: frequency sweeps linearly from [f0] to [f1] between [start] and [start]+[length] seconds. */
    data class Voice(val f0: Double, val f1: Double, val start: Double, val length: Double, val wave: Wave = Wave.SINE, val gain: Double = 0.5, val decay: Double = 6.0)

    fun render(duration: Double, voices: List<Voice>): ShortArray {
        val n = (duration * RATE).toInt()
        val out = DoubleArray(n)
        val rnd = Random(7)
        for (v in voices) {
            val s0 = (v.start * RATE).toInt()
            val len = (v.length * RATE).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toDouble() / len
                val f = v.f0 + (v.f1 - v.f0) * t
                phase += 2 * PI * f / RATE
                val env = attack(i) * exp(-v.decay * t)
                val sample = when (v.wave) {
                    Wave.SINE -> sin(phase)
                    Wave.TRIANGLE -> 2.0 / PI * kotlin.math.asin(sin(phase))
                    Wave.SQUARE -> if (sin(phase) >= 0) 0.6 else -0.6
                    Wave.NOISE -> rnd.nextDouble(-1.0, 1.0)
                }
                out[idx] += sample * env * v.gain
            }
        }
        val peak = out.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 1e-6 } ?: 1.0
        val norm = if (peak > 0.95) 0.95 / peak else 1.0
        return ShortArray(n) { (out[it] * norm * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun attack(i: Int): Double = if (i < 110) i / 110.0 else 1.0

    private fun note(semitonesFromA4: Int): Double = 440.0 * 2.0.pow(semitonesFromA4 / 12.0)

    fun library(): Map<String, ShortArray> = mapOf(
        "merge" to render(0.18, listOf(Voice(440.0, 660.0, 0.0, 0.16, Wave.TRIANGLE, 0.6, 7.0), Voice(880.0, 1320.0, 0.0, 0.10, Wave.SINE, 0.15, 12.0))),
        "combo" to render(0.45, listOf(
            Voice(note(0), note(0), 0.00, 0.18, Wave.TRIANGLE, 0.35, 6.0),
            Voice(note(4), note(4), 0.05, 0.18, Wave.SQUARE, 0.25, 6.0),
            Voice(note(7), note(7), 0.10, 0.20, Wave.TRIANGLE, 0.35, 6.0),
            Voice(note(12), note(12), 0.15, 0.28, Wave.SINE, 0.4, 5.0),
            Voice(140.0, 45.0, 0.0, 0.22, Wave.SINE, 0.5, 5.0),
        )),
        "attack" to render(0.35, listOf(Voice(1200.0, 180.0, 0.0, 0.30, Wave.SQUARE, 0.35, 5.0), Voice(300.0, 90.0, 0.0, 0.30, Wave.SINE, 0.5, 4.0))),
        "hit" to render(0.35, listOf(Voice(130.0, 38.0, 0.0, 0.30, Wave.SINE, 0.9, 6.0), Voice(0.0, 0.0, 0.0, 0.08, Wave.NOISE, 0.35, 20.0))),
        "shatter" to render(0.25, listOf(Voice(0.0, 0.0, 0.0, 0.22, Wave.NOISE, 0.5, 12.0), Voice(2400.0, 600.0, 0.0, 0.12, Wave.TRIANGLE, 0.2, 15.0))),
        "tick" to render(0.06, listOf(Voice(1000.0, 800.0, 0.0, 0.05, Wave.SINE, 0.6, 25.0))),
        "go" to render(0.5, listOf(Voice(note(3), note(3), 0.0, 0.16, Wave.TRIANGLE, 0.5, 6.0), Voice(note(10), note(10), 0.14, 0.34, Wave.TRIANGLE, 0.6, 4.0), Voice(note(22), note(22), 0.14, 0.34, Wave.SINE, 0.2, 4.0))),
        "win" to render(1.1, listOf(
            Voice(note(3), note(3), 0.00, 0.25, Wave.TRIANGLE, 0.4, 4.0),
            Voice(note(7), note(7), 0.12, 0.25, Wave.TRIANGLE, 0.4, 4.0),
            Voice(note(10), note(10), 0.24, 0.25, Wave.TRIANGLE, 0.4, 4.0),
            Voice(note(15), note(15), 0.36, 0.70, Wave.TRIANGLE, 0.5, 2.5),
            Voice(note(27), note(27), 0.36, 0.70, Wave.SINE, 0.2, 2.5),
        )),
        "lose" to render(0.9, listOf(
            Voice(note(0), note(0), 0.00, 0.30, Wave.TRIANGLE, 0.4, 4.0),
            Voice(note(-4), note(-4), 0.25, 0.30, Wave.TRIANGLE, 0.4, 4.0),
            Voice(note(-9), note(-10), 0.50, 0.40, Wave.SQUARE, 0.35, 3.0),
        )),
    )

    fun toWav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()); buf.putInt(36 + dataLen); buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()); buf.putInt(16); buf.putShort(1); buf.putShort(1)
        buf.putInt(RATE); buf.putInt(RATE * 2); buf.putShort(2); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(dataLen)
        for (s in samples) buf.putShort(s)
        return buf.array()
    }

    @Suppress("unused")
    private fun dbToGain(db: Double) = 10.0.pow(db / 20.0)

    @Suppress("unused")
    private fun gainToDb(g: Double) = 20.0 * ln(g) / ln(10.0)
}
