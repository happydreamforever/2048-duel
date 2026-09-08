package com.duel2048.shared.engine

/**
 * SplitMix64: tiny, fast and, most importantly, bit-for-bit identical on the server JVM
 * and on Android. Client and server share one seed per match, so tile spawns are
 * predictable on both sides without any extra network traffic.
 */
class SplitMix64(var state: Long) {

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform int in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Uniform float in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()

    companion object {
        private val GOLDEN: Long = 0x9E3779B97F4A7C15uL.toLong()
        private val MIX1: Long = 0xBF58476D1CE4E5B9uL.toLong()
        private val MIX2: Long = 0x94D049BB133111EBuL.toLong()
    }
}
