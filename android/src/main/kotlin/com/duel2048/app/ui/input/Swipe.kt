package com.duel2048.app.ui.input

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.duel2048.shared.engine.Direction
import kotlin.math.abs

/**
 * Fires one direction per gesture as soon as the finger travels [thresholdPx],
 * which feels snappier than waiting for the drag to end.
 */
fun Modifier.swipeInput(enabled: Boolean = true, thresholdPx: Float = 56f, onSwipe: (Direction) -> Unit): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        var total = Offset.Zero
        var fired = false
        detectDragGestures(
            onDragStart = {
                total = Offset.Zero
                fired = false
            },
            onDrag = { change, amount ->
                change.consume()
                if (!fired && currentEnabled) {
                    total += amount
                    if (abs(total.x) > thresholdPx || abs(total.y) > thresholdPx) {
                        fired = true
                        val dir = if (abs(total.x) > abs(total.y)) {
                            if (total.x > 0) Direction.RIGHT else Direction.LEFT
                        } else {
                            if (total.y > 0) Direction.DOWN else Direction.UP
                        }
                        currentOnSwipe(dir)
                    }
                }
            },
        )
    }
}

/** Arrow keys / WASD for keyboards and emulators. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class) // Key constants are experimental in compose-ui 1.4
fun Modifier.keyboardInput(onMove: (Direction) -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val dir = when (e.key) {
        Key.DirectionUp, Key.W -> Direction.UP
        Key.DirectionDown, Key.S -> Direction.DOWN
        Key.DirectionLeft, Key.A -> Direction.LEFT
        Key.DirectionRight, Key.D -> Direction.RIGHT
        else -> null
    }
    if (dir != null) {
        onMove(dir)
        true
    } else {
        false
    }
}
