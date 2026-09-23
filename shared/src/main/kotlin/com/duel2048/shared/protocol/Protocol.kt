package com.duel2048.shared.protocol

import kotlinx.serialization.json.Json

/** JSON wire format. Messages carry a "type" discriminator (see @SerialName in Messages.kt). */
object Protocol {
    const val VERSION = 3
    const val WS_PATH = "/ws"

    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(msg: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), msg)
    fun encode(msg: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), msg)
    fun decodeClient(text: String): ClientMessage = json.decodeFromString(ClientMessage.serializer(), text)
    fun decodeServer(text: String): ServerMessage = json.decodeFromString(ServerMessage.serializer(), text)
}
