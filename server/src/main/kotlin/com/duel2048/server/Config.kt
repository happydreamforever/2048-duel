package com.duel2048.server

import com.duel2048.shared.engine.Rules

/** Runtime configuration, overridable through environment variables. */
data class ServerConfig(
    val port: Int,
    val host: String,
    /** How long a PVP searcher waits before a bot fills in. */
    val botFallbackMs: Long,
    val matchDurationMs: Long,
    val countdownSeconds: Int,
    val botIntervalMs: Long,
    val botJitterMs: Long,
    val boardSize: Int,
    /** Directory holding the JSON database. */
    val dataDir: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            host = env["HOST"] ?: "0.0.0.0",
            botFallbackMs = env["BOT_FALLBACK_MS"]?.toLongOrNull() ?: 8_000L,
            matchDurationMs = env["MATCH_DURATION_MS"]?.toLongOrNull() ?: Rules.MATCH_DURATION_MS,
            countdownSeconds = env["COUNTDOWN_SECONDS"]?.toIntOrNull() ?: Rules.COUNTDOWN_SECONDS,
            botIntervalMs = env["BOT_INTERVAL_MS"]?.toLongOrNull() ?: 550L,
            botJitterMs = env["BOT_JITTER_MS"]?.toLongOrNull() ?: 250L,
            boardSize = env["BOARD_SIZE"]?.toIntOrNull() ?: Rules.BOARD_SIZE,
            dataDir = env["DATA_DIR"] ?: "data",
        )
    }
}
