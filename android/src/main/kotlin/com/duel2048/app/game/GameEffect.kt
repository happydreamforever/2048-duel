package com.duel2048.app.game

import com.duel2048.shared.engine.Cell
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.MoveEvents
import kotlinx.coroutines.flow.MutableSharedFlow

/** One-shot events the UI turns into particles, shakes, haptics and sounds. */
sealed interface GameEffect {
    data class Merges(val cells: List<Pair<Cell, Int>>, val combo: Int, val scoreGained: Int, val mine: Boolean) : GameEffect
    data class Spawned(val cell: Cell) : GameEffect
    data class AttackSent(val count: Int) : GameEffect
    data class GarbageHit(val cells: List<Cell>, val mine: Boolean) : GameEffect
    data class GarbageShattered(val cells: List<Cell>, val mine: Boolean) : GameEffect
    data class GarbageDamaged(val cells: List<Cell>, val mine: Boolean) : GameEffect
    data class NoMove(val direction: Direction) : GameEffect
    data class CountdownTick(val seconds: Int) : GameEffect
    data object Go : GameEffect
    data class MatchEnd(val won: Boolean, val draw: Boolean) : GameEffect
    data class Milestone(val value: Int) : GameEffect
}

/** Emits the effects that follow from one move's events. Shared by online and offline sessions. */
fun MutableSharedFlow<GameEffect>.emitMoveEffects(ev: MoveEvents, mine: Boolean) {
    if (ev.merges.isNotEmpty()) {
        tryEmit(GameEffect.Merges(ev.merges.map { it.at to it.value }, ev.combo, ev.scoreGained, mine))
        if (mine) {
            val best = ev.merges.maxOf { it.value }
            if (best >= 256) tryEmit(GameEffect.Milestone(best))
        }
    }
    if (ev.garbageShattered.isNotEmpty()) tryEmit(GameEffect.GarbageShattered(ev.garbageShattered, mine))
    if (ev.garbageDamaged.isNotEmpty()) tryEmit(GameEffect.GarbageDamaged(ev.garbageDamaged, mine))
    if (mine && ev.garbageSent > 0) tryEmit(GameEffect.AttackSent(ev.garbageSent))
}
