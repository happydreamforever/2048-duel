package com.duel2048.app.data

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserSettings(
    val playerName: String = "",
    val serverUrl: String = DEFAULT_SERVER,
    val themeId: String = "neon",
    val haptics: Boolean = true,
    val sound: Boolean = true,
    /** Skips ambient animation, particles and glow layers for weak devices / reduced motion. */
    val lowEffects: Boolean = false,
    val bestSolo: Int = 0,
    /** Training record (offline duels). Online results live in the account stats. */
    val wins: Int = 0,
    val losses: Int = 0,
    /** Account session; blank when logged out. */
    val authToken: String = "",
    val accountName: String = "",
    val accWins: Int = 0,
    val accLosses: Int = 0,
    val accDraws: Int = 0,
    val accBestScore: Int = 0,
    /** Lifetime score. Level is 1 + score / 100. */
    val score: Int = 0,
    val coins: Int = com.duel2048.shared.social.Economy.START_COINS,
    /** BCP-47 tag such as "ko"; blank follows the system language. */
    val language: String = "",
    /** 3D cube renderer: [com.duel2048.app.data.CubeRenderer.MAGIC] or [com.duel2048.app.data.CubeRenderer.CUBE2]. */
    val cubeRenderer: String = CubeRenderer.CUBE2.id,
) {
    val loggedIn: Boolean get() = authToken.isNotBlank()

    companion object {
        /** 10.0.2.2 is the host machine as seen from the Android emulator. */
        const val DEFAULT_SERVER = "ws://10.0.2.2:8080/ws"
    }
}

/** Tiny SharedPreferences-backed settings store exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("duel2048", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings

    /**
     * Device-level QA overrides, usable on release builds:
     *   adb shell settings put global duel2048_server ws://10.0.2.2:8080/ws
     *   adb shell settings put global duel2048_lowfx 1
     * Remove with `adb shell settings delete global <name>`.
     */
    private fun globalOverride(name: String): String? = try {
        Settings.Global.getString(resolver, name)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun load(): UserSettings = loadPrefs().let { s ->
        s.copy(
            serverUrl = globalOverride("duel2048_server") ?: s.serverUrl,
            lowEffects = globalOverride("duel2048_lowfx")?.let { it == "1" || it == "true" } ?: s.lowEffects,
            language = globalOverride("duel2048_lang") ?: s.language,
        )
    }

    private fun loadPrefs(): UserSettings = UserSettings(
        playerName = prefs.getString("name", "") ?: "",
        serverUrl = prefs.getString("server", UserSettings.DEFAULT_SERVER) ?: UserSettings.DEFAULT_SERVER,
        themeId = prefs.getString("theme", "neon") ?: "neon",
        haptics = prefs.getBoolean("haptics", true),
        sound = prefs.getBoolean("sound", true),
        lowEffects = prefs.getBoolean("lowfx", false),
        bestSolo = prefs.getInt("bestSolo", 0),
        wins = prefs.getInt("wins", 0),
        losses = prefs.getInt("losses", 0),
        authToken = prefs.getString("token", "") ?: "",
        accountName = prefs.getString("account", "") ?: "",
        accWins = prefs.getInt("accWins", 0),
        accLosses = prefs.getInt("accLosses", 0),
        accDraws = prefs.getInt("accDraws", 0),
        accBestScore = prefs.getInt("accBest", 0),
        score = prefs.getInt("score", 0),
        coins = if (prefs.contains("coins")) prefs.getInt("coins", com.duel2048.shared.social.Economy.START_COINS) else com.duel2048.shared.social.Economy.START_COINS,
        language = prefs.getString("lang", "") ?: "",
        cubeRenderer = prefs.getString("cubeRenderer", CubeRenderer.CUBE2.id) ?: CubeRenderer.CUBE2.id,
    )

    fun update(transform: (UserSettings) -> UserSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("name", s.playerName)
            .putString("server", s.serverUrl)
            .putString("theme", s.themeId)
            .putBoolean("haptics", s.haptics)
            .putBoolean("sound", s.sound)
            .putBoolean("lowfx", s.lowEffects)
            .putInt("bestSolo", s.bestSolo)
            .putInt("wins", s.wins)
            .putInt("losses", s.losses)
            .putString("token", s.authToken)
            .putString("account", s.accountName)
            .putInt("accWins", s.accWins)
            .putInt("accLosses", s.accLosses)
            .putInt("accDraws", s.accDraws)
            .putInt("accBest", s.accBestScore)
            .putInt("score", s.score)
            .putInt("coins", s.coins)
            .putString("lang", s.language)
            .putString("cubeRenderer", s.cubeRenderer)
            .apply()
    }
}
