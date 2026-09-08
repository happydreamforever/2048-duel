package com.duel2048.app.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Small haptic vocabulary. Everything is guarded by the user's setting. */
class Haptics(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick() = oneShot(10, 90)

    fun merge(value: Int) = oneShot(if (value >= 128) 28 else 16, if (value >= 128) 200 else 120)

    fun combo() = waveform(longArrayOf(0, 18, 30, 24, 30, 36))

    fun heavy() = waveform(longArrayOf(0, 45, 40, 70))

    fun success() = waveform(longArrayOf(0, 30, 50, 30, 50, 90))

    fun fail() = oneShot(140, 180)

    private fun oneShot(ms: Long, amplitude: Int) {
        if (!enabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
    }

    private fun waveform(pattern: LongArray) {
        if (!enabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
