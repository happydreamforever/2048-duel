package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.MySqlStore
import com.duel2048.server.db.CompetitionStore
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class SoftDeleteSqlTest {
    @Test fun `deleted SQL accounts cannot authenticate match or appear in player discovery`() = runBlocking {
        val url = System.getenv("DUEL_TEST_DB_URL") ?: return@runBlocking
        require(url.substringBefore('?').endsWith("_test"))
        val db = MySqlStore(url, System.getenv("DUEL_TEST_DB_USER"), System.getenv("DUEL_TEST_DB_PASSWORD"))
        val dir = Files.createTempDirectory("soft-delete-sql").toFile()
        try {
            val economy = CompetitionStore(db,dir.path)
            val name = "Soft" + UUID.randomUUID().toString().take(7)
            val account = (db.register(name, "pass123") as AuthResult.Ok).user
            val token = checkNotNull(account.token)
            assertTrue(db.isAvailable(account.id))
            assertNotNull(db.authByToken(token))
            db.connection { c -> c.prepareStatement("INSERT INTO admin_deleted_records(entity_type,record_id,deleted_at,changed_at,reason) VALUES ('players',?,?,?,'SQL test')").use { ps ->
                ps.setString(1, account.id); ps.setLong(2, System.currentTimeMillis()); ps.setLong(3, System.currentTimeMillis()); ps.executeUpdate()
            } }
            assertFailsWith<IllegalArgumentException> { economy.enter("deleted-entry", "math", "pvp", listOf(account.id)) }
            assertEquals(100, economy.wallet(account.id).coins)
            assertFalse(db.isAvailable(account.id))
            assertNull(db.authByToken(token))
            assertNull(db.userByName(name))
            assertIs<AuthResult.Failed>(db.login(name, "pass123"))
            assertIs<AuthResult.Failed>(db.register(name, "pass123"))
            assertFalse(name in db.names())
            assertFalse(db.leaderboard(100).any { it.name == name })
            // Preserve statistics when an already-started match finishes after account deletion.
            assertEquals(1, db.updateStats(account.id) { it.copy(matches = it.matches + 1) }?.stats?.matches)
            db.connection { c -> c.prepareStatement("UPDATE admin_deleted_records SET deleted_at=NULL WHERE entity_type='players' AND record_id=?").use { ps -> ps.setString(1,account.id); ps.executeUpdate() } }
            assertTrue(db.isAvailable(account.id))
            assertIs<AuthResult.Ok>(db.login(name, "pass123"))
            assertTrue(name in db.names())
        } finally { db.close(); dir.deleteRecursively() }
    }
}
