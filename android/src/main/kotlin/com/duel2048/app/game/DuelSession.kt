package com.duel2048.app.game

import com.duel2048.shared.engine.Direction
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Error surfaced to the UI as a code (mapped to a localized string) plus optional detail. */
data class DuelError(val code: String, val detail: String? = null)

/** A duel the UI can render: online (MatchController) or offline training (LocalDuelSession). */
interface DuelSession {
    val state: StateFlow<DuelUiState>
    val effects: SharedFlow<GameEffect>
    fun swipe(direction: Direction)
    fun leave()
    fun dismiss()
}

/** Bot settings for offline training. */
data class TrainingProfile(val id: String, val intervalMs: Long, val jitterMs: Long, val depth: Int) {
    companion object {
        val EASY = TrainingProfile("easy", 1300, 400, 1)
        val NORMAL = TrainingProfile("normal", 750, 300, 2)
        val HARD = TrainingProfile("hard", 420, 150, 3)
        val all = listOf(EASY, NORMAL, HARD)
        fun byId(id: String): TrainingProfile = all.firstOrNull { it.id == id } ?: NORMAL
    }
}
