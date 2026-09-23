package com.duel2048.server

import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.cube.CubeState
import com.duel2048.shared.protocol.Countdown
import com.duel2048.shared.protocol.CubeMatchFound
import com.duel2048.shared.protocol.CubeMatchOver
import com.duel2048.shared.protocol.CubeMoveAck
import com.duel2048.shared.protocol.CubeMoveRejected
import com.duel2048.shared.protocol.CubeOpponentProgress
import com.duel2048.shared.protocol.CubePlayerResult
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.MatchStarted
import com.duel2048.shared.protocol.PlayerInfo
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

class CubeParticipant(
    val info: PlayerInfo,
    private val session: PlayerSession?,
    val botSolution: List<CubeMove>?,
) {
    @Volatile lateinit var state: CubeState
    @Volatile var expectedSeq: Int = 0
    @Volatile var moveCount: Int = 0
    @Volatile var solved: Boolean = false
    @Volatile var finishedAtMs: Long = 0L

    fun send(msg: ServerMessage) {
        session?.send(msg)
    }
}

/**
 * Cube-solve race: both players receive the same scramble and race to return to solved.
 * The server validates every move and the final fingerprint.
 */
class CubeMatch(
    val id: String,
    private val p1: CubeParticipant,
    private val p2: CubeParticipant,
    private val seed: Long,
    private val scramble: List<CubeMove>,
    private val config: ServerConfig,
    private val scope: CoroutineScope,
    private val onFinished: (CubeMatch, CubeMatchOver) -> Unit,
) {
    private val log = LoggerFactory.getLogger("CubeMatch")
    private val mutex = Mutex()
    private val jobs = ArrayList<Job>()

    @Volatile private var started = false
    @Volatile private var finished = false
    private var startedAt = 0L

    init {
        val scrambled = CubeScramble.apply(seed)
        p1.state = scrambled
        p2.state = scrambled
    }

    fun start() {
        val notation = scramble.map { it.notation() }
        p1.send(CubeMatchFound(id, p1.info, p2.info, seed, notation, config.cubeTimeLimitMs, config.countdownSeconds))
        p2.send(CubeMatchFound(id, p2.info, p1.info, seed, notation, config.cubeTimeLimitMs, config.countdownSeconds))
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
            broadcast(MatchStarted(id, config.cubeTimeLimitMs))
            if (p1.botSolution != null) jobs += scope.launch { runBot(p1) }
            if (p2.botSolution != null) jobs += scope.launch { runBot(p2) }
            while (currentCoroutineContext().isActive) {
                delay(1000)
                val remaining = remainingMs()
                if (remaining <= 0) {
                    mutex.withLock { finishLocked(EndReason.TIME_UP, decideByProgress()) }
                    break
                }
                broadcast(Tick(id, remaining))
            }
        }
    }

    private fun remainingMs(): Long = config.cubeTimeLimitMs - (System.currentTimeMillis() - startedAt)

    private suspend fun runBot(p: CubeParticipant) {
        val solution = p.botSolution ?: return
        while (currentCoroutineContext().isActive && !finished) {
            delay(config.botIntervalMs + ThreadLocalRandom.current().nextLong(0, config.botJitterMs + 1))
            if (finished || p.solved) return
            val idx = p.moveCount
            if (idx >= solution.size) return
            onMove(p.info.id, p.expectedSeq, solution[idx].notation())
        }
    }

    suspend fun onMove(playerId: String, seq: Int, notation: String) {
        mutex.withLock {
            val p = participant(playerId) ?: return
            if (finished || p.solved) return
            if (!started) {
                p.send(CubeMoveRejected(id, seq, p.expectedSeq, "not_started", p.state.fingerprint()))
                return
            }
            if (seq < p.expectedSeq) return
            if (seq > p.expectedSeq) {
                p.send(CubeMoveRejected(id, seq, p.expectedSeq, "out_of_sync", p.state.fingerprint()))
                return
            }
            val move = CubeMove.parse(notation)
            if (move == null) {
                p.send(CubeMoveRejected(id, seq, p.expectedSeq, "bad_move", p.state.fingerprint()))
                return
            }
            p.expectedSeq++
            p.state = p.state.apply(move)
            p.moveCount++
            p.send(CubeMoveAck(id, seq, p.state.fingerprint(), p.moveCount))
            val o = other(p)
            o.send(CubeOpponentProgress(id, p.moveCount, p.state.isSolved()))

            if (p.state.isSolved()) {
                markSolved(p)
            }
        }
    }

    suspend fun onSolved(playerId: String, moves: Int, elapsedMs: Long, fingerprint: String) {
        mutex.withLock {
            val p = participant(playerId) ?: return
            if (finished || p.solved) return
            if (!p.state.isSolved() || p.state.fingerprint() != fingerprint) {
                p.send(CubeMoveRejected(id, p.expectedSeq, p.expectedSeq, "not_solved", p.state.fingerprint()))
                return
            }
            if (moves != p.moveCount) {
                p.send(CubeMoveRejected(id, p.expectedSeq, p.expectedSeq, "move_mismatch", p.state.fingerprint()))
                return
            }
            markSolved(p, elapsedMs)
        }
    }

    private fun markSolved(p: CubeParticipant, elapsedMs: Long = System.currentTimeMillis() - startedAt) {
        p.solved = true
        p.finishedAtMs = elapsedMs
        val o = other(p)
        o.send(CubeOpponentProgress(id, p.moveCount, true))
        if (!o.solved) {
            finishLocked(EndReason.SOLVED, p)
        } else {
            finishLocked(EndReason.SOLVED, decideByProgress())
        }
    }

    suspend fun forfeit(playerId: String) {
        mutex.withLock {
            if (finished) return
            val p = participant(playerId) ?: return
            finishLocked(EndReason.FORFEIT_CUBE, other(p).takeIf { !it.solved })
        }
    }

    private fun decideByProgress(): CubeParticipant? {
        val done = listOf(p1, p2).filter { it.solved }
        if (done.isEmpty()) return null
        if (done.size == 1) return done.first()
        return done.minWithOrNull(compareBy<CubeParticipant> { it.finishedAtMs }.thenBy { it.moveCount })
    }

    private fun finishLocked(reason: EndReason, winner: CubeParticipant?) {
        if (finished) return
        finished = true
        val results = listOf(p1, p2).map { p ->
            CubePlayerResult(
                playerId = p.info.id,
                moves = p.moveCount,
                elapsedMs = if (p.solved) p.finishedAtMs else remainingMs().let { config.cubeTimeLimitMs - it },
                solved = p.solved,
            )
        }
        log.info(
            "cube match $id over: reason=$reason winner=${winner?.info?.name ?: "draw"} " +
                "moves=${p1.moveCount}/${p2.moveCount} solved=${p1.solved}/${p2.solved}",
        )
        val over = CubeMatchOver(id, winner?.info?.id, reason, results)
        broadcast(over)
        jobs.forEach { it.cancel() }
        onFinished(this, over)
    }

    private fun broadcast(msg: ServerMessage) {
        p1.send(msg)
        p2.send(msg)
    }

    private fun participant(playerId: String): CubeParticipant? = when (playerId) {
        p1.info.id -> p1
        p2.info.id -> p2
        else -> null
    }

    private fun other(p: CubeParticipant): CubeParticipant = if (p === p1) p2 else p1
}
