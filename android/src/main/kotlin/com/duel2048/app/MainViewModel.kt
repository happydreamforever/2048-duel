package com.duel2048.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duel2048.app.data.SettingsStore
import com.duel2048.app.data.UserSettings
import com.duel2048.app.game.DuelError
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.cube.CubeDuelUiState
import com.duel2048.app.cube.CubeMatchController
import com.duel2048.app.cube.LocalCubeSession
import com.duel2048.app.game.DuelSession
import com.duel2048.app.game.LocalDuelSession
import com.duel2048.app.game.MatchController
import com.duel2048.app.game.SoloController
import com.duel2048.app.game.TrainingProfile
import com.duel2048.app.net.AccountClient
import com.duel2048.app.net.DuelClient
import com.duel2048.app.net.LeaderboardClient
import com.duel2048.app.net.SocialClient
import com.duel2048.shared.social.AdminNote
import com.duel2048.shared.social.ChatLine
import com.duel2048.shared.social.Economy
import com.duel2048.shared.social.LiveGame
import com.duel2048.shared.social.PlayerPresence
import com.duel2048.shared.social.Puzzles
import androidx.compose.ui.graphics.Color
import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Accounts
import com.duel2048.shared.protocol.ClientMessage
import com.duel2048.shared.protocol.LeaderboardEntry
import com.duel2048.shared.protocol.Login
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.Register
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Login : Screen
    data object Matchmaking : Screen
    data object Duel : Screen
    data object Result : Screen
    data object Solo : Screen
    data object CubeMatchmaking : Screen
    data object CubeDuel : Screen
    data object CubeResult : Screen
    data object Leaderboard : Screen
    data object MoreGames : Screen
    data class GameModes(val game: HubGame) : Screen
    data object MiniPlay : Screen
    data object Watch : Screen
}

enum class HubMode { PVP, BOT, TRAIN, WATCH }

enum class HubGame(val title: String, val blurb: String) {
    CUBE("Cube", "OpenGL cube, same scramble"),
    CUBE2("Cube 2", "AnimCube race"),
    MATH("Math", "Beat the clock"),
    WORD("Word puzzle", "Find the hidden word"),
    ;

    val colors: List<Color>
        get() = when (this) {
            CUBE -> listOf(Color(0xFF6B4EFF), Color(0xFF2D1B69))
            CUBE2 -> listOf(Color(0xFF4A6FA5), Color(0xFF1B3A5C))
            MATH -> listOf(Color(0xFFB8860B), Color(0xFF7A4B00))
            WORD -> listOf(Color(0xFF1F9D6B), Color(0xFF0E6B8A))
        }
}

data class MiniUi(
    val title: String,
    val opponent: String,
    val prompt: String,
    val grid: List<String> = emptyList(),
    val secondsLeft: Int,
    val status: String,
    val solution: String = "",
    val hint: String = "",
    val won: Boolean = false,
    val guided: Boolean = false,
    val mode: String = "bot",
)

data class LoginUiState(val busy: Boolean = false, val error: DuelError? = null)

data class LeaderboardUiState(val loading: Boolean = false, val entries: List<LeaderboardEntry> = emptyList(), val error: DuelError? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    val settings: StateFlow<UserSettings> = settingsStore.settings

    val client = DuelClient()
    private val cubeClient = DuelClient()
    private val account = AccountClient()
    private val leaderboardClient = LeaderboardClient()
    private val social = SocialClient()
    val match = MatchController(client, viewModelScope, onStats = ::saveAccountStats, onSessionExpired = ::onSessionExpired)
    val cubeMatch = CubeMatchController(cubeClient, viewModelScope, onStats = ::saveAccountStats, onSessionExpired = ::onSessionExpired)
    private val localCubeHolder = MutableStateFlow<LocalCubeSession?>(null)
    private val cubeIsLocal = MutableStateFlow(false)
    /** Online cube match or offline cube training, depending on [cubeIsLocal]. */
    val cubeUiState = cubeIsLocal.flatMapLatest { local ->
        if (local) localCubeHolder.value?.state ?: flowOf(CubeDuelUiState(training = true))
        else cubeMatch.state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CubeDuelUiState())
    val solo = SoloController(initialBest = settings.value.bestSolo) { best ->
        settingsStore.update { it.copy(bestSolo = best) }
    }

    private val _session = MutableStateFlow<DuelSession>(match)
    /** The duel currently shown: the online match or an offline training session. */
    val session: StateFlow<DuelSession> = _session.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _leaderboard = MutableStateFlow(LeaderboardUiState())
    val leaderboard: StateFlow<LeaderboardUiState> = _leaderboard.asStateFlow()

    private var pendingMode: MatchMode? = null
    private var pendingCubeMode: MatchMode? = null
    private val rewarded = HashSet<String>()
    private val charged = HashSet<String>()
    private var miniSolution = ""
    private var miniRoom = ""
    private var miniJob: kotlinx.coroutines.Job? = null
    private var noteCursor = 0L

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner.asStateFlow()
    private val _players = MutableStateFlow<List<PlayerPresence>>(emptyList())
    val players: StateFlow<List<PlayerPresence>> = _players.asStateFlow()
    private val _notes = MutableStateFlow<List<AdminNote>>(emptyList())
    val notes: StateFlow<List<AdminNote>> = _notes.asStateFlow()
    private val _chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val chat: StateFlow<List<ChatLine>> = _chat.asStateFlow()
    private val _liveGames = MutableStateFlow<List<LiveGame>>(emptyList())
    val liveGames: StateFlow<List<LiveGame>> = _liveGames.asStateFlow()
    private val _watchFocus = MutableStateFlow<String?>(null)
    val watchFocus: StateFlow<String?> = _watchFocus.asStateFlow()
    private val _mini = MutableStateFlow<MiniUi?>(null)
    val mini: StateFlow<MiniUi?> = _mini.asStateFlow()
    private var watchGame: HubGame? = null

    init {
        viewModelScope.launch {
            _session.flatMapLatest { it.state }.map { it.phase }.distinctUntilChanged().collect { phase ->
                when (phase) {
                    DuelPhase.CONNECTING, DuelPhase.SEARCHING -> _screen.value = Screen.Matchmaking
                    DuelPhase.FOUND, DuelPhase.PLAYING -> {
                        val s = _session.value.state.value
                        val kind = if (s.training) "train" else if (s.mode == MatchMode.BOT) "bot" else "pvp"
                        if (!chargeJoinOnce("join-2048-${s.matchId}", kind)) {
                            _session.value.leave()
                            _screen.value = Screen.Home
                        } else {
                            _screen.value = Screen.Duel
                        }
                    }
                    DuelPhase.OVER -> {
                        val s = _session.value.state.value
                        if (s.training && s.result != null) {
                            settingsStore.update { if (s.won) it.copy(wins = it.wins + 1) else if (s.draw) it else it.copy(losses = it.losses + 1) }
                        }
                        rewardOnce("2048-${s.matchId}", if (s.training) "train" else if (s.mode == MatchMode.BOT) "bot" else "pvp", s.won)
                        delay(1600)
                        if (_session.value.state.value.phase == DuelPhase.OVER) _screen.value = Screen.Result
                    }
                    DuelPhase.IDLE -> if (_screen.value == Screen.Matchmaking || _screen.value == Screen.Duel) _screen.value = Screen.Home
                }
            }
        }
        viewModelScope.launch {
            cubeUiState.map { it.phase }.distinctUntilChanged().collect { phase ->
                when (phase) {
                    DuelPhase.CONNECTING, DuelPhase.SEARCHING -> _screen.value = Screen.CubeMatchmaking
                    DuelPhase.FOUND, DuelPhase.PLAYING -> {
                        val s = cubeUiState.value
                        val kind = if (s.guided) "train" else if (s.training || s.mode == MatchMode.BOT) "bot" else "pvp"
                        if (!chargeJoinOnce("join-cube-${s.matchId}-${s.scrambleNonce}", kind)) {
                            leaveCubeDuel()
                        } else {
                            _screen.value = Screen.CubeDuel
                        }
                    }
                    DuelPhase.OVER -> {
                        val s = cubeUiState.value
                        if (s.training && s.result != null) {
                            settingsStore.update {
                                if (s.won) it.copy(wins = it.wins + 1)
                                else if (s.draw) it
                                else it.copy(losses = it.losses + 1)
                            }
                        }
                        val kind = if (s.guided) "train" else if (s.training || s.mode == MatchMode.BOT) "bot" else "pvp"
                        rewardOnce("cube-${s.matchId}-${s.scrambleNonce}", kind, s.won)
                        delay(1600)
                        if (cubeUiState.value.phase == DuelPhase.OVER) _screen.value = Screen.CubeResult
                    }
                    DuelPhase.IDLE -> if (_screen.value == Screen.CubeMatchmaking || _screen.value == Screen.CubeDuel) _screen.value = Screen.Home
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                refreshNotes()
                delay(8_000)
            }
        }
    }

    // ------------------------------------------------------------------ online

    fun startDuel(mode: MatchMode) {
        val s = settings.value
        if (!s.loggedIn) {
            pendingMode = mode
            pendingCubeMode = null
            _login.value = LoginUiState()
            _screen.value = Screen.Login
            return
        }
        _session.value = match
        match.start(s.serverUrl, s.authToken, mode)
    }

    fun startCubeDuel(mode: MatchMode) {
        val s = settings.value
        if (!s.loggedIn) {
            pendingCubeMode = mode
            pendingMode = null
            _login.value = LoginUiState()
            _screen.value = Screen.Login
            return
        }
        cubeIsLocal.value = false
        localCubeHolder.value = null
        cubeMatch.start(s.serverUrl, s.authToken, mode)
    }

    fun startCubeTraining(profile: TrainingProfile, botName: String, guided: Boolean = false) {
        val s = settings.value
        val name = s.accountName.ifBlank { s.playerName }
        val session = LocalCubeSession(viewModelScope, profile, name, botName, guided = guided)
        localCubeHolder.value = session
        cubeIsLocal.value = true
        session.start()
    }

    fun applyCubeMove(move: CubeMove) {
        if (cubeIsLocal.value) localCubeHolder.value?.applyMove(move)
        else cubeMatch.applyMove(move)
    }

    fun cancelCubeSearch() {
        cubeMatch.cancel()
        _screen.value = Screen.Home
    }

    fun leaveCubeDuel() {
        if (cubeIsLocal.value) {
            localCubeHolder.value?.leave()
            cubeIsLocal.value = false
            localCubeHolder.value = null
        } else {
            cubeMatch.leave()
        }
        _screen.value = Screen.Home
    }

    fun playCubeAgain() {
        if (cubeIsLocal.value) {
            val session = localCubeHolder.value
            val profile = session?.profile ?: TrainingProfile.NORMAL
            val botName = session?.botName ?: "Trainer"
            session?.dismiss()
            startCubeTraining(profile, botName, cubeUiState.value.guided)
        } else {
            val mode = cubeMatch.state.value.mode
            cubeMatch.dismiss()
            startCubeDuel(mode)
        }
    }

    fun openLogin() {
        pendingMode = null
        _login.value = LoginUiState()
        _screen.value = Screen.Login
    }

    fun cancelLogin() {
        pendingMode = null
        _screen.value = Screen.Home
    }

    fun submitAuth(name: String, password: String, register: Boolean) {
        if (_login.value.busy) return
        val n = name.trim()
        Accounts.validateName(n)?.let { _login.value = LoginUiState(error = DuelError(it)); return }
        Accounts.validatePassword(password)?.let { _login.value = LoginUiState(error = DuelError(it)); return }
        _login.value = LoginUiState(busy = true)
        val url = settings.value.serverUrl
        viewModelScope.launch {
            val request: ClientMessage = if (register) Register(n, password) else Login(n, password)
            when (val r = account.authenticate(url, request)) {
                is AccountClient.Outcome.Ok -> {
                    settingsStore.update {
                        it.copy(
                            authToken = r.auth.token,
                            accountName = r.auth.name,
                            playerName = it.playerName.ifBlank { r.auth.name },
                            accWins = r.auth.stats.wins,
                            accLosses = r.auth.stats.losses,
                            accDraws = r.auth.stats.draws,
                            accBestScore = r.auth.stats.bestScore,
                        )
                    }
                    viewModelScope.launch { pullWallet() }
                    _login.value = LoginUiState()
                    _screen.value = Screen.Home
                    pendingMode?.let { mode ->
                        pendingMode = null
                        startDuel(mode)
                    }
                    pendingCubeMode?.let { mode ->
                        pendingCubeMode = null
                        startCubeDuel(mode)
                    }
                }
                is AccountClient.Outcome.Failed -> _login.value = LoginUiState(error = DuelError(r.code, r.detail))
            }
        }
    }

    fun logout() {
        settingsStore.update { it.copy(authToken = "", accountName = "", accWins = 0, accLosses = 0, accDraws = 0, accBestScore = 0) }
    }

    private fun saveAccountStats(stats: AccountStats) {
        settingsStore.update { it.copy(accWins = stats.wins, accLosses = stats.losses, accDraws = stats.draws, accBestScore = stats.bestScore) }
    }

    private fun onSessionExpired() {
        settingsStore.update { it.copy(authToken = "") }
        _login.value = LoginUiState(error = DuelError("session_expired"))
        _screen.value = Screen.Login
    }

    // ------------------------------------------------------------------ offline

    fun startTraining(profile: TrainingProfile, botName: String) {
        val s = settings.value
        val name = s.accountName.ifBlank { s.playerName }
        val local = LocalDuelSession(viewModelScope, profile, name, botName)
        _session.value = local
        local.start()
    }

    fun openSolo() {
        solo.ensureGame()
        _screen.value = Screen.Solo
    }

    // ------------------------------------------------------------------ leaderboard

    fun openLeaderboard() {
        _screen.value = Screen.Leaderboard
        refreshLeaderboard()
    }

    fun refreshLeaderboard() {
        if (_leaderboard.value.loading) return
        _leaderboard.value = _leaderboard.value.copy(loading = true, error = null)
        val url = settings.value.serverUrl
        viewModelScope.launch {
            leaderboardClient.fetch(url).fold(
                onSuccess = { entries -> _leaderboard.value = LeaderboardUiState(entries = entries) },
                onFailure = { e -> _leaderboard.value = _leaderboard.value.copy(loading = false, error = DuelError("connect_failed", e.message ?: "")) },
            )
        }
    }

    // ------------------------------------------------------------------ common

    fun cancelSearch() {
        val s = _session.value
        if (s is MatchController) s.cancel() else s.leave()
        _screen.value = Screen.Home
    }

    fun leaveMatch() {
        _session.value.leave()
        _screen.value = Screen.Home
    }

    fun playAgain() {
        when (val s = _session.value) {
            is LocalDuelSession -> {
                s.dismiss()
                startTraining(s.profile, s.botName)
            }
            else -> {
                val mode = s.state.value.mode
                s.dismiss()
                startDuel(mode)
            }
        }
    }

    fun openMoreGames() { _screen.value = Screen.MoreGames }

    fun openGameModes(game: HubGame) { _screen.value = Screen.GameModes(game) }

    fun startHub(game: HubGame, mode: HubMode, difficulty: String) {
        if (game == HubGame.CUBE || game == HubGame.CUBE2) {
            val renderer = if (game == HubGame.CUBE2) com.duel2048.app.data.CubeRenderer.CUBE2.id else com.duel2048.app.data.CubeRenderer.MAGIC.id
            updateSettings { it.copy(cubeRenderer = renderer) }
            when (mode) {
                HubMode.PVP -> startCubeDuel(MatchMode.PVP)
                HubMode.BOT -> startCubeTraining(profileFor(difficulty), "Bot")
                HubMode.TRAIN -> startCubeTraining(TrainingProfile.EASY, "Guide", guided = true)
                HubMode.WATCH -> openWatch(game)
            }
        } else if (mode == HubMode.WATCH) {
            openWatch(game)
        } else {
            startMini(game, mode, difficulty)
        }
    }

    fun revealCubeSolver() {
        if (cubeIsLocal.value) localCubeHolder.value?.revealSolver() else cubeMatch.revealSolver()
    }

    fun cubeHint() {
        if (cubeIsLocal.value) localCubeHolder.value?.revealSolver(onlyNext = true) else cubeMatch.revealSolver(onlyNext = true)
    }

    fun focusWatch(id: String) { _watchFocus.value = id }

    fun refreshLive() {
        viewModelScope.launch {
            social.live(settings.value.serverUrl).onSuccess { games ->
                val filtered = watchGame?.let { g ->
                    val key = if (g == HubGame.WORD) "word" else if (g == HubGame.MATH) "math" else "cube"
                    games.filter { it.game == key || (key == "cube" && it.game == "cube") }
                } ?: games
                _liveGames.value = filtered
                if (_watchFocus.value == null) _watchFocus.value = filtered.firstOrNull()?.id
            }
        }
    }

    fun refreshPlayers() {
        viewModelScope.launch { social.players(settings.value.serverUrl).onSuccess { _players.value = it } }
    }

    fun refreshNotes() {
        viewModelScope.launch {
            social.notes(settings.value.serverUrl, noteCursor).onSuccess { fresh ->
                if (fresh.isNotEmpty()) {
                    noteCursor = fresh.maxOf { it.id }
                    _notes.value = (_notes.value + fresh).takeLast(30)
                }
            }
        }
    }

    fun refreshChat() {
        viewModelScope.launch {
            social.chat(settings.value.serverUrl, "lobby", 0).onSuccess { _chat.value = it }
        }
    }

    fun sendChat(text: String) {
        val name = settings.value.accountName.ifBlank { settings.value.playerName.ifBlank { "Player" } }
        viewModelScope.launch {
            social.sendChat(settings.value.serverUrl, name, text, "lobby").onSuccess { refreshChat() }
        }
    }

    fun sendReport(text: String) {
        if (text.isBlank()) return
        val name = settings.value.accountName.ifBlank { settings.value.playerName.ifBlank { "Player" } }
        viewModelScope.launch { social.report(settings.value.serverUrl, name, text.trim(), "lobby") }
    }

    fun buyCoins(pack: Int) = grant(0, pack)

    fun submitMini(answer: String) {
        val s = _mini.value ?: return
        if (s.status != "playing") return
        viewModelScope.launch {
            if (miniRoom.isNotBlank()) {
                val name = settings.value.accountName.ifBlank { "Player" }
                val room = social.answerMini(settings.value.serverUrl, miniRoom, name, answer.trim()).getOrNull() ?: return@launch
                val won = room.winner == name
                _mini.value = s.copy(status = room.status, won = won, solution = room.solution)
                if (room.status == "over") rewardOnce("mini-$miniRoom", s.mode, won)
            } else {
                val won = answer.trim().equals(miniSolution, ignoreCase = true)
                _mini.value = s.copy(status = "over", won = won, solution = miniSolution, secondsLeft = 0)
                rewardOnce("mini-${s.prompt}-${s.grid.joinToString()}", s.mode, won)
            }
        }
    }

    fun miniHint() {
        val s = _mini.value ?: return
        if (miniSolution.isBlank()) return
        val hint = if (s.grid.isNotEmpty()) miniSolution.take(1) else miniSolution.take((miniSolution.length / 2).coerceAtLeast(1))
        _mini.value = s.copy(hint = hint)
    }

    fun closeMini() {
        miniJob?.cancel()
        _mini.value = null
        _screen.value = Screen.MoreGames
    }

    fun clearBanner() { _banner.value = null }

    private fun openWatch(game: HubGame) {
        watchGame = game
        _watchFocus.value = null
        _screen.value = Screen.Watch
        refreshLive()
    }

    private fun startMini(game: HubGame, mode: HubMode, difficulty: String) {
        miniJob?.cancel()
        val guided = mode == HubMode.TRAIN
        val modeId = when (mode) {
            HubMode.PVP -> "pvp"
            HubMode.BOT -> "bot"
            else -> "train"
        }
        viewModelScope.launch {
            val name = settings.value.accountName.ifBlank { settings.value.playerName.ifBlank { "Player" } }
            var prompt = ""
            var grid = emptyList<String>()
            var seconds = 30
            var opponent = if (mode == HubMode.PVP) "waiting" else "Bot"
            miniRoom = ""
            miniSolution = ""
            if (mode == HubMode.PVP) {
                val joined = social.joinMini(settings.value.serverUrl, name, if (game == HubGame.WORD) "word" else "math", "pvp", difficulty).getOrNull()
                if (joined != null && joined.roomId.isNotBlank()) {
                    miniRoom = joined.roomId
                    prompt = joined.prompt
                    grid = joined.grid
                    seconds = joined.seconds
                    opponent = joined.opponent
                }
            }
            if (miniRoom.isBlank()) {
                if (game == HubGame.WORD) {
                    val built = Puzzles.word(if (difficulty == "6") 6 else 4)
                    grid = built.first
                    miniSolution = built.second
                    prompt = "Find the word"
                    seconds = built.third + if (guided) 25 else 0
                } else {
                    val built = Puzzles.math(difficulty)
                    prompt = built.first
                    miniSolution = built.second
                    seconds = when (difficulty) {
                        "very_hard" -> 90
                        "hard" -> 40
                        "medium" -> 30
                        else -> 20
                    } + if (guided) 20 else 0
                }
            }
            _mini.value = MiniUi(game.title, opponent, prompt, grid, seconds, "playing", guided = guided, mode = modeId)
            _screen.value = Screen.MiniPlay
            miniJob = viewModelScope.launch {
                var left = seconds
                while (left > 0 && _mini.value?.status == "playing") {
                    delay(1000)
                    left--
                    _mini.value = _mini.value?.copy(secondsLeft = left)
                }
                val cur = _mini.value ?: return@launch
                if (cur.status == "playing") {
                    _mini.value = cur.copy(status = "over", won = false, solution = miniSolution, secondsLeft = 0)
                    rewardOnce("mini-time-${cur.prompt}", cur.mode, false)
                }
            }
        }
    }

    private fun profileFor(id: String) = when (id) {
        "easy" -> TrainingProfile.EASY
        "hard" -> TrainingProfile.HARD
        else -> TrainingProfile.NORMAL
    }

    private fun chargeJoinOnce(key: String, kind: String): Boolean {
        if (key in charged) return true
        val cost = Economy.joinCost(kind)
        if (settings.value.coins < cost) {
            _banner.value = "need_coins"
            return false
        }
        charged.add(key)
        if (cost > 0) grant(0, -cost)
        return true
    }

    private fun rewardOnce(key: String, kind: String, won: Boolean) {
        if (!rewarded.add(key)) return
        val (coins, score) = Economy.payout(kind, won)
        grant(score, coins)
    }

    private fun grant(score: Int, coins: Int) {
        settingsStore.update { it.copy(score = it.score + score, coins = (it.coins + coins).coerceAtLeast(0)) }
        val token = settings.value.authToken
        if (token.isNotBlank() && (score != 0 || coins != 0)) {
            viewModelScope.launch { social.applyWallet(settings.value.serverUrl, token, score, coins) }
        }
    }

    private suspend fun pullWallet() {
        val token = settings.value.authToken
        if (token.isBlank()) return
        social.wallet(settings.value.serverUrl, token).onSuccess { w ->
            settingsStore.update { it.copy(score = w.score, coins = w.coins) }
        }
    }

    fun goHome() {
        val s = _session.value
        if (s.state.value.phase == DuelPhase.OVER) s.dismiss()
        if (cubeIsLocal.value && cubeUiState.value.phase == DuelPhase.OVER) {
            localCubeHolder.value?.dismiss()
            cubeIsLocal.value = false
            localCubeHolder.value = null
        } else if (cubeMatch.state.value.phase == DuelPhase.OVER) {
            cubeMatch.dismiss()
        }
        _screen.value = Screen.Home
    }

    fun clearError() = (_session.value as? MatchController)?.clearError()

    fun updateSettings(transform: (UserSettings) -> UserSettings) = settingsStore.update(transform)

    override fun onCleared() {
        client.disconnect()
        cubeClient.disconnect()
    }
}
