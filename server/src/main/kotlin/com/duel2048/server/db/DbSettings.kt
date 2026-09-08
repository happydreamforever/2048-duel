package com.duel2048.server.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Database settings in the shape of `database.json`:
 *
 * {
 *   "type": "mariadb",          // or "mysql"; optional, default mariadb
 *   "host": "127.0.0.1",
 *   "port": 3306,
 *   "user": "root",
 *   "password": "",
 *   "database": "duel2048",
 *   "charset": "utf8mb4",
 *   "ssl": false                 // optional
 * }
 */
@Serializable
data class DbSettings(
    val type: String = "mariadb",
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val user: String = "root",
    val password: String = "",
    val database: String = "duel2048",
    val charset: String = "utf8mb4",
    val ssl: Boolean = false,
) {
    val isMySql: Boolean get() = type.trim().lowercase() == "mysql"

    fun toConnection(): DbConnection {
        val scheme = if (isMySql) "mysql" else "mariadb"
        val params = ArrayList<String>()
        val cs = charset.trim()
        if (isMySql) {
            if (cs.isNotEmpty()) {
                params += "characterEncoding=" + (if (cs.lowercase().startsWith("utf8")) "UTF-8" else cs)
                params += "connectionCollation=${cs}_general_ci"
            }
            if (ssl) params += "sslMode=REQUIRED"
        } else {
            if (cs.isNotEmpty()) params += "connectionCollation=${cs}_general_ci"
            if (ssl) params += "sslMode=trust"
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return DbConnection("jdbc:$scheme://$host:$port/$database$query", user, password)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): DbSettings = json.decodeFromString(serializer(), text)

        /** Looks for database.json via [explicitPath], ./database.json, ../database.json. */
        fun load(explicitPath: String?): Pair<File, DbSettings>? {
            val candidates = listOfNotNull(explicitPath, "database.json", "../database.json").map { File(it) }
            val file = candidates.firstOrNull { it.isFile } ?: return null
            return file.absoluteFile to parse(file.readText())
        }
    }
}
