package com.duel2048.app.cube.magic.domain

fun interface TimeProvider {
    fun currentTimeMillis(): Long
}