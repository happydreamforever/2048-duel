package com.duel2048.app.game

import com.duel2048.shared.bot.Bot
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.engine.GameState
import com.duel2048.shared.engine.Rules
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.MatchOver
import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.PlayerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
import kotlin.random.Random

/**
 * Offline training duel. The whole match (both boards, bot, garbage exchange, timer)
 * runs on the device with the same shared rules the server uses, so it doubles as a
 * test bench for the game logic and effects without any network.
 *
 * Everything runs on the caller's dispatcher (the view model's main thread), so no locking.
 */
class LocalDuelSession(
    private val scope: CoroutineScope,
    val profile: TrainingProfile,
    playerName: String,
    val botName: String,
    private val durationMs: Long = Rules.MATCH_DURATION_MS,
) : DuelSession {

    private val meInfo = PlayerInfo("me", playerName.ifBlank { "You" }, isBot = false)
    private val botInfo = PlayerInfo("bot", botName, isBot = true)

    private val _state = MutableStateFlow(DuelUiState(training = true, mode = MatchMode.BOT))
    override val state: StateFlow<DuelUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 128)
    override val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    private var me: GameState = GameEngine.newGame(0L)
    private var bot: GameState = GameEngine.newGame(0L)
    private var version = 0L
    private var startedAt = 0L
    private var job: Job? = null
    private var finished = false

    fun start() {
        val seed = Random.nextLong()
        me = GameEngine.newGame(seed)
        bot = GameEngine.newGame(seed)
        finished = false
        version++
        _state.value = DuelUiState(
            phase = DuelPhase.FOUND,
            mode = MatchMode.BOT,
            training = true,
            matchId = "training",
            me = PlayerSide(info = meInfo).with(me, BoardUiBuilder.fromState(me, version, TileKind.SPAWN)),
            opponent = PlayerSide(info = botInfo).with(bot, BoardUiBuilder.fromState(bot, version, TileKind.SPAWN)),
            countdown = Rules.COUNTDOWN_SECONDS,
            durationMs = durationMs,
            remainingMs = durationMs,
        )
        job = scope.launch {
            for (s in Rules.COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = s) }
                _effects.tryEmit(GameEffect.CountdownTick(s))
                delay(1000)
            }
            startedAt = System.currentTimeMillis()
            _state.update { it.copy(phase = DuelPhase.PLAYING, countdown = null, remainingMs = durationMs, remainingAnchor = startedAt) }
            _effects.tryEmit(GameEffect.Go)
            launch { botLoop() }
            while (isActive && !finished) {
                delay(1000)
                val remaining = durationMs - (System.currentTimeMillis() - startedAt)
                if (remaining <= 0) {
                    finish(EndReason.TIME_UP, decideByScore())
                    break
                }
                _state.update { it.copy(remainingMs = remaining, remainingAnchor = System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun botLoop() {
        while (currentCoroutineContext().isActive && !finished) {
            delay(profile.intervalMs + Random.nextLong(profile.jitterMs + 1))
            if (finished || bot.gameOver) return
            val dir = Bot.chooseMove(bot, profile.depth) ?: return
            applyBotMove(dir)
        }
    }

    override fun swipe(direction: Direction) {
        if (_state.value.phase != DuelPhase.PLAYING || finished || me.gameOver) return
        val r = GameEngine.move(me, direction)
        if (!r.events.moved) {
            _effects.tryEmit(GameEffect.NoMove(direction))
            return
        }
        me = r.state
        version++
        _state.update { it.copy(me = it.me.with(me, BoardUiBuilder.fromMove(me, r.events, version))) }
        _effects.emitMoveEffects(r.events, mine = true)
        if (r.events.garbageSent > 0) {
            val g = GameEngine.addGarbage(bot, r.events.garbageSent)
            bot = g.state
            version++
            _state.update { it.copy(opponent = it.opponent.with(bot, BoardUiBuilder.fromGarbage(bot, g.placements, version))) }
            _effects.tryEmit(GameEffect.GarbageHit(g.placements.map { it.at }, mine = false))
        }
        checkEnd()
    }

    private fun applyBotMove(direction: Direction) {
        val r = GameEngine.move(bot, direction)
        if (!r.events.moved) return
        bot = r.state
        version++
        _state.update { it.copy(opponent = it.opponent.with(bot, BoardUiBuilder.fromMove(bot, r.events, version))) }
        _effects.emitMoveEffects(r.events, mine = false)
        if (r.events.garbageSent > 0) {
            val g = GameEngine.addGarbage(me, r.events.garbageSent)
            me = g.state
            version++
            _state.update { it.copy(me = it.me.with(me, BoardUiBuilder.fromGarbage(me, g.placements, version)), incomingWarning = 0) }
            _effects.tryEmit(GameEffect.GarbageHit(g.placements.map { it.at }, mine = true))
        }
        checkEnd()
    }

    private fun checkEnd() {
        when {
            me.gameOver && bot.gameOver -> finish(EndReason.BOARD_FULL, decideByScore())
            me.gameOver -> finish(EndReason.BOARD_FULL, botInfo.id)
            bot.gameOver -> finish(EndReason.BOARD_FULL, meInfo.id)
        }
    }

    private fun decideByScore(): String? = when {
        me.score != bot.score -> if (me.score > bot.score) meInfo.id else botInfo.id
        me.board.maxValue() != bot.board.maxValue() -> if (me.board.maxValue() > bot.board.maxValue()) meInfo.id else botInfo.id
        else -> null
    }

    private fun result(info: PlayerInfo, s: GameState) = PlayerResult(
        playerId = info.id, score = s.score, maxTile = s.board.maxValue(), moves = s.moves, merges = s.mergesTotal,
        bestCombo = s.bestCombo, garbageSent = s.garbageSentTotal, garbageReceived = s.garbageReceivedTotal,
    )

    private fun finish(reason: EndReason, winnerId: String?) {
        if (finished) return
        finished = true
        job?.cancel()
        val over = MatchOver("training", winnerId, reason, listOf(result(meInfo, me), result(botInfo, bot)))
        _state.update { it.copy(phase = DuelPhase.OVER, result = over, countdown = null) }
        _effects.tryEmit(GameEffect.MatchEnd(won = winnerId == meInfo.id, draw = winnerId == null))
    }

    override fun leave() {
        finished = true
        job?.cancel()
        _state.value = DuelUiState(training = true, mode = MatchMode.BOT)
    }

    override fun dismiss() = leave()
}
