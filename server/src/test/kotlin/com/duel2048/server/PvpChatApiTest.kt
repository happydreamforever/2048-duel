package com.duel2048.server

import com.duel2048.server.db.*
import com.duel2048.shared.protocol.*
import com.duel2048.shared.social.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class PvpChatApiTest {
    @Test fun `mini chat is private to active PvP competitors and accepts only preset phrases`() = testApplication {
        val dir=Files.createTempDirectory("private-pvp-chat").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        val alice=(db.register("Alice","pass1") as AuthResult.Ok).user
        val bob=(db.register("Bobby","pass2") as AuthResult.Ok).user
        val eve=(db.register("Eve","pass3") as AuthResult.Ok).user
        val dan=(db.register("Danny","pass4") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),db) }
        suspend fun join(user:UserRecord,game:String="math",mode:String="pvp",level:String="easy"):MiniRoomView {
            val response=client.post("/social/mini/join") { contentType(ContentType.Application.Json);setBody(Protocol.json.encodeToString(MiniJoinBody.serializer(),MiniJoinBody(user.token!!,user.name,game,mode,level))) }
            assertEquals(HttpStatusCode.OK,response.status)
            return Protocol.json.decodeFromString(MiniRoomView.serializer(),response.bodyAsText())
        }
        suspend fun chat(room:String,text:String="Good luck",token:String=alice.token!!) = client.post("/social/chat") {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(ChatBody.serializer(),ChatBody(token,"SpoofedName",text,room)))
        }
        val waiting=join(alice)
        assertEquals(HttpStatusCode.Forbidden,chat(waiting.roomId).status)
        val playing=join(bob)
        assertEquals(waiting.roomId,playing.roomId)
        assertEquals(HttpStatusCode.Unauthorized,chat(playing.roomId,token="").status)
        assertEquals(HttpStatusCode.Unauthorized,client.get("/social/chat?room=${playing.roomId}").status)
        assertEquals(HttpStatusCode.Forbidden,chat("lobby").status)
        assertEquals(HttpStatusCode.Forbidden,chat("demo-word_puzzle").status)
        assertEquals(HttpStatusCode.Forbidden,chat(playing.roomId,token=eve.token!!).status)
        assertEquals(HttpStatusCode.BadRequest,chat(playing.roomId,"Any arbitrary typed message").status)
        val sent=chat(playing.roomId)
        assertEquals(HttpStatusCode.OK,sent.status)
        assertEquals("Alice",Protocol.json.decodeFromString(ChatLine.serializer(),sent.bodyAsText()).from)
        assertEquals(HttpStatusCode.OK,chat(playing.roomId,"👍",bob.token!!).status)
        val received=client.get("/social/chat?room=${playing.roomId}") { header(HttpHeaders.Authorization,"Bearer ${bob.token}") }
        val messages=Protocol.json.decodeFromString(ListSerializer(ChatLine.serializer()),received.bodyAsText())
        assertEquals(listOf("Good luck","👍"),messages.map { it.text })
        assertEquals(HttpStatusCode.Forbidden,client.get("/social/chat?room=${playing.roomId}") { header(HttpHeaders.Authorization,"Bearer ${eve.token}") }.status)
        val other=join(eve,"word","pvp","3");join(dan,"word","pvp","3")
        assertEquals(HttpStatusCode.Forbidden,chat(other.roomId).status)
        val bot=join(alice,"word_puzzle","bot","4")
        assertEquals(HttpStatusCode.Forbidden,chat(bot.roomId).status)
        val answer=PuzzleCatalog("/datasets/matches").mathEntries("easy").single { it.id==playing.puzzleId }.answer
        client.post("/social/mini/answer") { contentType(ContentType.Application.Json);setBody(Protocol.json.encodeToString(MiniAnswerBody.serializer(),MiniAnswerBody(playing.roomId,"Alice",answer,alice.token!!))) }
        assertEquals(HttpStatusCode.Forbidden,chat(playing.roomId).status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/social/chat?room=${playing.roomId}") { header(HttpHeaders.Authorization,"Bearer ${alice.token}") }.status)
    }

    @Test fun `2048 and cube chat use the actual PvP room and close after forfeiting`() = testApplication {
        val dir=Files.createTempDirectory("websocket-pvp-chat").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        val a=(db.register("Alice","pass1") as AuthResult.Ok).user
        val b=(db.register("Bobby","pass2") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path,"BOT_FALLBACK_MS" to "60000","COUNTDOWN_SECONDS" to "0")),db) }
        val sockets=createClient { install(WebSockets) }
        for(game in listOf(GameType.DUEL_2048,GameType.CUBE_SOLVE)) {
            sockets.webSocket("/ws") {
                next<Welcome>();send(Frame.Text(Protocol.encode(Auth(a.token!!))));next<AuthOk>()
                send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game))));next<Queued>()
                sockets.webSocket("/ws") {
                    next<Welcome>();send(Frame.Text(Protocol.encode(Auth(b.token!!))));next<AuthOk>()
                    send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game))))
                    val id=if(game==GameType.DUEL_2048) next<MatchFound>().matchId else next<CubeMatchFound>().matchId
                    val response=client.post("/social/chat") { contentType(ContentType.Application.Json);setBody(Protocol.json.encodeToString(ChatBody.serializer(),ChatBody(a.token!!,"Eve","GG",id))) }
                    assertEquals(HttpStatusCode.OK,response.status)
                    send(Frame.Text(Protocol.encode(LeaveMatch(id))))
                    if(game==GameType.DUEL_2048) next<MatchOver>() else next<CubeMatchOver>()
                    assertEquals(HttpStatusCode.Forbidden,client.get("/social/chat?room=$id") { header(HttpHeaders.Authorization,"Bearer ${a.token}") }.status)
                    close()
                }
                close()
            }
        }
    }

    @Test fun `support reports bind to the authenticated user and reject empty or oversized input`() = testApplication {
        val dir=Files.createTempDirectory("support-reports").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        val user=(db.register("Alice","pass1") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),db) }
        suspend fun report(token:String,text:String)=client.post("/social/report") { contentType(ContentType.Application.Json);setBody(Protocol.json.encodeToString(ReportBody.serializer(),ReportBody(token,"Spoof",text,"support"))) }
        assertEquals(HttpStatusCode.Unauthorized,report("","Please help").status)
        assertEquals(HttpStatusCode.BadRequest,report(user.token!!,"Hi").status)
        assertEquals(HttpStatusCode.BadRequest,report(user.token!!,"X".repeat(401)).status)
        assertEquals(HttpStatusCode.OK,report(user.token!!,"Please check the connection issue").status)
        val stored=Protocol.json.parseToJsonElement(File(dir,"social.json").readText()).jsonObject["reports"]!!.jsonArray.single().jsonObject
        assertEquals("Alice",stored["name"]!!.jsonPrimitive.content)
    }

    @Test fun `SQL chat support reports and published notifications share admin tables`() = runBlocking {
        val url=System.getenv("DUEL_TEST_DB_URL") ?: return@runBlocking
        require(url.substringBefore('?').endsWith("_test"))
        val dir=Files.createTempDirectory("sql-private-chat").toFile()
        val db=MySqlStore(url,System.getenv("DUEL_TEST_DB_USER"),System.getenv("DUEL_TEST_DB_PASSWORD"))
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val suffix=java.util.UUID.randomUUID().toString().take(5)
            val alice=(db.register("ChA$suffix","pass1") as AuthResult.Ok).user
            val bob=(db.register("ChB$suffix","pass2") as AuthResult.Ok).user
            val lobby=Lobby(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),scope,db)
            fun service()=SocialService(dir.path,db,lobby)
            val room=service().joinMini(alice.name,"math","pvp","easy",alice.id)
            service().joinMini(bob.name,"math","pvp","easy",bob.id)
            val active=service()
            assertTrue(active.canChat(alice.id,room.roomId))
            assertNotNull(active.chat(alice,"Good luck",room.roomId))
            assertEquals(alice.name,service().chatSince(room.roomId,0).single().from)
            val message="SQL support message $suffix"
            active.report(alice.name,message,"support")
            db.connection { c ->
                c.prepareStatement("SELECT COUNT(*) FROM reports WHERE player_name=? AND message=?").use { ps ->
                    ps.setString(1,alice.name);ps.setString(2,message);ps.executeQuery().use { rs -> assertTrue(rs.next());assertEquals(1,rs.getInt(1)) }
                }
                for(status in listOf("published","draft","archived")) c.prepareStatement("INSERT INTO announcements(text,status,published_at,created_at,updated_at) VALUES (?,?,?,?,?)").use { ps ->
                    ps.setString(1,"$status notice $suffix");ps.setString(2,status);ps.setLong(3,System.currentTimeMillis());ps.setLong(4,System.currentTimeMillis());ps.setLong(5,System.currentTimeMillis());ps.executeUpdate()
                }
            }
            val notes=active.notifications(0)
            assertTrue(notes.any { it.text=="published notice $suffix" })
            assertFalse(notes.any { it.text=="draft notice $suffix" || it.text=="archived notice $suffix" })
        } finally { scope.cancel();db.close();dir.deleteRecursively() }
    }

    private suspend inline fun <reified T:ServerMessage> DefaultClientWebSocketSession.next():T = withTimeout(10000) {
        while(true) {
            val frame=incoming.receive()
            if(frame is Frame.Text) {
                val message=Protocol.decodeServer(frame.readText())
                if(message is ErrorMsg) error(message.message)
                if(message is T) return@withTimeout message
            }
        }
        @Suppress("UNREACHABLE_CODE") error("No response")
    }
}
