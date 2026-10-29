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
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class ChallengeApiTest {
    @Test fun `HTTP invitations authenticate participants and keep private puzzle data private`() = testApplication {
        val dir=Files.createTempDirectory("challenge-api").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        val a=(db.register("Alice","pass1") as AuthResult.Ok).user
        val b=(db.register("Bobby","pass2") as AuthResult.Ok).user
        val e=(db.register("Eve","pass3") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path)),db) }
        suspend fun create(token:String)=client.post("/social/challenges") {
            header(HttpHeaders.Authorization,"Bearer $token");contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(ChallengeCreateBody.serializer(),ChallengeCreateBody("Bobby","word_puzzle","4",UUID.randomUUID().toString())))
        }
        suspend fun action(user:UserRecord,id:String,action:String)=client.post("/social/challenges/$id/$action") { header(HttpHeaders.Authorization,"Bearer ${user.token}") }
        assertEquals(HttpStatusCode.Unauthorized,create("").status)
        val created=create(a.token!!)
        assertEquals(HttpStatusCode.OK,created.status)
        val invite=Protocol.json.decodeFromString(ChallengeView.serializer(),created.bodyAsText())
        val eve=client.get("/social/challenges") { header(HttpHeaders.Authorization,"Bearer ${e.token}") }
        assertTrue(Protocol.json.decodeFromString(ListSerializer(ChallengeView.serializer()),eve.bodyAsText()).isEmpty())
        assertEquals(HttpStatusCode.BadRequest,action(e,invite.id,"accept").status)
        assertEquals(HttpStatusCode.OK,action(b,invite.id,"accept").status)
        assertEquals(HttpStatusCode.OK,action(a,invite.id,"ready").status)
        val ready=action(b,invite.id,"ready")
        val started=Protocol.json.decodeFromString(ChallengeView.serializer(),ready.bodyAsText())
        assertEquals("playing",started.status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/social/mini/${started.roomId}?name=Alice").status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/social/mini/${started.roomId}?name=Alice") { header(HttpHeaders.Authorization,"Bearer ${e.token}") }.status)
        assertEquals(HttpStatusCode.OK,client.get("/social/mini/${started.roomId}") { header(HttpHeaders.Authorization,"Bearer ${a.token}") }.status)
    }

    @Test fun `private engine seats never enter public queues or fall back to a bot`() = testApplication {
        val dir=Files.createTempDirectory("private-engine").toFile()
        val db=JsonDb(File(dir,"users.json")).load()
        val a=(db.register("Alice","pass1") as AuthResult.Ok).user
        val b=(db.register("Bobby","pass2") as AuthResult.Ok).user
        val e=(db.register("Eve","pass3") as AuthResult.Ok).user
        application { module(ServerConfig.fromEnv(mapOf("DB" to "json","DATA_DIR" to dir.path,"BOT_FALLBACK_MS" to "20","COUNTDOWN_SECONDS" to "0")),db) }
        val sockets=createClient { install(WebSockets) }
        for(game in listOf(GameType.DUEL_2048,GameType.CUBE_SOLVE)) {
            val key=if(game==GameType.CUBE_SOLVE) "cube" else "2048"
            val response=client.post("/social/challenges") {
                header(HttpHeaders.Authorization,"Bearer ${a.token}");contentType(ContentType.Application.Json)
                setBody(Protocol.json.encodeToString(ChallengeCreateBody.serializer(),ChallengeCreateBody(b.name,key,"normal",UUID.randomUUID().toString())))
            }
            val invite=Protocol.json.decodeFromString(ChallengeView.serializer(),response.bodyAsText())
            assertEquals(HttpStatusCode.OK,client.post("/social/challenges/${invite.id}/accept") { header(HttpHeaders.Authorization,"Bearer ${b.token}") }.status)
            val initial=wallet(a.token!!)
            sockets.webSocket("/ws") outer@{
                next<Welcome>();send(Frame.Text(Protocol.encode(Auth(a.token!!))));next<AuthOk>()
                send(Frame.Text(Protocol.encode(Hello("SpoofedName",Protocol.VERSION))))
                send(Frame.Text(Protocol.encode(FindMatch(MatchMode.BOT,game,invite.id))))
                assertEquals("challenge_error",next<ErrorMsg>().code)
                send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game,invite.id))))
                assertEquals(0,next<Queued>().botFallbackMs)
                send(Frame.Text(Protocol.encode(Auth(e.token!!))))
                assertEquals("in_match",next<AuthFailed>().code)
                assertNull(withTimeoutOrNull(80) { incoming.receive() })
                assertEquals(initial,wallet(a.token!!))
                sockets.webSocket("/ws") {
                    next<Welcome>();send(Frame.Text(Protocol.encode(Auth(e.token!!))));next<AuthOk>()
                    send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game,invite.id))))
                    assertEquals("challenge_error",next<ErrorMsg>().code)
                    close()
                }
                sockets.webSocket("/ws") {
                    next<Welcome>();send(Frame.Text(Protocol.encode(Auth(b.token!!))));val bob=next<AuthOk>()
                    send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game,invite.id))))
                    val id=if(game==GameType.CUBE_SOLVE) {
                        val found=next<CubeMatchFound>();assertEquals("Alice",found.opponent.name);assertFalse(found.opponent.isBot);found.matchId
                    } else {
                        val found=next<MatchFound>();assertEquals("Alice",found.opponent.name);assertFalse(found.opponent.isBot);found.matchId
                    }
                    assertEquals(initial-10,wallet(a.token!!))
                    val live=client.get("/social/live")
                    assertFalse(Protocol.json.decodeFromString(ListSerializer(LiveGame.serializer()),live.bodyAsText()).any { it.id==id })
                    this@outer.close()
                    sockets.webSocket("/ws") {
                        next<Welcome>();send(Frame.Text(Protocol.encode(Auth(a.token!!))));next<AuthOk>()
                        send(Frame.Text(Protocol.encode(FindMatch(MatchMode.PVP,game,invite.id))))
                        val restored=next<MatchRestored>().game
                        assertEquals(id,restored.id)
                        assertEquals(initial-10,wallet(a.token!!))
                        close()
                    }
                    send(Frame.Text(Protocol.encode(LeaveMatch(id))))
                    if(game==GameType.CUBE_SOLVE) next<CubeMatchOver>() else next<MatchOver>()
                    close()
                }
                close()
            }
            assertEquals(initial+15,wallet(a.token!!))
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.wallet(token:String):Int {
        val response=client.get("/social/wallet") { header(HttpHeaders.Authorization,"Bearer $token") }
        return Protocol.json.decodeFromString(WalletView.serializer(),response.bodyAsText()).coins
    }
    private suspend inline fun <reified T:ServerMessage> DefaultClientWebSocketSession.next():T = withTimeout(10000) {
        while(true) {
            val frame=incoming.receive()
            if(frame is Frame.Text) {
                val message=Protocol.decodeServer(frame.readText())
                if(message is ErrorMsg && message !is T) error(message.message)
                if(message is T) return@withTimeout message
            }
        }
        @Suppress("UNREACHABLE_CODE") error("No response")
    }
}
