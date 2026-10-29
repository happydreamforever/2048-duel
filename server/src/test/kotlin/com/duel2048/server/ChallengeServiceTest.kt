package com.duel2048.server

import com.duel2048.server.db.*
import com.duel2048.shared.social.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class ChallengeServiceTest {
    @Test fun `all minigames reserve only the chosen opponent and charge once when both join`() = runBlocking {
        for((game,level) in listOf("math" to "medium","word" to "3","word_puzzle" to "6")) fixture { db,lobby,social,challenges,users,advance ->
            val (a,b,e,d)=users
            val body=ChallengeCreateBody("bObBy",game,level,UUID.randomUUID().toString())
            val invite=challenges.create(a,body)
            assertEquals("Bobby",invite.to)
            assertEquals(invite.id,challenges.create(a,body).id)
            assertEquals(invite.id,challenges.create(a,body.copy(requestId=UUID.randomUUID().toString())).id)
            assertTrue(challenges.list(e.id).isEmpty())
            assertFailsWith<IllegalArgumentException> { challenges.action(e,invite.id,"accept") }
            assertFailsWith<IllegalArgumentException> { challenges.action(a,invite.id,"accept") }
            assertFailsWith<IllegalArgumentException> { challenges.action(a,invite.id,"ready") }
            assertEquals(100,lobby.economy.wallet(a.id).coins)
            assertEquals("accepted",challenges.action(b,invite.id,"accept").status)
            val first=challenges.action(a,invite.id,"ready")
            assertTrue(first.youReady);assertFalse(first.opponentReady)
            assertEquals(100,lobby.economy.wallet(a.id).coins)
            val public=social.joinMini(e.name,game,"pvp",level,e.id)
            val started=challenges.action(b,invite.id,"ready")
            assertEquals("playing",started.status)
            assertNotEquals(public.roomId,started.roomId)
            val from=social.mini(started.roomId,a.name)!!
            val to=social.mini(started.roomId,b.name)!!
            assertEquals(b.name,from.opponent);assertEquals(a.name,to.opponent)
            assertEquals(from.grid,to.grid);assertEquals(from.choices,to.choices)
            assertEquals(level,from.difficulty)
            assertEquals(90,lobby.economy.wallet(a.id).coins);assertEquals(90,lobby.economy.wallet(b.id).coins)
            assertEquals(started.roomId,challenges.action(a,invite.id,"ready").roomId)
            assertEquals(90,lobby.economy.wallet(a.id).coins)
            assertFalse(social.canReadMini(started.roomId,null));assertFalse(social.canReadMini(started.roomId,e.id))
            assertTrue(social.canReadMini(started.roomId,a.id));assertTrue(social.canChat(a.id,started.roomId));assertFalse(social.canChat(e.id,started.roomId))
            assertFalse(social.live().any { it.id==started.roomId })
            val outsider=social.joinMini(d.name,game,"pvp",level,d.id)
            assertEquals(public.roomId,outsider.roomId)
            assertNotEquals(started.roomId,outsider.roomId)
            advance()
            assertEquals("playing",challenges.list(a.id).first { it.id==invite.id }.status)
        }
    }
    @Test fun `invalid targets decline cancel expiry and insufficient funds cannot create a paid room`() = runBlocking {
        fixture { db,lobby,social,challenges,users,advance ->
            val (a,b,e)=users
            fun body(target:String="Bobby")=ChallengeCreateBody(target,"math","easy",UUID.randomUUID().toString())
            assertFailsWith<IllegalArgumentException> { challenges.create(a,body("Alice")) }
            assertFailsWith<IllegalArgumentException> { challenges.create(a,body("Missing")) }
            assertFailsWith<IllegalArgumentException> { challenges.create(a,body().copy(difficulty="unknown")) }
            val declined=challenges.create(a,body())
            assertFailsWith<IllegalArgumentException> { challenges.action(e,declined.id,"decline") }
            challenges.action(b,declined.id,"decline")
            assertFailsWith<IllegalArgumentException> { challenges.action(b,declined.id,"accept") }
            val cancelled=challenges.create(a,body())
            challenges.action(a,cancelled.id,"cancel")
            assertFailsWith<IllegalArgumentException> { challenges.action(b,cancelled.id,"accept") }
            val accepted=challenges.create(a,body())
            challenges.action(b,accepted.id,"accept");challenges.action(a,accepted.id,"ready")
            challenges.action(b,accepted.id,"cancel")
            assertFailsWith<IllegalArgumentException> { challenges.action(a,accepted.id,"ready") }
            val expired=challenges.create(a,body())
            advance()
            assertEquals("expired",challenges.list(b.id).first { it.id==expired.id }.status)
            assertFailsWith<IllegalArgumentException> { challenges.action(b,expired.id,"accept") }
            val poor=challenges.create(a,body())
            challenges.action(b,poor.id,"accept");challenges.action(a,poor.id,"ready")
            repeat(10) { lobby.economy.enter("debit-${UUID.randomUUID()}","math","pvp",listOf(b.id)) }
            assertFailsWith<IllegalArgumentException> { challenges.action(b,poor.id,"ready") }
            assertEquals(100,lobby.economy.wallet(a.id).coins)
            assertEquals(0,lobby.economy.wallet(b.id).coins)
            assertEquals(0,social.live().count { !it.demo })
        }
    }
    @Test fun `pending invitations persist across service restarts and accepted engine invitations expire safely`() = runBlocking {
        val dir=Files.createTempDirectory("challenge-restart").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val db=JsonDb(File(dir,"users.json")).load()
            val a=(db.register("Alice","pass1") as AuthResult.Ok).user
            val b=(db.register("Bobby","pass2") as AuthResult.Ok).user
            val lobby=Lobby(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),scope,db)
            val social=SocialService(dir.path,db,lobby)
            val first=ChallengeService(db,lobby,social,dir.path)
            val pending=first.create(a,ChallengeCreateBody(b.name,"word","3",UUID.randomUUID().toString()))
            val engine=first.create(a,ChallengeCreateBody(b.name,"2048","normal",UUID.randomUUID().toString()))
            first.action(b,engine.id,"accept")
            val restored=ChallengeService(db,lobby,social,dir.path)
            assertEquals("pending",restored.list(b.id).first { it.id==pending.id }.status)
            assertEquals("expired",restored.list(b.id).first { it.id==engine.id }.status)
            assertEquals(100,lobby.economy.wallet(a.id).coins)
        } finally { scope.cancel();dir.deleteRecursively() }
    }
    @Test fun `client supplied request IDs cannot force room ID collisions`() = runBlocking {
        fixture { db,lobby,social,challenges,users,advance ->
            val (a,b)=users
            val first=challenges.create(a,ChallengeCreateBody(b.name,"math","easy","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
            val second=challenges.create(a,ChallengeCreateBody(b.name,"math","hard","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab"))
            challenges.action(b,first.id,"accept");challenges.action(b,second.id,"accept")
            challenges.action(a,first.id,"ready")
            val started=challenges.action(b,first.id,"ready")
            val countdown=social.mini(started.roomId,a.name)!!
            assertEquals("waiting",countdown.status)
            assertEquals("",countdown.prompt)
            assertTrue(countdown.grid.isEmpty());assertTrue(countdown.choices.isEmpty())
            assertEquals("waiting",social.answer(countdown.roomId,a.name,"1")!!.status)
            delay(3100)
            val room=social.mini(started.roomId,a.name)!!
            val solution=PuzzleCatalog("/datasets/matches").mathEntries("easy").single { it.id==room.puzzleId }.answer
            social.answer(room.roomId,a.name,solution)
            challenges.action(a,second.id,"ready")
            val other=challenges.action(b,second.id,"ready")
            assertNotEquals(started.roomId,other.roomId)
        }
    }
    @Test fun `SQL invitations survive restart and complete only the intended private match`() = runBlocking {
        val url=System.getenv("DUEL_TEST_DB_URL") ?: return@runBlocking
        require(url.substringBefore('?').endsWith("_test"))
        val dir=Files.createTempDirectory("sql-invitations").toFile()
        val db=MySqlStore(url,System.getenv("DUEL_TEST_DB_USER"),System.getenv("DUEL_TEST_DB_PASSWORD"))
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val suffix=UUID.randomUUID().toString().take(5)
            val a=(db.register("InA$suffix","pass1") as AuthResult.Ok).user
            val b=(db.register("InB$suffix","pass2") as AuthResult.Ok).user
            val lobby=Lobby(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),scope,db)
            val social=SocialService(dir.path,db,lobby)
            val service=ChallengeService(db,lobby,social,dir.path)
            val invite=service.create(a,ChallengeCreateBody(b.name,"word_puzzle","8",UUID.randomUUID().toString()))
            service.action(b,invite.id,"accept")
            val restored=ChallengeService(db,lobby,social,dir.path)
            assertEquals("accepted",restored.list(a.id).first { it.id==invite.id }.status)
            restored.action(a,invite.id,"ready")
            val started=restored.action(b,invite.id,"ready")
            assertEquals("playing",started.status)
            val newSocial=SocialService(dir.path,db,lobby)
            val again=ChallengeService(db,lobby,newSocial,dir.path)
            assertEquals(started.roomId,again.list(a.id).first { it.id==invite.id }.roomId)
            assertFalse(newSocial.canReadMini(started.roomId,null))
            assertEquals(90,lobby.economy.wallet(a.id).coins)
            db.connection { c -> c.prepareStatement("SELECT status FROM pvp_challenges WHERE id=?").use { ps ->
                ps.setString(1,invite.id);ps.executeQuery().use { rs -> assertTrue(rs.next());assertEquals("playing",rs.getString(1)) }
            } }
        } finally { scope.cancel();db.close();dir.deleteRecursively() }
    }

    private suspend fun fixture(test:suspend(UserStore,Lobby,SocialService,ChallengeService,List<UserRecord>,()->Unit)->Unit) {
        val dir=Files.createTempDirectory("direct-matching").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val db=JsonDb(File(dir,"users.json")).load()
            val users=listOf("Alice","Bobby","Eve","Danny").map { (db.register(it,"pass1") as AuthResult.Ok).user }
            val lobby=Lobby(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),scope,db)
            val social=SocialService(dir.path,db,lobby)
            var clock=System.currentTimeMillis()
            val service=ChallengeService(db,lobby,social,dir.path) { clock }
            test(db,lobby,social,service,users) { clock+=121_000 }
        } finally { scope.cancel();dir.deleteRecursively() }
    }
}
