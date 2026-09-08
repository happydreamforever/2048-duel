package com.duel2048.server.db

import java.net.URI
import java.net.URLDecoder

data class DbConnection(val jdbcUrl: String, val user: String?, val password: String?)

/**
 * Parses DB_URL. Accepted forms:
 *  - `jdbc:mysql://host:3306/db?sslMode=REQUIRED` or `jdbc:mariadb://host:3306/db` (JDBC, credentials separate)
 *  - `mysql://user:password@host:3306/db?ssl-mode=REQUIRED` (cloud MySQL URI, credentials inside)
 *  - `host:3306/db` (shorthand, MySQL driver)
 * MariaDB is configured with database.json or the DB_HOST/DB_PORT/... fields instead (see DbSettings).
 */
object DatabaseUrl {

    fun parse(raw: String): DbConnection {
        val s = raw.trim()
        if (s.startsWith("jdbc:")) return DbConnection(s, null, null)
        if (s.startsWith("mysql://", ignoreCase = true)) {
            val uri = URI(s)
            val userInfo = uri.rawUserInfo?.split(":", limit = 2)
            val user = userInfo?.getOrNull(0)?.let { URLDecoder.decode(it, "UTF-8") }
            val password = userInfo?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
            val host = uri.host ?: error("no host in $s")
            val port = if (uri.port > 0) uri.port else 3306
            val db = uri.path.orEmpty().trimStart('/').ifBlank { "duel2048" }
            val params = LinkedHashMap<String, String>()
            uri.rawQuery?.split("&")?.filter { it.isNotBlank() }?.forEach { kv ->
                val parts = kv.split("=", limit = 2)
                params[translateParam(parts[0])] = parts.getOrElse(1) { "" }
            }
            val query = if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
            return DbConnection("jdbc:mysql://$host:$port/$db$query", user, password)
        }
        return DbConnection("jdbc:mysql://$s", null, null)
    }

    /** Provider URIs say `ssl-mode`; MySQL Connector/J wants `sslMode`. */
    private fun translateParam(key: String): String = when (key.lowercase()) {
        "ssl-mode", "sslmode" -> "sslMode"
        else -> key
    }
}
