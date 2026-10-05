package com.duel2048.app.cube.model3d

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewConfiguration
import com.duel2048.shared.cube.CubeMove
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * The cube: assets/models/rubiks_cube.glb rendered by Filament (PBR, shadows, SSAO, MSAA) on a
 * transparent TextureView. Layer turns move the 27 cubie nodes' transforms ([CubeAnimator]);
 * drag on empty space orbits the camera ([CubeCamera]); drag across a face turns that layer
 * (reported once through [onMove], exactly like a button press). Programmatic turns arrive
 * via [CubeModelBridge]. Rendering only happens while something changes.
 */
class RubiksCubeView(context: Context) : TextureView(context) {

    val geometry = CubeGeometry()
    val animator = CubeAnimator(geometry) { SystemClock.elapsedRealtime() }
    val camera = CubeCamera()
    var onMove: ((CubeMove) -> Unit)? = null
    /** Opponent views render the same cube but accept orbit only, never turns. */
    var gesturesEnabled = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private sealed class Drag
    private object Idle : Drag()
    private class Orbit(var x: Float, var y: Float) : Drag()
    private class Turn(val hit: CubeGeometry.PickHit, val x: Float, val y: Float) : Drag()
    private object Done : Drag()

    private var drag: Drag = Idle

    // ---- Filament ----------------------------------------------------------------------------

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val cameraEntity: Int
    private val filamentCamera: Camera
    private val colorGrading: ColorGrading
    private val indirectLight: IndirectLight
    private val lights: IntArray
    private val materialProvider: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private val asset: FilamentAsset
    /** TransformManager instance of each cubie node, in [CubeGeometry.cubies] order. */
    private val cubieInstances: IntArray

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private val transforms = Array(geometry.cubies.size) { FloatArray(16) }

    /** Something changed since the last presented frame (camera, size, surface). */
    private var dirty = true
    private var running = false
    private var destroyed = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || destroyed) return
            Choreographer.getInstance().postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    init {
        NativeLibs.load()
        isOpaque = false

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        filamentCamera = engine.createCamera(cameraEntity)
        // Exposure 1: light intensities below are relative (a white face under the key light ~ 1).
        filamentCamera.setExposure(1f)

        view.setScene(scene)
        view.setCamera(filamentCamera)
        view.setBlendMode(View.BlendMode.TRANSLUCENT)
        view.setMultiSampleAntiAliasingOptions(View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 })
        view.setAmbientOcclusionOptions(View.AmbientOcclusionOptions().apply { enabled = true })
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Filmic()).build(engine)
        view.setColorGrading(colorGrading)
        renderer.setClearOptions(Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) })

        // Studio lighting fixed to the cube: warm key from above-front (casts the gap shadows),
        // cool fill from below-behind, soft uniform ambient.
        val key = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.97f, 0.92f).intensity(2.6f).direction(-0.35f, -0.85f, -0.40f)
            .castShadows(true).build(engine, key)
        val fill = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.88f, 0.93f, 1f).intensity(0.9f).direction(0.50f, 0.30f, 0.80f)
            .build(engine, fill)
        lights = intArrayOf(key, fill)
        scene.addEntities(lights)
        indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(0.50f, 0.50f, 0.52f)).intensity(1f).build(engine)
        scene.setIndirectLight(indirectLight)

        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        val bytes = context.assets.open(ASSET_PATH).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.rewind() }
        asset = checkNotNull(assetLoader.createAsset(buffer)) { "cube model $ASSET_PATH failed to load" }
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        val rm = engine.renderableManager
        for (e in asset.renderableEntities) {
            val ri = rm.getInstance(e)
            rm.setCastShadows(ri, true)
            rm.setReceiveShadows(ri, true)
        }
        val tm = engine.transformManager
        cubieInstances = IntArray(geometry.cubies.size) { i ->
            val name = geometry.cubies[i].nodeName
            val entity = asset.getFirstEntityByName(name)
            check(entity != 0) { "cube model has no node $name" }
            tm.getInstance(entity)
        }

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                dirty = true
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.setViewport(Viewport(0, 0, width, height))
                camera.setViewport(width, height)
                filamentCamera.setProjection(
                    camera.fovYDegrees.toDouble(), camera.aspect.toDouble(),
                    CubeCamera.NEAR.toDouble(), CubeCamera.FAR.toDouble(), Camera.Fov.VERTICAL,
                )
                dirty = true
            }
        }
        uiHelper.attachTo(this)
    }

    /** Must be called when the view should start drawing (also done on attach). */
    fun onResume() {
        if (running || destroyed) return
        running = true
        dirty = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun onPause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        destroy()
        super.onDetachedFromWindow()
    }

    private fun renderFrame(frameTimeNanos: Long) {
        if (!dirty && !animator.isBusy()) return
        val sc = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        animator.frame(transforms)
        val tm = engine.transformManager
        for (i in transforms.indices) tm.setTransform(cubieInstances[i], transforms[i])
        filamentCamera.setModelMatrix(camera.modelMatrix())
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            dirty = false
        }
    }

    private fun destroy() {
        if (destroyed) return
        destroyed = true
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        engine.flushAndWait()

        assetLoader.destroyAsset(asset)
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        assetLoader.destroy()
        resourceLoader.destroy()
        for (light in lights) {
            engine.destroyEntity(light)
            EntityManager.get().destroy(light)
        }
        engine.destroyIndirectLight(indirectLight)
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    // ---- touch -------------------------------------------------------------------------------

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = if (gesturesEnabled) camera.pickRay(event.x, event.y)?.let { (o, d) -> animator.pick(o, d) } else null
                drag = if (hit != null) Turn(hit, event.x, event.y) else Orbit(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> when (val d = drag) {
                is Turn -> {
                    val dx = event.x - d.x
                    val dy = event.y - d.y
                    if (sqrt(dx * dx + dy * dy) > touchSlop) {
                        val move = if (camera.hasViewport) {
                            geometry.resolveDragMove(d.hit, dx, dy) { p -> checkNotNull(camera.projectToScreen(p)) }
                        } else {
                            null
                        }
                        if (move != null) {
                            drag = Done
                            onMove?.invoke(move)
                        } else {
                            drag = Orbit(event.x, event.y) // middle slice or ambiguous drag
                        }
                    }
                }
                is Orbit -> {
                    camera.orbitBy((event.x - d.x) * ORBIT_SPEED, (event.y - d.y) * ORBIT_SPEED)
                    d.x = event.x
                    d.y = event.y
                    dirty = true
                }
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = Idle
        }
        return true
    }

    /** Filament's JNI libraries, loaded once per process (gltfio links against filament). */
    private object NativeLibs {
        private var loaded = false

        @Synchronized
        fun load() {
            if (loaded) return
            Filament.init()
            Gltfio.init()
            loaded = true
        }
    }

    companion object {
        const val ASSET_PATH = "models/rubiks_cube.glb"
        /** Degrees per pixel while orbiting. */
        private const val ORBIT_SPEED = 0.45f
    }
}
