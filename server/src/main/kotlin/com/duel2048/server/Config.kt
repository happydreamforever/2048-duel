package com.duel2048.server

import com.duel2048.server.db.DatabaseUrl
import com.duel2048.shared.engine.Rules
import java.io.File

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
    /** "mysql" (default) or "json" for the file-based development store. */
    val dbKind: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    /** Directory holding the JSON database when dbKind == "json". */
    val dataDir: String,
    /** The server.env file that was loaded, if any. */
    val configFile: String?,
) {
    companion object {
        /**
         * Values come from environment variables first, then from a `server.env` file
         * (KEY=VALUE lines) looked up via CONFIG_FILE, ./server.env or ../server.env.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val (file, fileValues) = loadEnvFile(env["CONFIG_FILE"])
            fun get(key: String): String? = env[key]?.takeIf { it.isNotBlank() } ?: fileValues[key]?.takeIf { it.isNotBlank() }
            val conn = DatabaseUrl.parse(get("DB_URL") ?: "jdbc:mysql://127.0.0.1:3306/duel2048")
            return ServerConfig(
                port = get("PORT")?.toIntOrNull() ?: 8080,
                host = get("HOST") ?: "0.0.0.0",
                botFallbackMs = get("BOT_FALLBACK_MS")?.toLongOrNull() ?: 8_000L,
                matchDurationMs = get("MATCH_DURATION_MS")?.toLongOrNull() ?: Rules.MATCH_DURATION_MS,
                countdownSeconds = get("COUNTDOWN_SECONDS")?.toIntOrNull() ?: Rules.COUNTDOWN_SECONDS,
                botIntervalMs = get("BOT_INTERVAL_MS")?.toLongOrNull() ?: 550L,
                botJitterMs = get("BOT_JITTER_MS")?.toLongOrNull() ?: 250L,
                boardSize = get("BOARD_SIZE")?.toIntOrNull() ?: Rules.BOARD_SIZE,
                dbKind = (get("DB") ?: "mysql").lowercase(),
                dbUrl = conn.jdbcUrl,
                dbUser = get("DB_USER") ?: conn.user ?: "duel2048",
                dbPassword = get("DB_PASSWORD") ?: conn.password ?: "duel2048",
                dataDir = get("DATA_DIR") ?: "data",
                configFile = file,
            )
        }

        private fun loadEnvFile(explicit: String?): Pair<String?, Map<String, String>> {
            val candidates = listOfNotNull(explicit, "server.env", "../server.env").map { File(it) }
            val file = candidates.firstOrNull { it.isFile } ?: return null to emptyMap()
            val values = HashMap<String, String>()
            file.readLines().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val key = line.substring(0, eq).trim().removePrefix("export ").trim()
                var value = line.substring(eq + 1).trim()
                if (value.length >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1)
                }
                values[key] = value
            }
            return file.absolutePath to values
        }
    }
}
