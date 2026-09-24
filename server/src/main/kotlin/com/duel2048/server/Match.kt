package com.duel2048.server

import com.duel2048.shared.bot.Bot
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.protocol.Countdown
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.GarbageLanded
import com.duel2048.shared.protocol.MatchFound
import com.duel2048.shared.protocol.MatchOver
import com.duel2048.shared.protocol.MatchStarted
import com.duel2048.shared.protocol.MoveAck
import com.duel2048.shared.protocol.MoveRejected
import com.duel2048.shared.protocol.OpponentMoved
import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.PlayerResult
import com.duel2048.shared.protocol.ServerMessage
import com.duel2048.shared.protocol.Tick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom

/** A human or bot seat in a match. */
class Participant(val info: PlayerInfo, private val session: PlayerSession?, val bot: Bot.Profile?) {
    @Volatile lateinit var state: GameState
    @Volatile var expectedSeq: Int = 0

    fun send(msg: ServerMessage) {
        session?.send(msg)
    }
}

/**
 * One duel. The server owns both boards and applies every move itself
 * (the client only predicts), like the 2048royale server does.
 */
class Match(
    val id: String,
    private val p1: Participant,
    private val p2: Participant,
    private val seed: Long,
    private val config: ServerConfig,
    private val scope: CoroutineScope,
    private val onFinished: (Match, MatchOver) -> Unit,
) {
    private val log = LoggerFactory.getLogger("Match")
    private val mutex = Mutex()
    private val jobs = ArrayList<Job>()

    @Volatile private var started = false
    @Volatile private var finished = false
    private var startedAt = 0L

    init {
        p1.state = GameEngine.newGame(seed, config.boardSize)
        p2.state = GameEngine.newGame(seed, config.boardSize)
    }

    fun start() {
        p1.send(MatchFound(id, p1.info, p2.info, seed, config.boardSize, config.matchDurationMs, config.countdownSeconds, p1.state, p2.state))
        p2.send(MatchFound(id, p2.info, p1.info, seed, config.boardSize, config.matchDurationMs, config.countdownSeconds, p2.state, p1.state))
        jobs += scope.launch {
            for (s in config.countdownSeconds downTo 1) {
                broadcast(Countdown(id, s))
                delay(1000)
            }
            mutex.withLock {
                if (finished) return@launch
                started = true
                startedAt = System.currentTimeMillis()
            }
            broadcast(MatchStarted(id, config.matchDurationMs))
            for (p in listOf(p1, p2)) {
                if (p.bot != null) jobs += scope.launch { runBot(p) }
            }
            while (currentCoroutineContext().isActive) {
                delay(1000)
                val remaining = remainingMs()
                if (remaining <= 0) {
                    mutex.withLock { finishLocked(EndReason.TIME_UP, decideByScore()) }
                    break
                }
                broadcast(Tick(id, remaining))
            }
        }
    }

    private fun remainingMs(): Long = config.matchDurationMs - (System.currentTimeMillis() - startedAt)

    private suspend fun runBot(p: Participant) {
        val profile = p.bot ?: return
        while (currentCoroutineContext().isActive && !finished) {
            delay(profile.intervalMs + ThreadLocalRandom.current().nextLong(0, profile.jitterMs + 1))
            val st = p.state
            if (finished || st.gameOver) return
            val dir = Bot.chooseMove(st, profile.depth) ?: return
            onMove(p.info.id, p.expectedSeq, dir)
        }
    }

    suspend fun onMove(playerId: String, seq: Int, direction: Direction) {
        mutex.withLock {
            val p = participant(playerId) ?: return
            if (finished) return
            if (!started) {
                p.send(MoveRejected(id, seq, p.expectedSeq, "not_started", p.state))
                return
            }
            if (seq < p.expectedSeq) return // stale duplicate
            if (seq > p.expectedSeq) {
                p.send(MoveRejected(id, seq, p.expectedSeq, "out_of_sync", p.state))
                return
            }
            p.expectedSeq++
            val result = GameEngine.move(p.state, direction)
            p.state = result.state
            p.send(MoveAck(id, seq, p.state, result.events))
            val o = other(p)
            o.send(OpponentMoved(id, p.state, result.events))

            if (result.events.garbageSent > 0) {
                val g = GameEngine.addGarbage(o.state, result.events.garbageSent)
                o.state = g.state
                val landed = GarbageLanded(id, o.info.id, p.info.id, o.state, g.placements)
                o.send(landed)
                p.send(landed)
            }

            when {
                p.state.gameOver && o.state.gameOver -> finishLocked(EndReason.BOARD_FULL, decideByScore())
                p.state.gameOver -> finishLocked(EndReason.BOARD_FULL, o)
                o.state.gameOver -> finishLocked(EndReason.BOARD_FULL, p)
            }
        }
    }

    suspend fun forfeit(playerId: String) {
        mutex.withLock {
            if (finished) return
            val p = participant(playerId) ?: return
            finishLocked(EndReason.FORFEIT, other(p))
        }
    }

    private fun decideByScore(): Participant? = when {
        p1.state.score != p2.state.score -> if (p1.state.score > p2.state.score) p1 else p2
        p1.state.board.maxValue() != p2.state.board.maxValue() -> if (p1.state.board.maxValue() > p2.state.board.maxValue()) p1 else p2
        else -> null
    }

    private fun finishLocked(reason: EndReason, winner: Participant?) {
        if (finished) return
        finished = true
        val results = listOf(p1, p2).map { p ->
            PlayerResult(
                playerId = p.info.id,
                score = p.state.score,
                maxTile = p.state.board.maxValue(),
                moves = p.state.moves,
                merges = p.state.mergesTotal,
                bestCombo = p.state.bestCombo,
                garbageSent = p.state.garbageSentTotal,
                garbageReceived = p.state.garbageReceivedTotal,
            )
        }
        log.info("match $id over: reason=$reason winner=${winner?.info?.name ?: "draw"} scores=${p1.state.score}/${p2.state.score}")
        val over = MatchOver(id, winner?.info?.id, reason, results)
        broadcast(over)
        jobs.forEach { it.cancel() }
        onFinished(this, over)
    }

    private fun broadcast(msg: ServerMessage) {
        p1.send(msg)
        p2.send(msg)
    }

    private fun participant(playerId: String): Participant? = when (playerId) {
        p1.info.id -> p1
        p2.info.id -> p2
        else -> null
    }

    private fun other(p: Participant): Participant = if (p === p1) p2 else p1

    fun liveRow() = com.duel2048.shared.social.LiveGame(
        id, "2048", p1.info.name, p1.state.score, p2.info.name, p2.state.score,
    )
}
