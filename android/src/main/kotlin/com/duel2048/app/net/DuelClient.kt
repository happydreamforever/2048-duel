package com.duel2048.app.net

import com.duel2048.shared.protocol.ClientMessage
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.ServerMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/** Thin OkHttp WebSocket wrapper speaking the shared JSON protocol. */
class DuelClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val generation = AtomicInteger(0)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _messages = MutableSharedFlow<ServerMessage>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    fun connect(url: String) {
        disconnect()
        val gen = generation.incrementAndGet()
        _connection.value = ConnectionState.Connecting
        val request = try {
            Request.Builder().url(url.trim()).build()
        } catch (e: IllegalArgumentException) {
            _connection.value = ConnectionState.Failed("Invalid server URL")
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen == generation.get()) _connection.value = ConnectionState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation.get()) return
                runCatching { Protocol.decodeServer(text) }.onSuccess { _messages.tryEmit(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen == generation.get()) _connection.value = ConnectionState.Failed(t.message ?: "Connection failed")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen == generation.get()) _connection.value = ConnectionState.Disconnected
            }
        })
    }

    fun send(msg: ClientMessage): Boolean = socket?.send(Protocol.encode(msg)) ?: false

    fun disconnect() {
        generation.incrementAndGet()
        socket?.close(1000, "bye")
        socket = null
        _connection.value = ConnectionState.Disconnected
    }
}
