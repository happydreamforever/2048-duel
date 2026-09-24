package com.duel2048.app.cube

import com.duel2048.app.game.DuelError
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.net.ConnectionState
import com.duel2048.app.net.DuelClient
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeState
import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Auth
import com.duel2048.shared.protocol.AuthFailed
import com.duel2048.shared.protocol.AuthOk
import com.duel2048.shared.protocol.CancelFindMatch
import com.duel2048.shared.protocol.Countdown
import com.duel2048.shared.protocol.CubeMatchFound
import com.duel2048.shared.protocol.CubeMatchOver
import com.duel2048.shared.protocol.CubeMoveAck
import com.duel2048.shared.protocol.CubeMoveMsg
import com.duel2048.shared.protocol.CubeMoveRejected
import com.duel2048.shared.protocol.CubeOpponentProgress
import com.duel2048.shared.protocol.CubeSolvedMsg
import com.duel2048.shared.protocol.ErrorMsg
import com.duel2048.shared.protocol.FindMatch
import com.duel2048.shared.protocol.GameType
import com.duel2048.shared.protocol.Hello
import com.duel2048.shared.protocol.LeaveMatch
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.MatchStarted
import com.duel2048.shared.protocol.Ping
import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.Pong
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.Queued
import com.duel2048.shared.protocol.ServerMessage
import com.duel2048.shared.protocol.StatsUpdated
import com.duel2048.shared.protocol.Tick
import com.duel2048.shared.protocol.Welcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CubeDuelUiState(
    val phase: DuelPhase = DuelPhase.IDLE,
    val mode: MatchMode = MatchMode.PVP,
    /** Offline training on this device (no server). */
    val training: Boolean = false,
    val guided: Boolean = false,
    val matchId: String? = null,
    val me: PlayerInfo? = null,
    val opponent: PlayerInfo? = null,
    val scramble: List<String> = emptyList(),
    val scrambleSeed: Long = 0L,
    val scrambleNonce: Int = 0,
    val myMoves: Int = 0,
    val oppMoves: Int = 0,
    val oppLastMove: String = "",
    val oppSerial: Int = 0,
    val oppSolved: Boolean = false,
    val played: List<String> = emptyList(),
    val solutionHint: String = "",
    val solved: Boolean = false,
    val countdown: Int? = null,
    val durationMs: Long = 300_000L,
    val remainingMs: Long = 300_000L,
    val remainingAnchor: Long = 0L,
    val botFallbackMs: Long = 0L,
    val searchStartedAt: Long = 0L,
    val onlinePlayers: Int = 0,
    val pingMs: Long? = null,
    val result: CubeMatchOver? = null,
    val error: DuelError? = null,
) {
    val myId: String? get() = me?.id
    val won: Boolean get() = result?.winnerId != null && result.winnerId == myId
    val draw: Boolean get() = result != null && result.winnerId == null

    fun remainingNow(now: Long): Long =
        if (phase == DuelPhase.PLAYING && remainingAnchor > 0) {
            (remainingMs - (now - remainingAnchor)).coerceAtLeast(0L)
        } else {
            remainingMs
        }
}

/** Online cube-solve race: same scramble, first to solve wins. */
class CubeMatchController(
    private val client: DuelClient,
    private val scope: CoroutineScope,
    private val onStats: (AccountStats) -> Unit = {},
    private val onSessionExpired: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(CubeDuelUiState())
    val state: StateFlow<CubeDuelUiState> = _state.asStateFlow()

    private var local: CubeState? = null
    private var seq = 0
    private var startedAt = 0L
    private var token = ""
    private var connectionJob: Job? = null
    private var messageJob: Job? = null
    private var pingJob: Job? = null

    fun start(url: String, token: String, mode: MatchMode) {
        stopJobs()
        this.token = token
        local = null
        seq = 0
        _state.value = CubeDuelUiState(phase = DuelPhase.CONNECTING, mode = mode, searchStartedAt = now())
        messageJob = scope.launch { client.messages.collect { onMessage(it) } }
        connectionJob = scope.launch {
            client.connection.collect { c ->
                when (c) {
                    is ConnectionState.Failed -> fail(DuelError("connect_failed", c.reason))
                    ConnectionState.Disconnected -> {
                        val phase = _state.value.phase
                        if (phase == DuelPhase.PLAYING || phase == DuelPhase.FOUND || phase == DuelPhase.SEARCHING) {
                            fail(DuelError("connection_lost"))
                        }
                    }
                    else -> Unit
                }
            }
        }
        client.connect(url)
    }

    fun cancel() {
        client.send(CancelFindMatch)
        reset()
    }

    fun leave() {
        _state.value.matchId?.let { client.send(LeaveMatch(it)) }
        reset()
    }

    fun dismiss() = reset()

    fun applyMove(move: CubeMove) {
        val s = _state.value
        val cur = local ?: return
        val matchId = s.matchId ?: return
        if (s.phase != DuelPhase.PLAYING || s.solved) return
        val next = cur.apply(move)
        local = next
        val moves = s.myMoves + 1
        _state.update { it.copy(myMoves = moves, played = it.played + move.notation()) }
        client.send(CubeMoveMsg(matchId, seq, move.notation()))
        seq++
        if (next.isSolved()) {
            val elapsed = now() - startedAt
            _state.update { it.copy(solved = true) }
            client.send(CubeSolvedMsg(matchId, moves, elapsed, next.fingerprint()))
        }
    }

    fun localState(): CubeState? = local

    fun revealSolver(onlyNext: Boolean = false): String {
        val s = _state.value
        val scramble = s.scramble.mapNotNull { CubeMove.parse(it) }
        val played = s.played.mapNotNull { CubeMove.parse(it) }
        val text = (scramble + played).asReversed().joinToString(" ") { it.inverse().notation() }
        val shown = if (onlyNext) text.substringBefore(' ') else text
        _state.update { it.copy(solutionHint = shown) }
        return shown
    }

    private fun onMessage(msg: ServerMessage) {
        val s = _state.value
        when (msg) {
            is Welcome -> {
                client.send(Hello("", Protocol.VERSION))
                client.send(Auth(token))
                _state.update { it.copy(onlinePlayers = msg.onlinePlayers) }
            }
            is AuthOk -> {
                onStats(msg.stats)
                client.send(FindMatch(s.mode, GameType.CUBE_SOLVE))
                _state.update { it.copy(phase = DuelPhase.SEARCHING, searchStartedAt = now()) }
                startPings()
            }
            is AuthFailed -> {
                fail(DuelError("session_expired"))
                onSessionExpired()
            }
            is StatsUpdated -> onStats(msg.stats)
            is Queued -> _state.update { it.copy(botFallbackMs = msg.botFallbackMs, searchStartedAt = now()) }
            is CubeMatchFound -> {
                val scrambled = CubeState.SOLVED.applyAll(msg.scramble.mapNotNull { CubeMove.parse(it) })
                local = scrambled
                seq = 0
                _state.update {
                    it.copy(
                        phase = DuelPhase.FOUND,
                        matchId = msg.matchId,
                        me = msg.you,
                        opponent = msg.opponent,
                        scramble = msg.scramble,
                        scrambleSeed = msg.seed,
                        scrambleNonce = it.scrambleNonce + 1,
                        myMoves = 0,
                        oppMoves = 0,
                        oppLastMove = "",
                        oppSerial = 0,
                        played = emptyList(),
                        solutionHint = "",
                        oppSolved = false,
                        solved = false,
                        countdown = msg.countdownSeconds,
                        durationMs = msg.timeLimitMs,
                        remainingMs = msg.timeLimitMs,
                        remainingAnchor = 0L,
                        result = null,
                        error = null,
                    )
                }
            }
            is Countdown -> {
                if (msg.matchId != s.matchId) return
                _state.update { it.copy(countdown = msg.secondsLeft) }
            }
            is MatchStarted -> {
                if (msg.matchId != s.matchId) return
                startedAt = now()
                _state.update {
                    it.copy(phase = DuelPhase.PLAYING, countdown = null, remainingMs = msg.remainingMs, remainingAnchor = now())
                }
            }
            is Tick -> {
                if (msg.matchId != s.matchId) return
                _state.update { it.copy(remainingMs = msg.remainingMs, remainingAnchor = now()) }
            }
            is CubeMoveAck -> {
                if (msg.matchId != s.matchId) return
                if (local?.fingerprint() != msg.fingerprint) {
                    // Server rejected silently via fingerprint mismatch — wait for reject message.
                }
                _state.update { it.copy(myMoves = msg.moves) }
            }
            is CubeMoveRejected -> {
                if (msg.matchId != s.matchId) return
                seq = msg.expectedSeq
                // Fingerprint resync would need full state from server; for now keep local.
            }
            is CubeOpponentProgress -> {
                if (msg.matchId != s.matchId) return
                _state.update {
                    it.copy(
                        oppMoves = msg.moves,
                        oppSolved = msg.solved,
                        oppLastMove = msg.move,
                        oppSerial = if (msg.move.isNotBlank()) it.oppSerial + 1 else it.oppSerial,
                    )
                }
            }
            is CubeMatchOver -> {
                if (msg.matchId != s.matchId) return
                pingJob?.cancel()
                _state.update { it.copy(phase = DuelPhase.OVER, result = msg, countdown = null) }
                scope.launch {
                    delay(1500)
                    if (_state.value.phase == DuelPhase.OVER) client.disconnect()
                }
            }
            is ErrorMsg -> when (msg.code) {
                "version" -> fail(DuelError("version", msg.message))
                "not_logged_in" -> {
                    fail(DuelError("not_logged_in"))
                    onSessionExpired()
                }
                else -> _state.update { it.copy(error = DuelError("server", msg.message)) }
            }
            is Pong -> _state.update { it.copy(pingMs = now() - msg.clientTime) }
            else -> Unit
        }
    }

    private fun startPings() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                client.send(Ping(now()))
                delay(4000)
            }
        }
    }

    private fun fail(error: DuelError) {
        stopJobs()
        client.disconnect()
        _state.update { it.copy(phase = DuelPhase.IDLE, error = error, countdown = null) }
    }

    private fun reset() {
        stopJobs()
        client.disconnect()
        local = null
        _state.value = CubeDuelUiState(mode = _state.value.mode)
    }

    private fun stopJobs() {
        connectionJob?.cancel(); connectionJob = null
        messageJob?.cancel(); messageJob = null
        pingJob?.cancel(); pingJob = null
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
