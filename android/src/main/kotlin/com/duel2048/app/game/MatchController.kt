package com.duel2048.app.game

import com.duel2048.app.net.ConnectionState
import com.duel2048.app.net.DuelClient
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.Rules
import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Auth
import com.duel2048.shared.protocol.AuthFailed
import com.duel2048.shared.protocol.AuthOk
import com.duel2048.shared.protocol.CancelFindMatch
import com.duel2048.shared.protocol.Countdown
import com.duel2048.shared.protocol.ErrorMsg
import com.duel2048.shared.protocol.FindMatch
import com.duel2048.shared.protocol.GarbageLanded
import com.duel2048.shared.protocol.Hello
import com.duel2048.shared.protocol.LeaveMatch
import com.duel2048.shared.protocol.MatchFound
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.MatchOver
import com.duel2048.shared.protocol.MatchStarted
import com.duel2048.shared.protocol.MoveAck
import com.duel2048.shared.protocol.MoveMsg
import com.duel2048.shared.protocol.MoveRejected
import com.duel2048.shared.protocol.OpponentMoved
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DuelPhase { IDLE, CONNECTING, SEARCHING, FOUND, PLAYING, OVER }

data class PlayerSide(
    val info: PlayerInfo? = null,
    val board: BoardUi = BoardUi.empty(Rules.BOARD_SIZE),
    val score: Int = 0,
    val energy: Int = 0,
    val garbageSent: Int = 0,
    val garbageReceived: Int = 0,
    val maxTile: Int = 0,
    val moves: Int = 0,
    val gameOver: Boolean = false,
) {
    fun with(state: GameState, board: BoardUi): PlayerSide = copy(
        board = board,
        score = state.score,
        energy = state.energy,
        garbageSent = state.garbageSentTotal,
        garbageReceived = state.garbageReceivedTotal,
        maxTile = state.board.maxValue(),
        moves = state.moves,
        gameOver = state.gameOver,
    )
}

data class DuelUiState(
    val phase: DuelPhase = DuelPhase.IDLE,
    val mode: MatchMode = MatchMode.PVP,
    /** True for offline training against the on-device bot. */
    val training: Boolean = false,
    val matchId: String? = null,
    val me: PlayerSide = PlayerSide(),
    val opponent: PlayerSide = PlayerSide(),
    val countdown: Int? = null,
    val durationMs: Long = Rules.MATCH_DURATION_MS,
    val remainingMs: Long = Rules.MATCH_DURATION_MS,
    /** Wall-clock time at which [remainingMs] was last set, for interpolation. */
    val remainingAnchor: Long = 0L,
    val botFallbackMs: Long = 0L,
    val searchStartedAt: Long = 0L,
    val onlinePlayers: Int = 0,
    val pingMs: Long? = null,
    val result: MatchOver? = null,
    val error: DuelError? = null,
    val incomingWarning: Int = 0,
) {
    val myId: String? get() = me.info?.id
    val won: Boolean get() = result?.winnerId != null && result.winnerId == myId
    val draw: Boolean get() = result != null && result.winnerId == null

    fun remainingNow(now: Long): Long =
        if (phase == DuelPhase.PLAYING && remainingAnchor > 0) (remainingMs - (now - remainingAnchor)).coerceAtLeast(0L) else remainingMs
}

/**
 * Online duel: connects, authenticates with the saved token, queues for a match, predicts
 * the player's own moves with the shared engine and reconciles against the server's acks.
 */
class MatchController(
    private val client: DuelClient,
    private val scope: CoroutineScope,
    private val onStats: (AccountStats) -> Unit = {},
    private val onSessionExpired: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : DuelSession {
    private val _state = MutableStateFlow(DuelUiState())
    override val state: StateFlow<DuelUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 128)
    override val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    private var local: GameState? = null
    private var seq = 0
    private var version = 0L
    private val predictions = ArrayDeque<Pair<Int, String>>()
    private var token = ""
    private var connectionJob: Job? = null
    private var messageJob: Job? = null
    private var pingJob: Job? = null

    fun start(url: String, token: String, mode: MatchMode) {
        stopJobs()
        this.token = token
        local = null
        seq = 0
        predictions.clear()
        _state.value = DuelUiState(phase = DuelPhase.CONNECTING, mode = mode, searchStartedAt = now())
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

    override fun leave() {
        _state.value.matchId?.let { client.send(LeaveMatch(it)) }
        reset()
    }

    override fun dismiss() = reset()

    override fun swipe(direction: Direction) {
        val s = _state.value
        val cur = local ?: return
        val matchId = s.matchId ?: return
        if (s.phase != DuelPhase.PLAYING || cur.gameOver) return
        val r = GameEngine.move(cur, direction)
        if (!r.events.moved) {
            _effects.tryEmit(GameEffect.NoMove(direction))
            return
        }
        local = r.state
        version++
        val board = BoardUiBuilder.fromMove(r.state, r.events, version)
        _state.update { it.copy(me = it.me.with(r.state, board)) }
        predictions.addLast(seq to r.state.syncKey())
        client.send(MoveMsg(matchId, seq, direction))
        seq++
        _effects.emitMoveEffects(r.events, mine = true)
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
                client.send(FindMatch(s.mode))
                _state.update { it.copy(phase = DuelPhase.SEARCHING, searchStartedAt = now()) }
                startPings()
            }
            is AuthFailed -> {
                fail(DuelError("session_expired"))
                onSessionExpired()
            }
            is StatsUpdated -> onStats(msg.stats)
            is Queued -> _state.update { it.copy(botFallbackMs = msg.botFallbackMs, searchStartedAt = now()) }
            is MatchFound -> {
                local = msg.yourState
                seq = 0
                predictions.clear()
                version++
                _state.update {
                    it.copy(
                        phase = DuelPhase.FOUND,
                        matchId = msg.matchId,
                        me = PlayerSide(info = msg.you).with(msg.yourState, BoardUiBuilder.fromState(msg.yourState, version, TileKind.SPAWN)),
                        opponent = PlayerSide(info = msg.opponent).with(msg.opponentState, BoardUiBuilder.fromState(msg.opponentState, version, TileKind.SPAWN)),
                        countdown = msg.countdownSeconds,
                        durationMs = msg.durationMs,
                        remainingMs = msg.durationMs,
                        remainingAnchor = 0L,
                        result = null,
                        error = null,
                        incomingWarning = 0,
                    )
                }
            }
            is Countdown -> {
                if (msg.matchId != s.matchId) return
                _state.update { it.copy(countdown = msg.secondsLeft) }
                _effects.tryEmit(GameEffect.CountdownTick(msg.secondsLeft))
            }
            is MatchStarted -> {
                if (msg.matchId != s.matchId) return
                _state.update { it.copy(phase = DuelPhase.PLAYING, countdown = null, remainingMs = msg.remainingMs, remainingAnchor = now()) }
                _effects.tryEmit(GameEffect.Go)
            }
            is Tick -> {
                if (msg.matchId != s.matchId) return
                _state.update { it.copy(remainingMs = msg.remainingMs, remainingAnchor = now()) }
            }
            is MoveAck -> {
                if (msg.matchId != s.matchId) return
                var predicted: String? = null
                while (predictions.isNotEmpty() && predictions.first().first <= msg.seq) {
                    val (pSeq, key) = predictions.removeFirst()
                    if (pSeq == msg.seq) predicted = key
                }
                if (predicted != msg.state.syncKey()) resyncLocal(msg.state)
            }
            is MoveRejected -> {
                if (msg.matchId != s.matchId) return
                seq = msg.expectedSeq
                predictions.clear()
                resyncLocal(msg.state)
            }
            is OpponentMoved -> {
                if (msg.matchId != s.matchId) return
                version++
                val board = BoardUiBuilder.fromMove(msg.state, msg.events, version)
                _state.update { it.copy(opponent = it.opponent.with(msg.state, board), incomingWarning = if (msg.events.garbageSent > 0) msg.events.garbageSent else it.incomingWarning) }
                _effects.emitMoveEffects(msg.events, mine = false)
            }
            is GarbageLanded -> {
                if (msg.matchId != s.matchId) return
                version++
                val cells = msg.placements.map { it.at }
                if (msg.targetId == s.myId) {
                    local = msg.state
                    predictions.clear()
                    val board = BoardUiBuilder.fromGarbage(msg.state, msg.placements, version)
                    _state.update { it.copy(me = it.me.with(msg.state, board), incomingWarning = 0) }
                    _effects.tryEmit(GameEffect.GarbageHit(cells, mine = true))
                } else {
                    val board = BoardUiBuilder.fromGarbage(msg.state, msg.placements, version)
                    _state.update { it.copy(opponent = it.opponent.with(msg.state, board)) }
                    _effects.tryEmit(GameEffect.GarbageHit(cells, mine = false))
                }
            }
            is MatchOver -> {
                if (msg.matchId != s.matchId) return
                pingJob?.cancel()
                _state.update { it.copy(phase = DuelPhase.OVER, result = msg, countdown = null) }
                val won = msg.winnerId != null && msg.winnerId == s.myId
                _effects.tryEmit(GameEffect.MatchEnd(won = won, draw = msg.winnerId == null))
                // Keep the socket open briefly so the StatsUpdated message arrives.
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
            else -> Unit // cube PvP messages are handled by CubeMatchController
        }
    }

    private fun resyncLocal(state: GameState) {
        local = state
        version++
        _state.update { it.copy(me = it.me.with(state, BoardUiBuilder.fromState(state, version))) }
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
        _state.value = DuelUiState(mode = _state.value.mode)
    }

    private fun stopJobs() {
        connectionJob?.cancel(); connectionJob = null
        messageJob?.cancel(); messageJob = null
        pingJob?.cancel(); pingJob = null
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
