package com.duel2048.app.net

import com.duel2048.shared.protocol.LeaderboardEntry
import com.duel2048.shared.protocol.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches GET /leaderboard from the same host as the WebSocket URL. */
class LeaderboardClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(serverWsUrl: String, limit: Int = 50): Result<List<LeaderboardEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = httpBase(serverWsUrl) + "/leaderboard?limit=$limit"
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string() ?: error("empty response")
                Protocol.json.decodeFromString(ListSerializer(LeaderboardEntry.serializer()), body)
            }
        }
    }

    companion object {
        /** ws://host:port/ws -> http://host:port */
        fun httpBase(wsUrl: String): String {
            var u = wsUrl.trim()
            u = when {
                u.startsWith("wss://") -> "https://" + u.removePrefix("wss://")
                u.startsWith("ws://") -> "http://" + u.removePrefix("ws://")
                else -> u
            }
            u = u.trimEnd('/')
            if (u.endsWith(Protocol.WS_PATH)) u = u.removeSuffix(Protocol.WS_PATH)
            return u.trimEnd('/')
        }
    }
}
