package com.duel2048.app.cube.magic.grafic

import android.content.Context
import android.content.res.Resources
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import com.duel2048.app.cube.CubeWcaMapping
import com.duel2048.app.cube.magic.presentation.cube.CubeViewModel
import com.duel2048.shared.cube.CubeMove

/**
 * Custom [GLSurfaceView] that owns touch-event dispatch and forwards events to
 * [CubeViewModel]. Keeping touch handling here (rather than in the Composable's
 * pointerInput) avoids fighting GL's raw event consumption and keeps the ViewModel
 * free of Android View references.
 */
class CubeSurfaceView(
    context: Context,
    private val viewModel: CubeViewModel,
) : GLSurfaceView(context) {

    private var previousX = 0f
    private var previousY = 0f

    init {
        setEGLContextClientVersion(3)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Engine mutations must run on the GL thread (same thread as [CubeRenderer]). */
    fun runOnGlThread(block: () -> Unit) {
        queueEvent(block)
        requestRender()
    }

    fun applyScramble(viewModel: CubeViewModel, moves: List<CubeMove>) {
        if (moves.isEmpty()) return
        runOnGlThread {
            try {
                for (move in moves) {
                    CubeWcaMapping.applyToEngine(viewModel.engine, move)
                }
                Log.i(TAG, "Applied scramble on GL thread (${moves.size} moves)")
            } catch (e: Exception) {
                Log.e(TAG, "Scramble failed: ${e.message}", e)
            }
        }
    }

    fun applyMove(viewModel: CubeViewModel, move: CubeMove) {
        runOnGlThread {
            try {
                CubeWcaMapping.applyToEngine(viewModel.engine, move)
            } catch (e: Exception) {
                Log.e(TAG, "Move failed: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "CubeGL"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val metrics = Resources.getSystem().displayMetrics
                val viewWidth = if (width > 0) width else metrics.widthPixels
                val viewHeight = if (height > 0) height else metrics.heightPixels

                viewModel.onActionDown(x, y, viewWidth, viewHeight)
            }
            MotionEvent.ACTION_UP -> viewModel.onActionUp(x, y)
            MotionEvent.ACTION_MOVE -> viewModel.onActionMove(x, y, previousX, previousY)
            MotionEvent.ACTION_CANCEL -> viewModel.onActionCancel()
        }

        previousX = x
        previousY = y
        return true
    }
}
