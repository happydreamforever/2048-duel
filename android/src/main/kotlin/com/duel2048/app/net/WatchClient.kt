package com.duel2048.app.net

import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.WatchFrame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class WatchClient {
    private val http = OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(15,TimeUnit.SECONDS).build()
    fun frames(server:String,id:String) = callbackFlow {
        val base=LeaderboardClient.httpBase(server).replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        val socket=http.newWebSocket(Request.Builder().url("$base/social/watch/$id").build(),object:WebSocketListener() {
            override fun onMessage(webSocket:WebSocket,text:String) {
                runCatching { Protocol.json.decodeFromString(WatchFrame.serializer(),text) }.onSuccess { trySend(it) }.onFailure { close(it) }
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { close(t) }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { close() }
        })
        awaitClose { socket.close(1000,"Left watch room") }
    }
}
