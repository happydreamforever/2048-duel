package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.JsonDb
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonDbTest {

    private fun tempDb(): File = File.createTempFile("duel2048-db", ".json").also { it.delete() }

    @Test
    fun `register, login, token and stats survive a reload`() = runBlocking<Unit> {
        val file = tempDb()
        val db = JsonDb(file).load()

        val reg = db.register("Ann_01", "secret")
        assertIs<AuthResult.Ok>(reg)
        assertIs<AuthResult.Failed>(db.register("ann_01", "other")).also { assertEquals("name_taken", it.code) }
        assertEquals("invalid_name", (db.register("a!", "secret") as AuthResult.Failed).code)
        assertEquals("invalid_password", (db.register("Bob", "abc") as AuthResult.Failed).code)
        assertEquals("bad_credentials", (db.login("Ann_01", "wrong") as AuthResult.Failed).code)

        val login = db.login("ANN_01", "secret")
        assertIs<AuthResult.Ok>(login)
        val token = assertNotNull(login.user.token)
        assertEquals(login.user.id, db.authByToken(token)?.id)

        db.updateStats(login.user.id) { it.copy(wins = it.wins + 1, matches = it.matches + 1, bestScore = 1234) }

        val reloaded = JsonDb(file).load()
        assertEquals(1, reloaded.userCount())
        val user = reloaded.authByToken(token)
        assertNotNull(user)
        assertEquals(1, user.stats.wins)
        assertEquals(1234, user.stats.bestScore)
        assertTrue(file.readText().contains("passwordHash"))
        assertTrue(!file.readText().contains("secret"), "plain password must never be stored")

        reloaded.logout(user.id)
        assertNull(reloaded.authByToken(token))
        file.delete()
    }

    @Test
    fun `leaderboard sorts by wins then score`() = runBlocking<Unit> {
        val db = JsonDb(tempDb()).load()
        val a = (db.register("Alpha", "pass1") as AuthResult.Ok).user
        val b = (db.register("Beta", "pass1") as AuthResult.Ok).user
        db.updateStats(a.id) { it.copy(wins = 2, bestScore = 500) }
        db.updateStats(b.id) { it.copy(wins = 2, bestScore = 900) }
        val top = db.leaderboard()
        assertEquals(listOf("Beta", "Alpha"), top.map { it.name })
        db.file.delete()
    }
}
