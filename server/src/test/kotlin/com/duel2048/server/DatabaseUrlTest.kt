package com.duel2048.server

import com.duel2048.server.db.DatabaseUrl
import com.duel2048.server.db.DbSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseUrlTest {

    @Test
    fun `jdbc urls pass through`() {
        val c = DatabaseUrl.parse("jdbc:mysql://127.0.0.1:3306/duel2048")
        assertEquals("jdbc:mysql://127.0.0.1:3306/duel2048", c.jdbcUrl)
        assertNull(c.user)
    }

    @Test
    fun `cloud uri yields jdbc url, credentials and sslMode`() {
        val c = DatabaseUrl.parse("mysql://avnadmin:AVNS_secret@mysql-abc.aivencloud.com:28650/defaultdb?ssl-mode=REQUIRED")
        assertEquals("jdbc:mysql://mysql-abc.aivencloud.com:28650/defaultdb?sslMode=REQUIRED", c.jdbcUrl)
        assertEquals("avnadmin", c.user)
        assertEquals("AVNS_secret", c.password)
    }

    @Test
    fun `database json settings build driver urls`() {
        val maria = DbSettings.parse("""{ "host": "db.local", "port": 3307, "user": "root", "password": "", "database": "game", "charset": "utf8mb4" }""")
        assertEquals("jdbc:mariadb://db.local:3307/game?connectionCollation=utf8mb4_general_ci", maria.toConnection().jdbcUrl)
        assertEquals("root", maria.toConnection().user)
        val mysql = DbSettings.parse("""{ "type": "mysql", "host": "h", "port": 3306, "user": "u", "password": "p", "database": "d", "charset": "utf8mb4", "ssl": true }""")
        assertEquals("jdbc:mysql://h:3306/d?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&sslMode=REQUIRED", mysql.toConnection().jdbcUrl)
        assertEquals("p", mysql.toConnection().password)
    }

    @Test
    fun `shorthand and defaults`() {
        assertEquals("jdbc:mysql://db.local:3307/game", DatabaseUrl.parse("db.local:3307/game").jdbcUrl)
        assertEquals("jdbc:mysql://h:3306/duel2048", DatabaseUrl.parse("mysql://h").jdbcUrl)
        assertEquals("p%40ss", DatabaseUrl.parse("mysql://u:p%2540ss@h/d").password)
    }
}
