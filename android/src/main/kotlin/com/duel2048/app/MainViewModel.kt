package com.duel2048.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duel2048.app.data.SettingsStore
import com.duel2048.app.data.UserSettings
import com.duel2048.shared.protocol.SavedGameType
import java.util.UUID
import kotlinx.coroutines.flow.combine
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
import com.duel2048.shared.social.WordGrid
import com.duel2048.shared.social.ChallengeView
import com.duel2048.shared.social.ChallengeCreateBody
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
    data object SavedGames : Screen
    data object Community : Screen
    data object Profile : Screen
    data object Login : Screen
    data object Matchmaking : Screen
    data object Duel : Screen
    data object Result : Screen
    data object Solo : Screen
    data object CubeMatchmaking : Screen
    data object CubeDuel : Screen
    data object CubeResult : Screen
    data object Leaderboard : Screen
    data class GameModes(val game: HubGame) : Screen
    data class Challenges(val game: HubGame? = null, val difficulty: String = "", val opponent: String = "") : Screen
    data object MiniPlay : Screen
    data object Watch : Screen
}

enum class HubMode { PVP, BOT, TRAIN, WATCH }

enum class HubGame(val title: String, val blurb: String) {
    TILES("2048", "Merge, build, and outplay"),
    CUBE("Cube", "3D cube, same scramble"),
    MATH("Math", "Beat the clock"),
    WORD("Word Quiz", "Solve the clue using letter tiles"),
    WORD_PUZZLE("Word Puzzle", "Find words in a letter grid"),
    ;

    val colors: List<Color>
        get() = when (this) {
            TILES -> listOf(Color(0xFF007F9E), Color(0xFF06455B))
            CUBE -> listOf(Color(0xFF6B4EFF), Color(0xFF2D1B69))
            MATH -> listOf(Color(0xFFB8860B), Color(0xFF7A4B00))
            WORD_PUZZLE -> listOf(Color(0xFFB04C84), Color(0xFF543986))
            WORD -> listOf(Color(0xFF1F9D6B), Color(0xFF0E6B8A))
        }
}
fun HubGame.gameKey(): String = when(this) {
    HubGame.TILES -> "2048";HubGame.CUBE -> "cube";HubGame.MATH -> "math";HubGame.WORD -> "word";HubGame.WORD_PUZZLE -> "word_puzzle"
}
fun gameFromKey(key:String):HubGame = HubGame.entries.first { it.gameKey()==key }


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
    val saveId: String = UUID.randomUUID().toString(),
    val game: SavedGameType = SavedGameType.MATH,
    val difficulty: String = "easy",
    val choices: List<String> = emptyList(),
    val puzzleId: String = "",
    val selectedLetters: List<Int> = emptyList(),
    val answered: Boolean = false,
    val submitting: Boolean = false,
    val answerFailed: Boolean = false,
    val foundWords: List<String> = emptyList(),
    val score: Int = 0,
    val opponentScore: Int = 0,
    val invalidWord: Boolean = false,
)

data class ReportUiState(val busy: Boolean = false, val sent: Boolean = false, val error: Boolean = false, val signInRequired: Boolean = false)

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
    val cubeUiState = combine(cubeIsLocal, localCubeHolder) { local, holder ->
        if (local) holder?.state ?: flowOf(CubeDuelUiState(training = true))
        else cubeMatch.state
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.Eagerly, CubeDuelUiState())
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
    private var notesServer = ""

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
    private var saveIdentity: String? = null

    private val _challenges = MutableStateFlow<List<ChallengeView>>(emptyList())
    val challenges = _challenges.asStateFlow()
    private val _challengeBusy = MutableStateFlow(false)
    val challengeBusy = _challengeBusy.asStateFlow()
    private val _challengeError = MutableStateFlow<String?>(null)
    val challengeError = _challengeError.asStateFlow()
    private val _joiningChallenge = MutableStateFlow<String?>(null)
    val joiningChallenge = _joiningChallenge.asStateFlow()
    private var fetchingChallenges = false
    private var privateMatch: ChallengeView? = null

    init {
        viewModelScope.launch {
            settings.map { Triple(it.serverUrl, it.authToken, it.accountName) }.distinctUntilChanged().collect { (url, token, name) ->
                val identity = "$url|$name"
                if(notesServer!=url) {
                    if(notesServer.isNotBlank()) { _reportDraft.value="";_reportState.value=ReportUiState() }
                    notesServer=url;noteCursor=0;_notes.value=emptyList()
                }
                if (saveIdentity != null && saveIdentity != identity) {
                    _challenges.value=emptyList();_joiningChallenge.value=null;privateMatch=null
                    if(saveIdentity?.substringAfterLast('|')?.isNotBlank()==true) { _reportDraft.value="";_reportState.value=ReportUiState() }
                    (_session.value as? LocalDuelSession)?.leave()
                    localCubeHolder.value?.leave()
                    miniJob?.cancel()
                    _mini.value = null
                    localCubeHolder.value = null
                    cubeIsLocal.value = false
                    _session.value = match
                    solo.clear()
                }
                saveIdentity = identity
                viewModelScope.launch { pullWallet() }
            }
        }
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
            while(true) { refreshChallenges();delay(2000) }
        }
        viewModelScope.launch {
            while (true) {
                refreshNotes()
                pullWallet()
                delay(8_000)
            }
        }
    }

    // ------------------------------------------------------------------ online

    fun startDuel(mode: MatchMode) {
        privateMatch=null;_joiningChallenge.value=null
        ensureAccount { token -> _session.value = match; match.start(settings.value.serverUrl, token, mode) }
    }

    fun startCubeDuel(mode: MatchMode) {
        privateMatch=null;_joiningChallenge.value=null
        ensureAccount { token ->
            cubeIsLocal.value = false
            localCubeHolder.value = null
            cubeMatch.start(settings.value.serverUrl, token, mode)
        }
    }

    /**
     * Online play never demands login: logged-in users use their token, everyone else gets a
     * persistent guest account created silently on the server ("guest####") and reused forever.
     */
    private var ensuringSession = false

    private fun ensureAccount(onReady: (String) -> Unit) {
        val s = settings.value
        if (s.authToken.isNotBlank()) {
            onReady(s.authToken)
            return
        }
        if (ensuringSession) return
        ensuringSession = true
        viewModelScope.launch {
            try {
                val pass = s.guestPass.ifBlank { randomSecret() }
                when (val outcome = guestSession(s, pass)) {
                    is AccountClient.Outcome.Ok -> {
                        val auth = outcome.auth
                        settingsStore.update {
                            it.copy(
                                authToken = auth.token,
                                accountName = auth.name,
                                playerName = it.playerName.ifBlank { auth.name },
                                guestName = auth.name,
                                guestPass = pass,
                            )
                        }
                        viewModelScope.launch { pullWallet() }
                        onReady(auth.token)
                    }
                    else -> _banner.value = "guest_failed"
                }
            } finally {
                ensuringSession = false
            }
        }
    }

    /** Reuse the device's guest account, or register one (rerolling on a name clash). */
    private suspend fun guestSession(s: UserSettings, pass: String): AccountClient.Outcome {
        if (s.guestName.isNotBlank() && s.guestPass.isNotBlank()) {
            val again = account.authenticate(s.serverUrl, Login(s.guestName, s.guestPass))
            if (again is AccountClient.Outcome.Ok) return again
        }
        repeat(5) {
            val name = "guest" + kotlin.random.Random.nextInt(1000, 10000)
            val r = account.authenticate(s.serverUrl, Register(name, pass))
            if (r !is AccountClient.Outcome.Failed || r.code != "name_taken") return r
        }
        return AccountClient.Outcome.Failed("name_taken")
    }

    private fun randomSecret(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..16).map { alphabet.random() }.joinToString("")
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
        _joiningChallenge.value=null
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
        if(inviteRematch("cube")) { cubeMatch.dismiss();return }
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
        _reportDraft.value=""
        _reportState.value=ReportUiState()
        settingsStore.update { it.copy(authToken = "", accountName = "", accWins = 0, accLosses = 0, accDraws = 0, accBestScore = 0) }
    }

    private fun saveAccountStats(stats: AccountStats) {
        settingsStore.update { it.copy(accWins = stats.wins, accLosses = stats.losses, accDraws = stats.draws, accBestScore = stats.bestScore) }
    }

    private fun onSessionExpired() {
        // Drop the stale token; the next started match provisions (or re-enters) a session silently.
        settingsStore.update { it.copy(authToken = "") }
        _screen.value = Screen.Home
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
        _joiningChallenge.value=null
        val s = _session.value
        if (s is MatchController) s.cancel() else s.leave()
        _screen.value = Screen.Home
    }

    fun leaveMatch() {
        _session.value.leave()
        _screen.value = Screen.Home
    }

    fun playAgain() {
        if(inviteRematch("2048")) { _session.value.dismiss();return }
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

    fun openSavedGames() { _screen.value = Screen.Home }
    fun openCommunity() { _screen.value = Screen.Community }
    fun openProfile() { _screen.value = Screen.Profile }
    fun syncGames() { viewModelScope.launch { pullWallet() } }

    fun clearChallengeError() { _challengeError.value=null }
    fun openChallenges(game:HubGame?=null,difficulty:String="",opponent:String="") {
        if(settings.value.authToken.isBlank()) { ensureAccount { openChallenges(game,difficulty,opponent) };return }
        _challengeError.value=null
        _screen.value=Screen.Challenges(game,difficulty,opponent)
        refreshChallenges();refreshPlayers()
    }
    fun refreshChallenges() {
        val account=settings.value
        if(account.authToken.isBlank()) { _challenges.value=emptyList();return }
        if(fetchingChallenges) return
        fetchingChallenges=true
        viewModelScope.launch {
            try {
                social.challenges(account.serverUrl,account.authToken).onSuccess { list ->
                    if(settings.value.authToken!=account.authToken || settings.value.serverUrl!=account.serverUrl) return@onSuccess
                    _challenges.value=list
                    privateMatch?.let { previous -> list.firstOrNull { it.id==previous.id }?.let { privateMatch=it } }
                    val selected=list.firstOrNull { it.id==_joiningChallenge.value }
                    if(selected?.status=="playing") {
                        if(selected.game !in listOf("2048","cube")) openPrivateMini(selected)
                        else _joiningChallenge.value=null
                    } else if(selected?.status in listOf("cancelled","declined","expired","completed")) _joiningChallenge.value=null
                }.onFailure { if(_screen.value is Screen.Challenges) _challengeError.value=it.message }
            } finally { fetchingChallenges=false }
        }
    }
    fun createChallenge(game:HubGame,difficulty:String,opponent:String) {
        if(_challengeBusy.value) return
        val account=settings.value
        _challengeBusy.value=true;_challengeError.value=null
        viewModelScope.launch {
            try {
                social.createChallenge(account.serverUrl,account.authToken,ChallengeCreateBody(opponent.trim(),game.gameKey(),difficulty,UUID.randomUUID().toString()))
                    .onSuccess { if(settings.value.authToken==account.authToken) { _challenges.value=listOf(it)+_challenges.value.filterNot { c->c.id==it.id };_screen.value=Screen.Challenges();refreshChallenges() } }
                    .onFailure { _challengeError.value=it.message }
            } finally { _challengeBusy.value=false }
        }
    }
    fun respondChallenge(id:String,action:String) {
        if(_challengeBusy.value) return
        if(action=="cancel" && _joiningChallenge.value==id) _joiningChallenge.value=null
        val account=settings.value
        _challengeBusy.value=true;_challengeError.value=null
        viewModelScope.launch {
            try {
                social.challengeAction(account.serverUrl,account.authToken,id,action).onSuccess { changed ->
                    if(settings.value.authToken!=account.authToken) return@onSuccess
                    _challenges.value=_challenges.value.map { if(it.id==id) changed else it }
                    if(action=="cancel" || action=="decline") { if(_joiningChallenge.value==id) _joiningChallenge.value=null }
                    if(changed.status=="playing" && changed.game !in listOf("2048","cube") && _joiningChallenge.value==id) openPrivateMini(changed)
                }.onFailure { _challengeError.value=it.message }
            } finally { _challengeBusy.value=false;refreshChallenges() }
        }
    }
    fun joinChallenge(challenge:ChallengeView) {
        if(_challengeBusy.value || _joiningChallenge.value!=null) return
        privateMatch=challenge;_joiningChallenge.value=challenge.id;_challengeError.value=null
        val opponent=if(challenge.incoming) challenge.from else challenge.to
        if(challenge.game=="2048") {
            _session.value=match
            match.start(settings.value.serverUrl,settings.value.authToken,MatchMode.PVP,
                resumeMatchId=challenge.roomId.takeIf { challenge.status=="playing" },privateChallengeId=challenge.id,privateOpponent=opponent)
        } else if(challenge.game=="cube") {
            cubeIsLocal.value=false;localCubeHolder.value=null
            cubeMatch.start(settings.value.serverUrl,settings.value.authToken,MatchMode.PVP,
                resumeMatchId=challenge.roomId.takeIf { challenge.status=="playing" },privateChallengeId=challenge.id,privateOpponent=opponent)
        } else if(challenge.status=="playing") openPrivateMini(challenge)
        else respondChallenge(challenge.id,"ready")
    }
    private fun openPrivateMini(challenge:ChallengeView) {
        if(_joiningChallenge.value!=challenge.id) return
        _joiningChallenge.value=null
        val account=settings.value
        viewModelScope.launch {
            social.mini(account.serverUrl,challenge.roomId,account.accountName,account.authToken).onSuccess { room ->
                if(settings.value.authToken!=account.authToken) return@onSuccess
                miniJob?.cancel();miniRoom=room.roomId;miniSolution="";privateMatch=challenge
                _mini.value=MiniUi(gameFromKey(challenge.game).title,room.opponent,room.prompt,room.grid,room.seconds,room.status,
                    solution=room.solution,won=room.winner==account.accountName,mode="pvp",game=when(challenge.game){"word"->SavedGameType.WORD;"word_puzzle"->SavedGameType.WORD_PUZZLE;else->SavedGameType.MATH},
                    difficulty=room.difficulty,choices=room.choices,puzzleId=room.puzzleId,foundWords=room.foundWords,score=room.score,opponentScore=room.opponentScore)
                _screen.value=Screen.MiniPlay;startMiniTimer()
            }.onFailure { _challengeError.value=it.message }
        }
    }
    private fun inviteRematch(game:String):Boolean {
        val previous=privateMatch?.takeIf { it.game==game } ?: return false
        _joiningChallenge.value=null
        miniJob?.cancel()
        openChallenges(gameFromKey(game),previous.difficulty,if(previous.incoming) previous.from else previous.to)
        return true
    }

    fun openGameModes(game: HubGame) { _screen.value = Screen.GameModes(game) }

    fun startHub(game: HubGame, mode: HubMode, difficulty: String) {
        privateMatch=null;_joiningChallenge.value=null
        if (game == HubGame.TILES) {
            when (mode) {
                HubMode.PVP -> startDuel(MatchMode.PVP)
                HubMode.BOT -> startDuel(MatchMode.BOT)
                HubMode.TRAIN -> startTraining(profileFor(difficulty), "Trainer")
                HubMode.WATCH -> openWatch(game)
            }
        } else if (game == HubGame.CUBE) {
            when (mode) {
                HubMode.PVP -> startCubeDuel(MatchMode.PVP)
                HubMode.BOT -> startCubeDuel(MatchMode.BOT)
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
                    val key = if (g == HubGame.WORD_PUZZLE) "word_puzzle" else if (g == HubGame.WORD) "word" else if (g == HubGame.MATH) "math" else if (g == HubGame.TILES) "2048" else "cube"
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
            val server=settings.value.serverUrl
            social.notes(server, 0).onSuccess { fresh ->
                if (settings.value.serverUrl==server) {
                    noteCursor = fresh.maxOfOrNull { it.id } ?: 0L
                    _notes.value = fresh.distinctBy { it.id }.sortedBy { it.at }.takeLast(30)
                }
            }
        }
    }

    fun pvpChatRoom(): String? = when(_screen.value) {
        Screen.Duel -> _session.value.state.value.let { s -> s.matchId?.takeIf { !s.training && s.mode==MatchMode.PVP && s.phase==DuelPhase.PLAYING && s.opponent.info?.isBot==false } }
        Screen.CubeDuel -> cubeUiState.value.let { s -> s.matchId?.takeIf { !cubeIsLocal.value && s.mode==MatchMode.PVP && s.phase==DuelPhase.PLAYING } }
        Screen.MiniPlay -> _mini.value?.let { s -> miniRoom.takeIf { it.isNotBlank() && s.mode=="pvp" && s.status=="playing" } }
        else -> null
    }

    private val _chatError = MutableStateFlow(false)
    val chatError = _chatError.asStateFlow()
    private var displayedChatRoom: String? = null
    fun refreshChat(room: String) {
        if(pvpChatRoom()!=room) return
        if(displayedChatRoom!=room) { displayedChatRoom=room; _chat.value=emptyList(); _chatError.value=false }
        viewModelScope.launch {
            social.chat(settings.value.serverUrl,room,0,settings.value.authToken)
                .onSuccess { if(displayedChatRoom==room && pvpChatRoom()==room) { _chat.value=it;_chatError.value=false } }
                .onFailure { if(displayedChatRoom==room) _chatError.value=true }
        }
    }

    fun sendChat(room: String, text: String) {
        if(pvpChatRoom()!=room || text !in com.duel2048.shared.social.ChatPhrases.all) return
        viewModelScope.launch {
            social.sendChat(settings.value.serverUrl,settings.value.accountName,text,room,settings.value.authToken)
                .onSuccess { refreshChat(room) }.onFailure { _chatError.value=true }
        }
    }

    private val _reportState = MutableStateFlow(ReportUiState())
    val reportState = _reportState.asStateFlow()
    private val _reportDraft = MutableStateFlow("")
    val reportDraft = _reportDraft.asStateFlow()
    fun updateReportDraft(text: String) { _reportDraft.value=text.take(400) }
    fun resetReport() {
        if(!_reportState.value.busy) {
            if(_reportState.value.sent) _reportDraft.value=""
            _reportState.value=ReportUiState()
        }
    }
    fun sendReport(text: String) {
        if(_reportState.value.busy || text.trim().length !in 3..400) return
        if(settings.value.authToken.isBlank()) { _reportState.value=ReportUiState(signInRequired=true);return }
        val account=settings.value
        _reportState.value=ReportUiState(busy=true)
        viewModelScope.launch {
            social.report(account.serverUrl,account.accountName,text.trim(),"support",account.authToken)
                .onSuccess { if(settings.value.authToken==account.authToken && settings.value.serverUrl==account.serverUrl) _reportState.value=ReportUiState(sent=true) }
                .onFailure { if(settings.value.authToken==account.authToken && settings.value.serverUrl==account.serverUrl) _reportState.value=ReportUiState(error=true) }
        }
    }

    fun buyCoins(pack: Int) {
        if(settings.value.authToken.isBlank()) { ensureAccount { buyCoins(pack) }; return }
        viewModelScope.launch {
            social.purchaseCoins(settings.value.serverUrl,settings.value.authToken,UUID.randomUUID().toString(),pack)
                .onSuccess { _purchaseMessage.value = "Purchase request sent. Coins will be credited after payment is confirmed by an admin." }
                .onFailure { _purchaseMessage.value = "Purchase request failed. Please try again." }
            pullWallet()
        }
    }
    private val _purchaseMessage = MutableStateFlow("")
    val purchaseMessage = _purchaseMessage.asStateFlow()

    fun submitMini(answer: String) {
        val s = _mini.value ?: return
        if (s.status != "playing" || s.submitting || s.answered) return
        val submitted = if (s.game == SavedGameType.WORD_PUZZLE) WordGrid.selectedWord(s.grid, s.selectedLetters)?.takeIf { it.length >= 3 } ?: return
            else if (s.game == SavedGameType.WORD) Puzzles.selectedWord(s.grid, s.selectedLetters) ?: return
            else answer.takeIf { it in s.choices } ?: return
        if (s.game == SavedGameType.WORD && s.selectedLetters.size != (if (s.grid.size == 1) s.grid.first().length else s.difficulty.toIntOrNull() ?: 4)) return
        _mini.value = s.copy(submitting = true, answerFailed = false)
        viewModelScope.launch {
            if (miniRoom.isNotBlank()) {
                val name = settings.value.accountName.ifBlank { "Player" }
                val room = social.answerMini(settings.value.serverUrl, miniRoom, name,
                    if (s.game in listOf(SavedGameType.WORD, SavedGameType.WORD_PUZZLE)) "" else submitted, settings.value.authToken, s.selectedLetters).getOrNull()
                val latest = _mini.value ?: return@launch
                if (latest.saveId != s.saveId) return@launch
                if (room == null) { _mini.value = latest.copy(submitting = false, answerFailed = true); return@launch }
                if (latest.status == "over" && room.status != "over") return@launch
                val won = room.winner == name
                _mini.value = latest.copy(status = room.status, won = won, solution = room.solution,
                    answered = room.answered, submitting = false,
                    foundWords = room.foundWords, score = room.score, opponentScore = room.opponentScore,
                    selectedLetters = if (s.game == SavedGameType.WORD_PUZZLE) emptyList() else latest.selectedLetters,
                    invalidWord = s.game == SavedGameType.WORD_PUZZLE && (submitted !in room.foundWords || submitted in s.foundWords))
                if (room.status == "over") rewardOnce("mini-$miniRoom", s.mode, won)
            } else if (s.game == SavedGameType.WORD_PUZZLE) {
                val latest = _mini.value ?: return@launch
                if (latest.saveId != s.saveId || latest.status != "playing") return@launch
                val valid = Puzzles.isGridWord(submitted) && submitted !in latest.foundWords
                val found = if (valid) latest.foundWords + submitted else latest.foundWords
                _mini.value = latest.copy(foundWords = found, score = WordGrid.score(found),
                    selectedLetters = emptyList(), submitting = false, invalidWord = !valid)
            } else {
                val won = submitted.equals(miniSolution, ignoreCase = true)
                _mini.value = s.copy(status = "over", won = won, solution = miniSolution, secondsLeft = 0, submitting = false)
                rewardOnce("mini-${s.saveId}", s.mode, won)
            }
        }
    }

    fun selectMiniLetter(index: Int) {
        val s = _mini.value ?: return
        if (s.game !in listOf(SavedGameType.WORD, SavedGameType.WORD_PUZZLE) || s.status != "playing" || s.submitting || s.answered) return
        val length = if (s.game == SavedGameType.WORD_PUZZLE) 8 else if (s.grid.size == 1) s.grid.first().length else s.difficulty.toIntOrNull() ?: 4
        if (s.selectedLetters.size >= length) return
        if (index !in 0 until s.grid.sumOf(String::length) || index in s.selectedLetters) return
        if (s.game == SavedGameType.WORD_PUZZLE && WordGrid.selectedWord(s.grid, s.selectedLetters + index) == null) return
        _mini.value = s.copy(selectedLetters = s.selectedLetters + index, invalidWord = false)
    }

    fun undoMiniLetter(clear: Boolean = false) {
        val s = _mini.value ?: return
        if (s.status != "playing" || s.submitting || s.answered) return
        _mini.value = s.copy(selectedLetters = if (clear) emptyList() else s.selectedLetters.dropLast(1), invalidWord = false)
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
        _screen.value = Screen.Home
    }

    fun clearBanner() { _banner.value = null }

    fun openWatch(game: HubGame? = null) {
        watchGame = game
        _watchFocus.value = null
        _screen.value = Screen.Watch
        refreshLive()
    }

    private fun startMini(game: HubGame, mode: HubMode, difficulty: String) {
        if (mode != HubMode.TRAIN && settings.value.authToken.isBlank()) {
            ensureAccount { startMini(game, mode, difficulty) }
            return
        }
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
            var initialStatus = "playing"
            var choices = emptyList<String>()
            var puzzleId = ""
            miniRoom = ""
            miniSolution = ""
            if (mode != HubMode.TRAIN) {
                val joinResult = social.joinMini(settings.value.serverUrl, name, if (game == HubGame.WORD_PUZZLE) "word_puzzle" else if (game == HubGame.WORD) "word" else "math", modeId, difficulty, settings.value.authToken)
                val joined=joinResult.getOrNull()
                if (joined != null && joined.roomId.isNotBlank()) {
                    miniRoom = joined.roomId
                    prompt = joined.prompt
                    grid = joined.grid
                    seconds = joined.seconds
                    opponent = joined.opponent
                    initialStatus = joined.status
                    choices = joined.choices
                    puzzleId = joined.puzzleId
                } else {
                    _banner.value = if(joinResult.exceptionOrNull()?.message?.contains("coin",true)==true) "need_coins" else "guest_failed"
                    return@launch
                }
            }
            if (mode == HubMode.TRAIN) {
                if (game == HubGame.WORD_PUZZLE) {
                    grid = Puzzles.wordGrid(difficulty.toInt())
                    prompt = "Find words by connecting letters horizontally or vertically."
                    puzzleId = UUID.randomUUID().toString()
                    seconds = WordGrid.seconds
                    opponent = "Training"
                } else if (game == HubGame.WORD) {
                    val built = Puzzles.word(difficulty.toInt())
                    grid = built.grid
                    miniSolution = built.answer
                    prompt = built.prompt
                    puzzleId = built.id
                    seconds = built.seconds + if (guided) 25 else 0
                } else {
                    val built = Puzzles.math(difficulty)
                    prompt = built.prompt
                    miniSolution = built.answer
                    choices = built.choices
                    puzzleId = built.id
                    seconds = when (difficulty) {
                        "very_hard" -> 90
                        "hard" -> 40
                        "medium" -> 30
                        else -> 20
                    } + if (guided) 20 else 0
                }
            }
            _mini.value = MiniUi(game.title, opponent, prompt, grid, seconds, initialStatus, guided = guided, mode = modeId,
                game = if (game == HubGame.WORD_PUZZLE) SavedGameType.WORD_PUZZLE else if (game == HubGame.WORD) SavedGameType.WORD else SavedGameType.MATH, difficulty = difficulty,
                choices = choices, puzzleId = puzzleId)
            _screen.value = Screen.MiniPlay
            startMiniTimer()
        }
    }

    private fun startMiniTimer() {
        miniJob = viewModelScope.launch {
                while (_mini.value?.status in listOf("waiting", "playing")) {
                    delay(1000)
                    val current = _mini.value ?: return@launch
                    if (miniRoom.isNotBlank()) {
                        val name = settings.value.accountName
                        social.mini(settings.value.serverUrl, miniRoom, name, settings.value.authToken).onSuccess { room ->
                            val latest = _mini.value ?: return@onSuccess
                            if (latest.saveId != current.saveId || latest.status == "over" && room.status != "over") return@onSuccess
                            _mini.value = latest.copy(secondsLeft = room.seconds, status = room.status, prompt = room.prompt, grid = room.grid,
                                opponent = room.opponent, solution = room.solution, won = room.winner == name,
                                choices = room.choices, puzzleId = room.puzzleId, answered = room.answered,
                                foundWords = room.foundWords, score = room.score, opponentScore = room.opponentScore)
                            if (room.status == "over") rewardOnce("mini-$miniRoom", current.mode, room.winner == name)
                        }
                    } else {
                        _mini.value = current.copy(secondsLeft = (current.secondsLeft - 1).coerceAtLeast(0))
                        if (current.secondsLeft <= 1) break
                    }
                }
                val cur = _mini.value ?: return@launch
                if (cur.status == "playing") {
                    val won = cur.game == SavedGameType.WORD_PUZZLE && cur.score > 0
                    val solution = if (cur.game == SavedGameType.WORD_PUZZLE) Puzzles.gridSolutions(cur.grid).joinToString(", ") else miniSolution
                    _mini.value = cur.copy(status = "over", won = won, solution = solution, secondsLeft = 0)
                    rewardOnce("mini-${cur.saveId}", cur.mode, won)
                }
            }
    }

    fun playMiniAgain() {
        val current = _mini.value ?: return
        if(inviteRematch(if(current.game==SavedGameType.WORD) "word" else if(current.game==SavedGameType.WORD_PUZZLE) "word_puzzle" else "math")) return
        startMini(if (current.game == SavedGameType.WORD_PUZZLE) HubGame.WORD_PUZZLE else if (current.game == SavedGameType.WORD) HubGame.WORD else HubGame.MATH,
            if (current.mode == "pvp") HubMode.PVP else if (current.guided) HubMode.TRAIN else HubMode.BOT, current.difficulty)
    }

    private fun profileFor(id: String) = when (id) {
        "easy" -> TrainingProfile.EASY
        "hard" -> TrainingProfile.HARD
        else -> TrainingProfile.NORMAL
    }

    private fun chargeJoinOnce(key: String, kind: String): Boolean {
        // Online entry fees are reserved by the server when a match actually starts.
        viewModelScope.launch { pullWallet() }
        return true
    }

    private fun rewardOnce(key: String, kind: String, won: Boolean) {
        if (!rewarded.add(key)) return
        viewModelScope.launch { pullWallet() }
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

    fun clearError() {
        (_session.value as? MatchController)?.clearError()
        cubeMatch.clearError()
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) = settingsStore.update(transform)

    override fun onCleared() {
        client.disconnect()
        cubeClient.disconnect()
    }
}
