package com.duel2048.server

import com.duel2048.server.db.DatabaseUrl
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
    fun `shorthand and defaults`() {
        assertEquals("jdbc:mysql://db.local:3307/game", DatabaseUrl.parse("db.local:3307/game").jdbcUrl)
        assertEquals("jdbc:mysql://h:3306/duel2048", DatabaseUrl.parse("mysql://h").jdbcUrl)
        assertEquals("p%40ss", DatabaseUrl.parse("mysql://u:p%2540ss@h/d").password)
    }
}
