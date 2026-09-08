package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.MatchRecord
import com.duel2048.server.db.UserStore
import com.duel2048.server.db.UserRecord
import com.duel2048.shared.bot.Bot
import com.duel2048.shared.protocol.Auth
import com.duel2048.shared.protocol.AuthFailed
import com.duel2048.shared.protocol.AuthOk
import com.duel2048.shared.protocol.Login
import com.duel2048.shared.protocol.Logout
import com.duel2048.shared.protocol.MatchOver
import com.duel2048.shared.protocol.Register
import com.duel2048.shared.protocol.StatsUpdated
import com.duel2048.shared.protocol.CancelFindMatch
import com.duel2048.shared.protocol.ClientMessage
import com.duel2048.shared.protocol.ErrorMsg
import com.duel2048.shared.protocol.FindMatch
import com.duel2048.shared.protocol.Hello
import com.duel2048.shared.protocol.LeaveMatch
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.MoveMsg
import com.duel2048.shared.protocol.Ping
import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.Pong
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.Queued
import com.duel2048.shared.protocol.ServerStats
import com.duel2048.shared.protocol.Welcome
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connection handling and matchmaking.
 *
 * Pairing works like es/2048-multiplayer: a FIFO queue, first two searchers form a match.
 * If nobody shows up within [ServerConfig.botFallbackMs] a bot fills in so the game is
 * always playable.
 */
class Lobby(private val config: ServerConfig, private val scope: CoroutineScope, private val db: UserStore) {

    private val log = LoggerFactory.getLogger("Lobby")
    private val players = ConcurrentHashMap<String, PlayerSession>()
    private val matches = ConcurrentHashMap<String, Match>()
    private val queue = ArrayDeque<PlayerSession>()
    private val queueMutex = Mutex()
    private val fallbackJobs = ConcurrentHashMap<String, Job>()
    private val totalMatches = AtomicInteger()

    suspend fun handleSession(ws: DefaultWebSocketServerSession) {
        val session = PlayerSession(UUID.randomUUID().toString().substring(0, 8), ws)
        players[session.id] = session
        log.info("connected ${session.id} (online=${players.size})")
        session.send(Welcome(session.id, Protocol.VERSION, players.size, db.userCount()))
        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val msg = try {
                    Protocol.decodeClient(frame.readText())
                } catch (e: Exception) {
                    session.send(ErrorMsg("bad_message", e.message ?: "unparseable message"))
                    continue
                }
                dispatch(session, msg)
            }
        } catch (e: ClosedReceiveChannelException) {
            // normal close
        } catch (e: Exception) {
            log.warn("session ${session.id} failed: ${e.message}")
        } finally {
            disconnect(session)
        }
    }

    private suspend fun dispatch(s: PlayerSession, msg: ClientMessage) {
        when (msg) {
            is Hello -> {
                s.name = msg.name.trim().take(16).ifBlank { "Player" }
                if (msg.clientVersion != Protocol.VERSION) {
                    s.send(ErrorMsg("version", "server speaks protocol v${Protocol.VERSION}, client sent v${msg.clientVersion}"))
                }
            }
            is Register -> onAuth(s, db.register(msg.name, msg.password))
            is Login -> onAuth(s, db.login(msg.name, msg.password))
            is Auth -> {
                val user = db.authByToken(msg.token)
                if (user == null) s.send(AuthFailed("bad_token", "Session expired, please log in again")) else attach(s, user)
            }
            is Logout -> {
                s.userId?.let { db.logout(it) }
                s.userId = null
            }
            is FindMatch -> if (s.userId == null) s.send(ErrorMsg("not_logged_in", "Log in first")) else findMatch(s, msg.mode)
            is CancelFindMatch -> {
                fallbackJobs.remove(s.id)?.cancel()
                queueMutex.withLock { queue.remove(s) }
            }
            is MoveMsg -> {
                val match = s.match
                if (match == null || match.id != msg.matchId) {
                    s.send(ErrorMsg("no_match", "not in match ${msg.matchId}"))
                } else {
                    match.onMove(s.id, msg.seq, msg.direction)
                }
            }
            is LeaveMatch -> s.match?.forfeit(s.id)
            is Ping -> s.send(Pong(msg.clientTime, System.currentTimeMillis()))
        }
    }

    private fun onAuth(s: PlayerSession, result: AuthResult) {
        when (result) {
            is AuthResult.Ok -> attach(s, result.user)
            is AuthResult.Failed -> s.send(AuthFailed(result.code, result.message))
        }
    }

    private fun attach(s: PlayerSession, user: UserRecord) {
        s.userId = user.id
        s.name = user.name
        s.send(AuthOk(s.id, user.name, user.token ?: "", user.stats))
        log.info("${s.id} logged in as ${user.name}")
    }

    /** Persist the outcome for logged-in humans and push their fresh stats. */
    private fun recordStats(session: PlayerSession, over: MatchOver) {
        val userId = session.userId ?: return
        val res = over.results.firstOrNull { it.playerId == session.id } ?: return
        val won = over.winnerId == session.id
        val draw = over.winnerId == null
        scope.launch {
            val updated = db.updateStats(userId) { st ->
                st.copy(
                    wins = st.wins + if (won) 1 else 0,
                    losses = st.losses + if (!won && !draw) 1 else 0,
                    draws = st.draws + if (draw) 1 else 0,
                    matches = st.matches + 1,
                    bestScore = maxOf(st.bestScore, res.score),
                    bestTile = maxOf(st.bestTile, res.maxTile),
                    garbageSent = st.garbageSent + res.garbageSent,
                )
            }
            if (updated != null) session.send(StatsUpdated(updated.stats))
        }
    }

    private suspend fun findMatch(s: PlayerSession, mode: MatchMode) {
        if (s.match != null) {
            s.send(ErrorMsg("in_match", "already in a match"))
            return
        }
        if (mode == MatchMode.BOT) {
            startMatch(s, null)
            return
        }
        var opponent: PlayerSession? = null
        queueMutex.withLock {
            queue.remove(s)
            opponent = queue.removeFirstOrNull()
            if (opponent == null) queue.addLast(s)
        }
        val opp = opponent
        if (opp != null) {
            fallbackJobs.remove(opp.id)?.cancel()
            startMatch(s, opp)
        } else {
            s.send(Queued(position = 1, botFallbackMs = config.botFallbackMs))
            fallbackJobs[s.id] = scope.launch {
                delay(config.botFallbackMs)
                val stillWaiting = queueMutex.withLock { queue.remove(s) }
                fallbackJobs.remove(s.id)
                if (stillWaiting && s.match == null) startMatch(s, null)
            }
        }
    }

    private fun startMatch(a: PlayerSession, b: PlayerSession?) {
        val seed = ThreadLocalRandom.current().nextLong()
        val p1 = Participant(a.info, a, bot = null)
        val p2 = if (b != null) {
            Participant(b.info, b, bot = null)
        } else {
            val profile = Bot.DEFAULT.copy(
                name = BOT_NAMES[ThreadLocalRandom.current().nextInt(BOT_NAMES.size)],
                intervalMs = config.botIntervalMs,
                jitterMs = config.botJitterMs,
            )
            Participant(PlayerInfo("bot-" + UUID.randomUUID().toString().substring(0, 4), profile.name, isBot = true), null, profile)
        }
        val id = "m" + UUID.randomUUID().toString().substring(0, 8)
        val startedAt = System.currentTimeMillis()
        val match = Match(id, p1, p2, seed, config, scope) { finished, over ->
            matches.remove(finished.id)
            if (a.match === finished) a.match = null
            if (b != null && b.match === finished) b.match = null
            recordStats(a, over)
            if (b != null) recordStats(b, over)
            val r1 = over.results.firstOrNull { it.playerId == p1.info.id }
            val r2 = over.results.firstOrNull { it.playerId == p2.info.id }
            scope.launch {
                try {
                    db.recordMatch(
                        MatchRecord(
                            matchId = id, playedAt = startedAt, durationMs = System.currentTimeMillis() - startedAt,
                            reason = over.reason.name, winnerName = listOf(p1, p2).firstOrNull { it.info.id == over.winnerId }?.info?.name,
                            p1Name = p1.info.name, p1UserId = a.userId, p1Score = r1?.score ?: 0,
                            p2Name = p2.info.name, p2UserId = b?.userId, p2Score = r2?.score ?: 0,
                        ),
                    )
                } catch (e: Exception) {
                    log.warn("could not record match $id: ${e.message}")
                }
            }
        }
        matches[id] = match
        a.match = match
        b?.match = match
        totalMatches.incrementAndGet()
        log.info("match $id: ${p1.info.name} vs ${p2.info.name}${if (p2.bot != null) " (bot)" else ""} seed=$seed")
        match.start()
    }

    private suspend fun disconnect(s: PlayerSession) {
        players.remove(s.id)
        fallbackJobs.remove(s.id)?.cancel()
        queueMutex.withLock { queue.remove(s) }
        s.match?.forfeit(s.id)
        s.close()
        log.info("disconnected ${s.id} (online=${players.size})")
    }

    fun stats(): ServerStats = ServerStats(players.size, queue.size, matches.size, totalMatches.get())

    companion object {
        private val BOT_NAMES = listOf("Nova", "Byte", "Pixel", "Vega", "Orion", "Quark", "Echo", "Zed")
    }
}
