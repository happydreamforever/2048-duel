package com.duel2048.server

import com.duel2048.server.db.JsonDb
import com.duel2048.server.db.MySqlStore
import com.duel2048.server.db.UserStore
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.ServerStats
import com.duel2048.shared.social.ChatBody
import com.duel2048.shared.social.MiniAnswerBody
import com.duel2048.shared.social.MiniJoinBody
import com.duel2048.shared.social.NotifyBody
import com.duel2048.shared.social.ReportBody
import com.duel2048.shared.social.WalletBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
// Ktor 2.x keeps the historical typo package "callloging"; Ktor 3 renamed it to "calllogging".
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

private val log = LoggerFactory.getLogger("Server")

fun main() {
    val config = ServerConfig.fromEnv()
    log.info("2048 Duel server starting on ${config.host}:${config.port} (bot fallback ${config.botFallbackMs} ms, match ${config.matchDurationMs} ms)")
    val db = createStore(config)
    embeddedServer(Netty, port = config.port, host = config.host) { module(config, db) }.start(wait = false)
    printBanner(config, db)
    // Block the main thread; Ctrl+C stops the process.
    Thread.currentThread().join()
}

const val DB_FILE_NAME = "duel2048-db.json"

/** MySQL by default; DB=json switches to the file store for development. */
fun createStore(config: ServerConfig): UserStore {
    if (config.dbKind == "json") return JsonDb(File(config.dataDir, DB_FILE_NAME)).load()
    return try {
        MySqlStore(config.dbUrl, config.dbUser, config.dbPassword)
    } catch (e: Exception) {
        log.error("Cannot connect to MySQL at ${config.dbUrl} as ${config.dbUser}: ${e.message}")
        println()
        println(" MariaDB/MySQL is not reachable. Check database.json (copy database.json.example next to gradlew):")
        println("""   { "type": "mariadb", "host": "127.0.0.1", "port": 3306, "user": "root", "password": "", "database": "duel2048", "charset": "utf8mb4" }""")
        println(" create the database once with:")
        println("   CREATE DATABASE duel2048 CHARACTER SET utf8mb4;")
        println("   CREATE USER 'duel2048'@'%' IDENTIFIED BY 'duel2048';")
        println("   GRANT ALL PRIVILEGES ON duel2048.* TO 'duel2048'@'%';")
        println(" or set DB=json for a file-based store (no MySQL needed).")
        exitProcess(1)
    }
}

fun Application.module(config: ServerConfig = ServerConfig.fromEnv(), db: UserStore = createStore(config)) {
    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 60_000
        maxFrameSize = 1L shl 20
        masking = false
    }
    install(ContentNegotiation) { json(Protocol.json) }
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader("Content-Type")
        allowHeader("X-Admin-Key")
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val lobby = Lobby(config, appScope, db)
    val social = SocialService(config.dataDir, db, lobby)
    val adminKey = System.getenv("ADMIN_KEY")?.takeIf { it.isNotBlank() } ?: "duel-admin"
    appScope.launch { social.pollLoop() }

    routing {
        get("/") { call.respondText(statusPage(config, lobby.stats()), ContentType.Text.Html) }
        get("/health") { call.respondText("ok") }
        get("/stats") { call.respond(lobby.stats()) }
        get("/leaderboard") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(db.leaderboard(limit))
        }
        get("/social/players") { call.respond(social.players()) }
        get("/social/notifications") {
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            call.respond(social.notifications(since))
        }
        get("/social/live") { call.respond(social.live()) }
        get("/social/chat") {
            val room = call.request.queryParameters["room"] ?: "lobby"
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            call.respond(social.chatSince(room, since))
        }
        get("/social/wallet") {
            val token = call.request.queryParameters["token"].orEmpty()
            val wallet = social.wallet(token)
            if (wallet == null) call.respond(HttpStatusCode.Unauthorized, "bad token")
            else call.respond(wallet)
        }
        get("/social/mini/{id}") {
            val id = call.parameters["id"].orEmpty()
            val name = call.request.queryParameters["name"].orEmpty()
            val room = social.mini(id, name)
            if (room == null) call.respond(HttpStatusCode.NotFound, "no room")
            else call.respond(room)
        }
        post("/social/report") {
            val body = Protocol.json.decodeFromString(ReportBody.serializer(), call.receiveText())
            social.report(body.name.ifBlank { "player" }, body.text, body.game)
            call.respondText("{\"ok\":true}", ContentType.Application.Json)
        }
        post("/social/chat") {
            val body = Protocol.json.decodeFromString(ChatBody.serializer(), call.receiveText())
            val line = social.chat(body.name, body.text, body.room)
            if (line == null) call.respond(HttpStatusCode.BadRequest, "phrase not allowed")
            else call.respond(line)
        }
        post("/social/wallet") {
            val body = Protocol.json.decodeFromString(WalletBody.serializer(), call.receiveText())
            val wallet = social.applyWallet(body.token, body.scoreDelta, body.coinDelta)
            if (wallet == null) call.respond(HttpStatusCode.Unauthorized, "bad token")
            else call.respond(wallet)
        }
        post("/social/mini/join") {
            val body = Protocol.json.decodeFromString(MiniJoinBody.serializer(), call.receiveText())
            call.respond(social.joinMini(body.name, body.game, body.mode, body.difficulty))
        }
        post("/social/mini/answer") {
            val body = Protocol.json.decodeFromString(MiniAnswerBody.serializer(), call.receiveText())
            val room = social.answer(body.roomId, body.name, body.answer)
            if (room == null) call.respond(HttpStatusCode.NotFound, "no room")
            else call.respond(room)
        }
        post("/admin/notify") {
            val key = call.request.headers["X-Admin-Key"] ?: call.request.queryParameters["key"]
            if (key != adminKey) {
                call.respond(HttpStatusCode.Forbidden, "forbidden")
                return@post
            }
            val body = Protocol.json.decodeFromString(NotifyBody.serializer(), call.receiveText())
            call.respond(social.addNote(body.text))
        }
        webSocket(Protocol.WS_PATH) { lobby.handleSession(this) }
    }
}

/** IPv4 addresses of this machine that phones on the same network can use. */
fun lanAddresses(): List<String> = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>() }
        .filter { !it.isLinkLocalAddress && !it.isLoopbackAddress }
        .map { it.hostAddress }
        .distinct()
} catch (e: Exception) {
    emptyList()
}

private fun printBanner(config: ServerConfig, db: UserStore) {
    val port = config.port
    val ws = Protocol.WS_PATH
    val lan = lanAddresses()
    val lines = ArrayList<String>()
    lines += "======================================================================"
    lines += " 2048 Duel server is RUNNING on port $port.  Press Ctrl+C to stop."
    lines += " (Started through Gradle? The 'EXECUTING' progress bar is normal.)"
    lines += ""
    lines += " Server URL to enter in the app's settings (gear icon):"
    lines += "   Android emulator on this PC : ws://10.0.2.2:$port$ws"
    if (lan.isEmpty()) {
        lines += "   Phone on the same Wi-Fi     : ws://<this PC's IPv4 address>:$port$ws"
    } else {
        for (ip in lan) lines += "   Phone on the same Wi-Fi     : ws://$ip:$port$ws"
    }
    lines += ""
    val users = runBlocking { db.userCount() }
    lines += " Database: ${db.description}  ($users users)"
    lines += " Config: " + (config.configFile ?: "no server.env (environment variables only)") + " · database settings: " + (config.dbConfigFile ?: "environment / server.env")
    lines += " Browser check: http://localhost:$port/   (from the phone: http://<PC IP>:$port/)"
    lines += " Phone can't connect? Allow inbound TCP $port in the PC firewall, e.g. on Windows (admin):"
    lines += "   netsh advfirewall firewall add rule name=\"2048 Duel\" dir=in action=allow protocol=TCP localport=$port"
    lines += "======================================================================"
    lines.forEach { println(it) }
    System.out.flush()
}

private fun statusPage(config: ServerConfig, stats: ServerStats): String {
    val port = config.port
    val ws = Protocol.WS_PATH
    val lan = lanAddresses().joinToString("") { "<li><code>ws://$it:$port$ws</code> (phone on the same network)</li>" }
    return """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>2048 Duel server</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#0b0f2a;color:#f5f7ff;margin:0;padding:24px;line-height:1.5}
          h1{color:#00e5ff;margin-top:0} code{background:#1c2244;padding:2px 6px;border-radius:6px}
          .ok{display:inline-block;background:#3dff9a;color:#052;font-weight:700;padding:4px 10px;border-radius:999px}
          li{margin:4px 0} .muted{color:#a8b0cc}
        </style></head><body>
        <h1>2048 Duel server</h1>
        <p><span class="ok">RUNNING</span> &nbsp;protocol v${Protocol.VERSION} &nbsp;|&nbsp; online: ${stats.onlinePlayers}, queued: ${stats.queued}, matches: ${stats.activeMatches} (total ${stats.totalMatches})</p>
        <p>If you can read this page on your phone, the phone can reach the server. Enter one of these in the app's settings:</p>
        <ul>
          <li><code>ws://10.0.2.2:$port$ws</code> (Android emulator on this PC)</li>
          $lan
        </ul>
        <p class="muted">Endpoints: <code>/health</code>, <code>/stats</code>, <code>/leaderboard</code>, WebSocket <code>$ws</code>.</p>
        </body></html>
    """.trimIndent()
}
