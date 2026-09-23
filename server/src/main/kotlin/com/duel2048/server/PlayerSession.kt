package com.duel2048.server

import com.duel2048.shared.protocol.PlayerInfo
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.ServerMessage
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One connected human. Outgoing messages go through a channel so ordering is preserved. */
class PlayerSession(val id: String, private val ws: DefaultWebSocketServerSession) {

    @Volatile var name: String = "Player"
    @Volatile var match: Match? = null
    @Volatile var cubeMatch: CubeMatch? = null
    /** Account id once logged in; online play requires it. */
    @Volatile var userId: String? = null

    private val outbox = Channel<ServerMessage>(Channel.UNLIMITED)

    init {
        ws.launch {
            for (msg in outbox) {
                try {
                    ws.send(Frame.Text(Protocol.encode(msg)))
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    val info: PlayerInfo get() = PlayerInfo(id, name, isBot = false)

    fun send(msg: ServerMessage) {
        outbox.trySend(msg)
    }

    fun close() {
        outbox.close()
    }
}
