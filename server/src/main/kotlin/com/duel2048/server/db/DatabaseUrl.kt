package com.duel2048.server.db

import java.net.URI
import java.net.URLDecoder

data class DbConnection(val jdbcUrl: String, val user: String?, val password: String?)

/**
 * Accepts the common ways of writing a MySQL or MariaDB address:
 *  - `jdbc:mysql://host:3306/db?sslMode=REQUIRED` or `jdbc:mariadb://host:3306/db` (JDBC, credentials separate)
 *  - `mysql://user:password@host:3306/db?ssl-mode=REQUIRED` or `mariadb://user:password@host/db` (URI, credentials inside)
 *  - `host:3306/db` (shorthand, MySQL driver)
 * The MariaDB driver is picked for `mariadb` schemes, MySQL Connector/J otherwise.
 */
object DatabaseUrl {

    fun parse(raw: String): DbConnection {
        val s = raw.trim()
        if (s.startsWith("jdbc:")) return DbConnection(s, null, null)
        val scheme = when {
            s.startsWith("mysql://", ignoreCase = true) -> "mysql"
            s.startsWith("mariadb://", ignoreCase = true) -> "mariadb"
            else -> null
        }
        if (scheme != null) {
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
                val (key, value) = translateParam(scheme, parts[0], parts.getOrElse(1) { "" })
                params[key] = value
            }
            val query = if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
            return DbConnection("jdbc:$scheme://$host:$port/$db$query", user, password)
        }
        return DbConnection("jdbc:mysql://$s", null, null)
    }

    /**
     * Provider URIs say `ssl-mode=REQUIRED`. MySQL Connector/J wants `sslMode=REQUIRED`;
     * MariaDB Connector/J wants `sslMode=trust|verify-ca|verify-full|disable`.
     */
    private fun translateParam(scheme: String, key: String, value: String): Pair<String, String> {
        if (key.lowercase() != "ssl-mode" && key.lowercase() != "sslmode") return key to value
        if (scheme == "mysql") return "sslMode" to value
        val mapped = when (value.uppercase()) {
            "REQUIRED", "PREFERRED", "TRUST" -> "trust"
            "VERIFY_CA", "VERIFY-CA" -> "verify-ca"
            "VERIFY_IDENTITY", "VERIFY-FULL", "VERIFY_FULL" -> "verify-full"
            "DISABLED", "DISABLE" -> "disable"
            else -> value
        }
        return "sslMode" to mapped
    }
}
