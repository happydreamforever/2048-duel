package com.duel2048.app.net

import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.AdminNote
import com.duel2048.shared.social.ChatBody
import com.duel2048.shared.social.ChatLine
import com.duel2048.shared.social.LiveGame
import com.duel2048.shared.social.MiniAnswerBody
import com.duel2048.shared.social.MiniJoinBody
import com.duel2048.shared.social.MiniRoomView
import com.duel2048.shared.social.PlayerPresence
import com.duel2048.shared.social.ReportBody
import com.duel2048.shared.social.WalletBody
import com.duel2048.shared.social.WalletView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SocialClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    suspend fun players(ws: String): Result<List<PlayerPresence>> = get(ws, "/social/players", ListSerializer(PlayerPresence.serializer()))
    suspend fun notes(ws: String, since: Long): Result<List<AdminNote>> =
        get(ws, "/social/notifications?since=$since", ListSerializer(AdminNote.serializer()))
    suspend fun live(ws: String): Result<List<LiveGame>> = get(ws, "/social/live", ListSerializer(LiveGame.serializer()))
    suspend fun chat(ws: String, room: String, since: Long, token: String): Result<List<ChatLine>> =
        get(ws, "/social/chat?room=$room&since=$since", ListSerializer(ChatLine.serializer()), token)
    suspend fun wallet(ws: String, token: String): Result<WalletView> = get(ws, "/social/wallet", WalletView.serializer(),token)

    suspend fun sendChat(ws: String, name: String, text: String, room: String, token: String): Result<ChatLine> =
        post(ws, "/social/chat", Protocol.json.encodeToString(ChatBody.serializer(), ChatBody(token = token, name = name, text = text, room = room)), ChatLine.serializer())

    suspend fun report(ws: String, name: String, text: String, game: String, token: String): Result<Unit> =
        postUnit(ws, "/social/report", Protocol.json.encodeToString(ReportBody.serializer(), ReportBody(token = token, name = name, text = text, game = game)))

    suspend fun purchaseCoins(ws: String, token: String, id: String, coins: Int): Result<com.duel2048.shared.social.CoinPurchase> =
        post(ws,"/social/coins/purchases",Protocol.json.encodeToString(com.duel2048.shared.social.CoinPurchaseBody.serializer(),com.duel2048.shared.social.CoinPurchaseBody(token,id,coins)),com.duel2048.shared.social.CoinPurchase.serializer())

    suspend fun challenges(ws: String, token: String): Result<List<com.duel2048.shared.social.ChallengeView>> =
        get(ws,"/social/challenges",ListSerializer(com.duel2048.shared.social.ChallengeView.serializer()),token)
    suspend fun createChallenge(ws:String,token:String,body:com.duel2048.shared.social.ChallengeCreateBody):Result<com.duel2048.shared.social.ChallengeView> =
        post(ws,"/social/challenges",Protocol.json.encodeToString(com.duel2048.shared.social.ChallengeCreateBody.serializer(),body),com.duel2048.shared.social.ChallengeView.serializer(),token)
    suspend fun challengeAction(ws:String,token:String,id:String,action:String):Result<com.duel2048.shared.social.ChallengeView> =
        post(ws,"/social/challenges/$id/$action","{}",com.duel2048.shared.social.ChallengeView.serializer(),token)

    suspend fun mini(ws: String, roomId: String, name: String, token: String = ""): Result<MiniRoomView> =
        get(ws, "/social/mini/$roomId?name=${java.net.URLEncoder.encode(name, "UTF-8")}", MiniRoomView.serializer(), token)

    suspend fun joinMini(ws: String, name: String, game: String, mode: String, difficulty: String, token: String = ""): Result<MiniRoomView> =
        post(
            ws,
            "/social/mini/join",
            Protocol.json.encodeToString(MiniJoinBody.serializer(), MiniJoinBody(token = token, name = name, game = game, mode = mode, difficulty = difficulty)),
            MiniRoomView.serializer(),
        )

    suspend fun answerMini(ws: String, roomId: String, name: String, answer: String, token: String = "", selectedLetters: List<Int> = emptyList()): Result<MiniRoomView> =
        post(ws, "/social/mini/answer", Protocol.json.encodeToString(MiniAnswerBody.serializer(), MiniAnswerBody(roomId, name, answer, token, selectedLetters)), MiniRoomView.serializer())

    private suspend fun <T> get(ws: String, path: String, serializer: kotlinx.serialization.KSerializer<T>, token: String = ""): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = LeaderboardClient.httpBase(ws) + path
                http.newCall(Request.Builder().url(url).apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }.build()).execute().use { response ->
                    if (!response.isSuccessful) error(response.body?.string()?.take(300) ?: "HTTP ${response.code}")
                    Protocol.json.decodeFromString(serializer, response.body?.string().orEmpty())
                }
            }
        }

    private suspend fun <T> post(ws: String, path: String, payload: String, serializer: kotlinx.serialization.KSerializer<T>, token: String = ""): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = LeaderboardClient.httpBase(ws) + path
                val req = Request.Builder().url(url).apply { if(token.isNotBlank()) header("Authorization","Bearer $token") }.post(payload.toRequestBody(jsonType)).build()
                http.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) error(response.body?.string()?.take(300) ?: "HTTP ${response.code}")
                    Protocol.json.decodeFromString(serializer, response.body?.string().orEmpty())
                }
            }
        }

    private suspend fun postUnit(ws: String, path: String, payload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = LeaderboardClient.httpBase(ws) + path
            val req = Request.Builder().url(url).post(payload.toRequestBody(jsonType)).build()
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error(response.body?.string()?.take(300) ?: "HTTP ${response.code}")
            }
        }
    }
}
