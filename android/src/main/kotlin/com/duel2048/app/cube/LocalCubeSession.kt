package com.duel2048.app.cube

import com.duel2048.app.game.DuelPhase
import com.duel2048.app.game.TrainingProfile
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeScramble
import com.duel2048.shared.cube.CubeState
import com.duel2048.shared.engine.Rules
import com.duel2048.shared.protocol.CubeMatchOver
import com.duel2048.shared.protocol.CubePlayerResult
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.SavedGame
import com.duel2048.shared.protocol.SavedGameType
import com.duel2048.shared.protocol.SavedGameMode
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Offline cube-solve race against a local bot (inverse-scramble replay). No server required. */
class LocalCubeSession(
    private val scope: CoroutineScope,
    val profile: TrainingProfile,
    playerName: String,
    val botName: String,
    private val guided: Boolean = false,
    private val durationMs: Long = 300_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val meInfo = PlayerInfo("me", playerName.ifBlank { "You" }, isBot = false)
    private val botInfo = PlayerInfo("bot", botName, isBot = true)

    private val _state = MutableStateFlow(CubeDuelUiState(training = true, mode = MatchMode.BOT))
    val state: StateFlow<CubeDuelUiState> = _state.asStateFlow()

    private var local: CubeState? = null
    private var botSolution: List<CubeMove> = emptyList()
    private var botMoves = 0
    private var botSolved = false
    private var startedAt = 0L
    private var job: Job? = null
    private var finished = false

    fun snapshot(): SavedGame? = local?.let { SavedGame(
        id = _state.value.matchId ?: return null, game = SavedGameType.CUBE,
        mode = if (guided) SavedGameMode.TRAINING else SavedGameMode.BOT, completed = finished,
        durationMs = durationMs, remainingMs = _state.value.remainingNow(now()), seed = _state.value.scrambleSeed,
        played = _state.value.played, opponentPlayed = botSolution.take(botMoves).map { it.notation() },
        difficulty = profile.id, guided = guided, opponentName = botName,
        you = meInfo, opponent = botInfo, cubeResult = _state.value.result,
    ) }

    fun start(saved: SavedGame? = null) {
        job?.cancel()
        finished = false
        val seed = saved?.seed ?: Random.nextLong()
        val scramble = CubeScramble.generate(seed)
        botSolution = CubeScramble.inverse(scramble)
        local = CubeScramble.apply(seed).applyAll(saved?.played.orEmpty().map { checkNotNull(CubeMove.parse(it)) })
        botMoves = saved?.opponentPlayed?.size ?: 0
        botSolved = false
        _state.value = CubeDuelUiState(
            phase = DuelPhase.FOUND,
            mode = MatchMode.BOT,
            training = true,
            guided = guided,
            matchId = saved?.id ?: UUID.randomUUID().toString(),
            me = meInfo,
            opponent = botInfo,
            scramble = scramble.map { it.notation() },
            scrambleSeed = seed,
            scrambleNonce = _state.value.scrambleNonce + 1,
            myMoves = saved?.played?.size ?: 0,
            oppMoves = botMoves,
            oppLastMove = "",
            oppSerial = 0,
            played = saved?.played.orEmpty(),
            opponentPlayed = botSolution.take(botMoves).map { it.notation() },
            solutionHint = "",
            oppSolved = false,
            solved = false,
            countdown = Rules.COUNTDOWN_SECONDS,
            durationMs = durationMs,
            remainingMs = saved?.remainingMs ?: durationMs,
            result = null,
        )
        job = scope.launch {
            for (s in (if (saved == null) Rules.COUNTDOWN_SECONDS else 0) downTo 1) {
                _state.update { it.copy(countdown = s) }
                delay(1000)
            }
            startedAt = now() - (durationMs - (saved?.remainingMs ?: durationMs))
            _state.update {
                it.copy(phase = DuelPhase.PLAYING, countdown = null, remainingMs = saved?.remainingMs ?: durationMs, remainingAnchor = now())
            }
            launch { if (!guided) botLoop() }
            while (isActive && !finished) {
                delay(1000)
                val remaining = durationMs - (now() - startedAt)
                if (remaining <= 0) {
                    finish(EndReason.TIME_UP)
                    break
                }
                _state.update { it.copy(remainingMs = remaining, remainingAnchor = now()) }
            }
        }
    }

    fun applyMove(move: CubeMove) {
        val s = _state.value
        val cur = local ?: return
        if (s.phase != DuelPhase.PLAYING || s.solved || finished) return
        local = cur.apply(move)
        val moves = s.myMoves + 1
        _state.update { it.copy(myMoves = moves, played = it.played + move.notation()) }
        if (local!!.isSolved()) {
            _state.update { it.copy(solved = true) }
            finish(EndReason.SOLVED)
        }
    }

    fun leave() = dismiss()

    fun revealSolver(onlyNext: Boolean = false): String {
        val s = _state.value
        val scramble = s.scramble.mapNotNull { CubeMove.parse(it) }
        val played = s.played.mapNotNull { CubeMove.parse(it) }
        val text = (scramble + played).asReversed().joinToString(" ") { it.inverse().notation() }
        val shown = if (onlyNext) text.substringBefore(' ') else text
        _state.update { it.copy(solutionHint = shown) }
        return shown
    }

    fun dismiss() {
        job?.cancel()
        job = null
        local = null
        finished = true
        _state.value = CubeDuelUiState(training = true, mode = MatchMode.BOT)
    }

    private suspend fun botLoop() {
        while (currentCoroutineContext().isActive && !finished) {
            delay(profile.intervalMs + Random.nextLong(profile.jitterMs + 1))
            if (finished || botSolved) return
            if (botMoves >= botSolution.size) return
            val move = botSolution[botMoves]
            botMoves++
            botSolved = botMoves >= botSolution.size
            _state.update {
                it.copy(
                    oppMoves = botMoves,
                    oppSolved = botSolved,
                    oppLastMove = move.notation(),
                    opponentPlayed = it.opponentPlayed + move.notation(),
                    oppSerial = it.oppSerial + 1,
                )
            }
            if (botSolved && !_state.value.solved) finish(EndReason.SOLVED)
        }
    }

    private fun finish(reason: EndReason) {
        if (finished) return
        finished = true
        job?.cancel()
        val s = _state.value
        val mySolved = s.solved
        val winnerId = when {
            mySolved && !botSolved -> meInfo.id
            botSolved && !mySolved -> botInfo.id
            mySolved && botSolved -> {
                // Both solved: whoever had fewer moves wins; tie if equal.
                when {
                    s.myMoves < botMoves -> meInfo.id
                    botMoves < s.myMoves -> botInfo.id
                    else -> null
                }
            }
            else -> null
        }
        val over = CubeMatchOver(
            matchId = s.matchId ?: "cube-training",
            winnerId = winnerId,
            reason = reason,
            results = listOf(
                CubePlayerResult(meInfo.id, s.myMoves, if (mySolved) now() - startedAt else 0L, mySolved),
                CubePlayerResult(botInfo.id, botMoves, if (botSolved) now() - startedAt else 0L, botSolved),
            ),
        )
        _state.update { it.copy(phase = DuelPhase.OVER, result = over, countdown = null) }
    }
}
