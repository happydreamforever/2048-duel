package com.duel2048.app.net

import com.duel2048.shared.protocol.AuthFailed
import com.duel2048.shared.protocol.AuthOk
import com.duel2048.shared.protocol.ClientMessage
import com.duel2048.shared.protocol.ErrorMsg
import com.duel2048.shared.protocol.Hello
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.protocol.Welcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot login/register handshake on a throwaway connection. */
class AccountClient {

    sealed interface Outcome {
        data class Ok(val auth: AuthOk) : Outcome
        data class Failed(val code: String, val detail: String? = null) : Outcome
    }

    suspend fun authenticate(url: String, request: ClientMessage): Outcome = coroutineScope {
        val client = DuelClient()
        val result = CompletableDeferred<Outcome>()
        val messages = launch {
            client.messages.collect { m ->
                when (m) {
                    is Welcome -> {
                        client.send(Hello("", Protocol.VERSION))
                        client.send(request)
                    }
                    is AuthOk -> result.complete(Outcome.Ok(m))
                    is AuthFailed -> result.complete(Outcome.Failed(m.code, m.message))
                    is ErrorMsg -> result.complete(Outcome.Failed(if (m.code == "version") "version" else "server", m.message))
                    else -> Unit
                }
            }
        }
        val connection = launch {
            client.connection.collect { c ->
                if (c is ConnectionState.Failed) result.complete(Outcome.Failed("connect_failed", c.reason))
            }
        }
        client.connect(url)
        val outcome = withTimeoutOrNull(10_000) { result.await() } ?: Outcome.Failed("timeout")
        messages.cancel()
        connection.cancel()
        client.disconnect()
        outcome
    }
}
