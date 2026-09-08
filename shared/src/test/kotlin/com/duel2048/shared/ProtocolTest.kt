package com.duel2048.shared

import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.protocol.CancelFindMatch
import com.duel2048.shared.protocol.EndReason
import com.duel2048.shared.protocol.Hello
import com.duel2048.shared.protocol.MatchOver
import com.duel2048.shared.protocol.MoveAck
import com.duel2048.shared.protocol.MoveMsg
import com.duel2048.shared.protocol.PlayerResult
import com.duel2048.shared.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun `client messages round trip with type discriminator`() {
        val msgs = listOf(Hello("Ann"), MoveMsg("m1", 3, Direction.UP), CancelFindMatch)
        for (m in msgs) {
            val text = Protocol.encode(m)
            assertTrue(text.contains("\"type\""), text)
            assertEquals(m, Protocol.decodeClient(text))
        }
    }

    @Test
    fun `server messages round trip including full game state`() {
        val s = GameEngine.newGame(5L)
        val r = GameEngine.move(s, Direction.LEFT)
        val ack = MoveAck("m1", 0, r.state, r.events)
        assertEquals(ack, Protocol.decodeServer(Protocol.encode(ack)))

        val over = MatchOver("m1", null, EndReason.TIME_UP, listOf(PlayerResult("a", 1, 2, 3, 4, 5, 6, 7)))
        val text = Protocol.encode(over)
        assertEquals(over, Protocol.decodeServer(text))
    }
}
