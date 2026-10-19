package com.duel2048.shared

import com.duel2048.shared.cube.CubeMove
import com.duel2048.shared.cube.CubeState
import com.duel2048.shared.engine.Direction
import com.duel2048.shared.engine.GameEngine
import com.duel2048.shared.protocol.CancelFindMatch
import com.duel2048.shared.protocol.CubeMoveMsg
import com.duel2048.shared.protocol.CubeOpponentProgress
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

    @Test
    fun `middle slice moves replay identically through client and opponent messages`() {
        var authoritative = CubeState.SOLVED
        var opponent = CubeState.SOLVED
        val moves = CubeMove.parseList("M M' M2 E E' E2 S S' S2")
        for ((seq, move) in moves.withIndex()) {
            val sent = CubeMoveMsg("cube1", seq, move.notation())
            val received = Protocol.decodeClient(Protocol.encode(sent)) as CubeMoveMsg
            authoritative = authoritative.apply(checkNotNull(CubeMove.parse(received.move)))

            val progress = CubeOpponentProgress("cube1", seq + 1, authoritative.isSolved(), received.move)
            val shown = Protocol.decodeServer(Protocol.encode(progress)) as CubeOpponentProgress
            opponent = opponent.apply(checkNotNull(CubeMove.parse(shown.move)))
            assertEquals(authoritative.fingerprint(), opponent.fingerprint(), move.notation())
            assertEquals(seq + 1, shown.moves)
        }
    }
}
