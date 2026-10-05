package com.duel2048.app.cube.model3d

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.duel2048.shared.cube.CubeMove
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import kotlin.math.sqrt

/**
 * GLSurfaceView hosting the hand-built cube. Drag on empty space orbits; drag across a face
 * turns that layer (reported once through [onMove], exactly like a button press);
 * programmatic turns arrive via [CubeModelBridge].
 */
class RubiksCubeView(context: Context) : GLSurfaceView(context) {

    val renderer: CubeModelRenderer
    var onMove: ((CubeMove) -> Unit)? = null
    /** Opponent views render the same cube but accept orbit only, never turns. */
    var gesturesEnabled = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private sealed class Drag
    private object Idle : Drag()
    private class Orbit(var x: Float, var y: Float) : Drag()
    private class Turn(val hit: CubeGeometry.PickHit, val x: Float, val y: Float, var resolved: Boolean = false) : Drag()
    private object Done : Drag()

    private var drag: Drag = Idle

    init {
        renderer = CubeModelRenderer(CubeGeometry.procedural())
        setEGLContextClientVersion(2)
        setEGLConfigChooser(MsaaConfigChooser())
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = if (gesturesEnabled) renderer.pick(event.x, event.y) else null
                drag = if (hit != null) Turn(hit, event.x, event.y) else Orbit(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> when (val d = drag) {
                is Turn -> {
                    val dx = event.x - d.x
                    val dy = event.y - d.y
                    if (!d.resolved && sqrt(dx * dx + dy * dy) > touchSlop) {
                        d.resolved = true
                        // Surface must be live for both picking and projection; pick() already
                        // proved it was at DOWN, but re-check before projecting.
                        if (renderer.projectToScreen(d.hit.point) == null) {
                            drag = Orbit(event.x, event.y)
                        } else {
                            val move = renderer.geometry.resolveDragMove(d.hit, dx, dy) { p ->
                                checkNotNull(renderer.projectToScreen(p))
                            }
                            if (move != null) {
                                drag = Done
                                onMove?.invoke(move)
                            } else {
                                drag = Orbit(event.x, event.y) // middle slice or ambiguous drag
                            }
                        }
                    }
                }
                is Orbit -> {
                    renderer.orbitBy((event.x - d.x) * ORBIT_SPEED, (event.y - d.y) * ORBIT_SPEED)
                    d.x = event.x
                    d.y = event.y
                }
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = Idle
        }
        return true
    }

    /**
     * Requests an RGBA8888 / depth-16 config with 4x MSAA, falling back to the same config
     * without MSAA on devices that do not expose it.
     */
    private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: javax.microedition.khronos.egl.EGLDisplay): EGLConfig {
            for (msaa in booleanArrayOf(true, false)) {
                val attrs = if (msaa) intArrayOf(
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                    EGL10.EGL_SAMPLES, 4,
                    EGL10.EGL_NONE,
                ) else intArrayOf(
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                if (egl.eglChooseConfig(display, attrs, configs, 1, count) && count[0] > 0 && configs[0] != null) {
                    return configs[0]!!
                }
            }
            throw RuntimeException("no suitable EGL config for the cube view")
        }
    }

    private companion object {
        /** Degrees per pixel while orbiting. */
        const val ORBIT_SPEED = 0.45f
    }
}
