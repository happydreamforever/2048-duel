package com.duel2048.server

import com.duel2048.server.db.AuthResult
import com.duel2048.server.db.MySqlStore
import com.duel2048.shared.social.PuzzleCatalog
import kotlinx.coroutines.*
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class MathCatalogSqlTest {
    @Test fun `matches read only valid published SQL problems and keep existing rooms unchanged`() = runBlocking {
        val url=System.getenv("DUEL_TEST_DB_URL") ?: return@runBlocking
        require(url.substringBefore('?').endsWith("_test"))
        val db=MySqlStore(url,System.getenv("DUEL_TEST_DB_USER"),System.getenv("DUEL_TEST_DB_PASSWORD"))
        val dir=Files.createTempDirectory("math-sql").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val prefix="catalog-"+UUID.randomUUID().toString().take(6)
        try {
            db.connection { c ->
                c.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS math_problems(id VARCHAR(100) PRIMARY KEY,difficulty VARCHAR(16),prompt VARCHAR(200),answer VARCHAR(100),choices_json TEXT,state VARCHAR(16),version BIGINT DEFAULT 1,created_at BIGINT,updated_at BIGINT)") }
                c.createStatement().use { it.executeUpdate("UPDATE math_problems SET state='draft' WHERE difficulty='hard'") }
                for ((id,prompt,answer,choices,state) in listOf(
                    listOf("good","600 + 634","1234","[\"1231\",\"1233\",\"1234\",\"1235\"]","active"),
                    listOf("negative","1 - 2","-1","[\"-1\",\"0\",\"1\",\"2\"]","active"),
                    listOf("draft","60 + 60","120","[\"119\",\"120\",\"121\",\"122\"]","draft"),
                    listOf("deleted","60 + 60","120","[\"119\",\"120\",\"121\",\"122\"]","active")
                )) c.prepareStatement("INSERT INTO math_problems(id,difficulty,prompt,answer,choices_json,state,created_at,updated_at) VALUES (?,'hard',?,?,?,?,0,0)").use { ps ->
                    ps.setString(1,"$prefix-$id");ps.setString(2,prompt);ps.setString(3,answer);ps.setString(4,choices);ps.setString(5,state);ps.executeUpdate()
                }
                c.prepareStatement("INSERT INTO admin_deleted_records(entity_type,record_id,deleted_at) VALUES ('math',?,1)").use { ps ->ps.setString(1,"$prefix-deleted");ps.executeUpdate() }
            }
            val catalog=MatchMathCatalog(db,PuzzleCatalog("/datasets/matches"))
            repeat(10) { assertEquals("$prefix-good",catalog.choose("hard").id) }
            val a=(db.register("Ma"+UUID.randomUUID().toString().take(6),"pass1") as AuthResult.Ok).user
            val lobby=Lobby(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),scope,db)
            val social=SocialService(dir.path,db,lobby)
            val room=social.joinMini(a.name,"math","bot","hard",a.id)
            assertEquals("$prefix-good",room.puzzleId)
            assertEquals("600 + 634",room.prompt)
            db.connection { c ->c.prepareStatement("UPDATE math_problems SET state='draft' WHERE id=?").use { ps ->ps.setString(1,"$prefix-good");ps.executeUpdate() } }
            assertFailsWith<IllegalArgumentException> { catalog.choose("hard") }
            assertEquals(room.prompt,social.mini(room.roomId,a.name)!!.prompt)
        } finally {scope.cancel();db.close();dir.deleteRecursively()}
    }
}
