package com.duel2048.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duel2048.app.data.SettingsStore
import com.duel2048.app.data.UserSettings
import com.duel2048.app.game.DuelError
import com.duel2048.app.game.DuelPhase
import com.duel2048.app.game.DuelSession
import com.duel2048.app.game.LocalDuelSession
import com.duel2048.app.game.MatchController
import com.duel2048.app.game.SoloController
import com.duel2048.app.game.TrainingProfile
import com.duel2048.app.net.AccountClient
import com.duel2048.app.net.DuelClient
import com.duel2048.shared.protocol.AccountStats
import com.duel2048.shared.protocol.Accounts
import com.duel2048.shared.protocol.ClientMessage
import com.duel2048.shared.protocol.Login
import com.duel2048.shared.protocol.MatchMode
import com.duel2048.shared.protocol.Register
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Login : Screen
    data object Matchmaking : Screen
    data object Duel : Screen
    data object Result : Screen
    data object Solo : Screen
}

data class LoginUiState(val busy: Boolean = false, val error: DuelError? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    val settings: StateFlow<UserSettings> = settingsStore.settings

    val client = DuelClient()
    private val account = AccountClient()
    val match = MatchController(client, viewModelScope, onStats = ::saveAccountStats, onSessionExpired = ::onSessionExpired)
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

    private var pendingMode: MatchMode? = null

    init {
        viewModelScope.launch {
            _session.flatMapLatest { it.state }.map { it.phase }.distinctUntilChanged().collect { phase ->
                when (phase) {
                    DuelPhase.CONNECTING, DuelPhase.SEARCHING -> _screen.value = Screen.Matchmaking
                    DuelPhase.FOUND, DuelPhase.PLAYING -> _screen.value = Screen.Duel
                    DuelPhase.OVER -> {
                        val s = _session.value.state.value
                        if (s.training && s.result != null) {
                            settingsStore.update { if (s.won) it.copy(wins = it.wins + 1) else if (s.draw) it else it.copy(losses = it.losses + 1) }
                        }
                        delay(1600)
                        if (_session.value.state.value.phase == DuelPhase.OVER) _screen.value = Screen.Result
                    }
                    DuelPhase.IDLE -> if (_screen.value == Screen.Matchmaking || _screen.value == Screen.Duel) _screen.value = Screen.Home
                }
            }
        }
    }

    // ------------------------------------------------------------------ online

    fun startDuel(mode: MatchMode) {
        val s = settings.value
        if (!s.loggedIn) {
            pendingMode = mode
            _login.value = LoginUiState()
            _screen.value = Screen.Login
            return
        }
        _session.value = match
        match.start(s.serverUrl, s.authToken, mode)
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
                    _login.value = LoginUiState()
                    _screen.value = Screen.Home
                    pendingMode?.let { mode ->
                        pendingMode = null
                        startDuel(mode)
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

    fun goHome() {
        val s = _session.value
        if (s.state.value.phase == DuelPhase.OVER) s.dismiss()
        _screen.value = Screen.Home
    }

    fun clearError() = (_session.value as? MatchController)?.clearError()

    fun updateSettings(transform: (UserSettings) -> UserSettings) = settingsStore.update(transform)

    override fun onCleared() {
        client.disconnect()
    }
}
