package dev.changbak.explorer

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import dev.changbak.explorer.domain.*
import dev.changbak.explorer.rendering.CloudSceneMotion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Real ES 2.0 geometry: indexed sphere meshes, a depth buffer, normals and shaded materials. */
class LandscapeFriend3DView(context: Context, state: TravelState) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private var released = false
    private var paused = false
    private var presentedState = state
    private val errorLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(Color.rgb(71, 94, 121))
        setBackgroundColor(Color.rgb(239, 246, 253))
        visibility = View.GONE
    }
    private val scene = FriendRenderer(state) { reason ->
        main.post {
            if (!released) {
                renderError = reason
                errorLabel.text = "3D 친구를 준비하지 못했어요.\n앱을 다시 열어 주세요."
                errorLabel.visibility = View.VISIBLE
                contentDescription = "3D 친구 표시 오류"
            }
        }
    }
    private val surface = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(scene)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    var renderError: String? = null
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (released || paused || !isAttachedToWindow) return
            surface.requestRender()
            main.postDelayed(this, if (scene.isAnimating) 16L else 33L)
        }
    }

    init {
        contentDescription = "풍경을 기억하는 입체 친구"
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(errorLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setSpeaking(value: Boolean) { scene.speaking = value; if (!released && !paused) surface.requestRender() }
    fun updateState(next: TravelState, animate: Boolean = true) {
        if (released || presentedState == next) return
        presentedState = next
        val smooth = animate && ValueAnimator.areAnimatorsEnabled()
        surface.queueEvent { scene.updateState(next, SystemClock.uptimeMillis(), smooth) }
        if (!paused) surface.requestRender()
    }
    fun setListening(value: Boolean) { scene.listening = value; if (!released && !paused) surface.requestRender() }
    fun setMood(value: String) {
        scene.mood = value.trim().lowercase(Locale.ROOT)
        if (!released && !paused) surface.requestRender()
    }
    fun pauseScene() {
        if (released || paused) return
        paused = true
        main.removeCallbacks(tick)
        scene.speaking = false; scene.listening = false
        surface.onPause()
    }
    fun resumeScene() {
        if (released) return
        if (paused) { paused = false; surface.onResume() }
        main.removeCallbacks(tick)
        if (isAttachedToWindow) main.post(tick)
    }
    fun releaseScene() {
        if (released) return
        main.removeCallbacksAndMessages(null)
        scene.speaking = false; scene.listening = false
        if (!paused) surface.onPause()
        paused = true; released = true
        // GLSurfaceView destroys its rendering thread/context when removed from the view tree.
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!released && !paused) main.post(tick) }
    override fun onDetachedFromWindow() { releaseScene(); super.onDetachedFromWindow() }
}

/** Shares the production mesh/shaders with an EGL pbuffer render check. */
fun createLandscapeRendererForCheck(state: TravelState, onError: (String) -> Unit): GLSurfaceView.Renderer =
    FriendRenderer(state, fixedTime = 1f, onFailure = onError)

fun createCloudTransitionRendererForCheck(from: TravelState, to: TravelState, progress: Float, onError: (String) -> Unit): GLSurfaceView.Renderer =
    FriendRenderer(from, fixedTime = 1f, fixedMotionTime = (CloudSceneMotion.DURATION_MS * progress.coerceIn(0f, 1f)).toLong(), onFailure = onError)
        .apply { updateState(to, 0, true) }

private class FriendRenderer(private var state: TravelState, private val fixedTime: Float? = null,
    private val fixedMotionTime: Long? = null, private val onFailure: (String) -> Unit) : GLSurfaceView.Renderer {
    @Volatile var speaking = false
    @Volatile var listening = false
    @Volatile var mood = "neutral"
    @Volatile var isAnimating = false
        private set
    private val motion = CloudSceneMotion(state.cloudWorld)
    fun updateState(next: TravelState, now: Long, animate: Boolean) {
        motion.retarget(next.cloudWorld, now, animate)
        state = next
        isAnimating = motion.isMoving(now)
    }
    private var latest = state.memories.associateBy { it.trait.part }
    private var form = state.body?.form ?: CloudForm.CLOUD
    private var features = state.body?.features.orEmpty()
    private var paintSpeaking = false
    private var paintListening = false
    private var paintMood = "neutral"
    private var paintFace = true
    private var paintAlpha = 1f
    private val sphere = SphereMesh(18, 28)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val root = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private val inverse = FloatArray(16)
    private val normal = FloatArray(16)
    private var program = 0
    private var positionLoc = -1
    private var normalLoc = -1
    private var mvpLoc = -1
    private var mvLoc = -1
    private var normalMatrixLoc = -1
    private var colorLoc = -1
    private var layerFramebuffer = 0
    private var layerTexture = 0
    private var layerDepth = 0
    private var width = 1
    private var height = 1
    private var compositeProgram = 0
    private var quadPosition = -1
    private var quadUv = -1
    private var textureLoc = -1
    private var opacityLoc = -1
    private val quad = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f,
            -1f,1f,0f,1f, 1f,-1f,1f,0f, 1f,1f,1f,1f)); position(0)
    }
    private var failed = false
    private var firstFrame = true
    private val white = floatArrayOf(0.96f, 0.985f, 1f, 1f)
    private val ink = floatArrayOf(0.12f, 0.22f, 0.32f, 1f)
    private val green = floatArrayOf(0.48f, 0.79f, 0.55f, 1f)
    private val blue = floatArrayOf(0.49f, 0.78f, 0.94f, 1f)
    private val paleBlue = floatArrayOf(0.76f, 0.89f, 0.98f, 1f)
    private val gold = floatArrayOf(1f, 0.83f, 0.48f, 1f)
    private val pink = floatArrayOf(0.97f, 0.64f, 0.67f, 1f)
    private val bodyColor get() = when (latest[FriendPart.COLOR]?.trait) {
        FriendTrait.GREEN -> floatArrayOf(0.79f, 0.94f, 0.81f, 1f)
        FriendTrait.BLUE -> floatArrayOf(0.73f, 0.88f, 1f, 1f)
        FriendTrait.WARM_LIGHT -> floatArrayOf(1f, 0.92f, 0.71f, 1f)
        else -> white
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        failed = false; firstFrame = true
        try {
            GLES20.glClearColor(0.925f, 0.959f, 0.989f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            // Culling is unnecessary for these closed, low-count meshes and avoids seam winding issues.
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            compositeProgram = linkProgram(QUAD_VERTEX, QUAD_FRAGMENT)
            quadPosition = GLES20.glGetAttribLocation(compositeProgram, "aPosition")
            quadUv = GLES20.glGetAttribLocation(compositeProgram, "aUv")
            textureLoc = GLES20.glGetUniformLocation(compositeProgram, "uTexture")
            opacityLoc = GLES20.glGetUniformLocation(compositeProgram, "uOpacity")
            layerFramebuffer = 0; layerTexture = 0; layerDepth = 0
            positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
            normalLoc = GLES20.glGetAttribLocation(program, "aNormal")
            mvpLoc = GLES20.glGetUniformLocation(program, "uMvp")
            mvLoc = GLES20.glGetUniformLocation(program, "uModelView")
            normalMatrixLoc = GLES20.glGetUniformLocation(program, "uNormalMatrix")
            colorLoc = GLES20.glGetUniformLocation(program, "uColor")
            check(listOf(positionLoc, normalLoc, mvpLoc, mvLoc, normalMatrixLoc, colorLoc).all { it >= 0 }) { "Required shader input was not linked" }
            Matrix.setLookAtM(view, 0, 0f, 0.35f, 6.5f, 0f, 0f, 0f, 0f, 1f, 0f)
            Log.i(TAG, "ES2 shader compile/link succeeded; sphere vertices=${sphere.vertexCount}, triangles=${sphere.indexCount / 3}; depth=16")
        } catch (error: Exception) { fail(error) }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        this.width = max(width, 1); this.height = max(height, 1)
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(height, 1)
        // Keep the whole character visible in both tall and wide app cards.
        val vertical = if (aspect < 1f) 1.55f / aspect else 1.55f
        Matrix.frustumM(projection, 0, -vertical * aspect, vertical * aspect, -vertical, vertical, 4f, 12f)
        try { prepareLayerBuffer() } catch (error: Exception) { fail(error) }
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.925f, 0.959f, 0.989f, 1f)
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (failed || program == 0) return
        try {
            GLES20.glUseProgram(program)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            val time = fixedTime ?: (SystemClock.uptimeMillis() / 1000f)
            val world = state.cloudWorld
            if (world == null) {
                latest = state.memories.associateBy { it.trait.part }
                Matrix.setIdentityM(root, 0)
                ball(0f, -1.25f, -0.10f, 1.30f, 0.035f, 0.69f, paleBlue)
                drawFigure(state.body ?: CloudFriend(), time, 0f, 0f, 1f, true, true)
            } else {
                // An empty new sky contains no default avatar or pre-filled decorative clouds.
                latest = emptyMap()
                val now = fixedMotionTime ?: SystemClock.uptimeMillis()
                isAnimating = motion.isMoving(now)
                motion.frame(now).forEach { cloud ->
                    beginLayer()
                    drawFigure(cloud.figure, time + cloud.cloudId.coerceAtLeast(0) * 0.11f,
                        cloud.x, cloud.y, cloud.scale, cloud.face > 0f, cloud.active > 0.5f,
                        cloud.z, cloud.face)
                    compositeLayer(cloud.opacity)
                }
            }
            if (firstFrame) {
                val error = GLES20.glGetError()
                check(error == GLES20.GL_NO_ERROR) { "First frame GL error: $error" }
                firstFrame = false
                Log.i(TAG, "First 3D frame rendered: form=$form, cloudCount=${world?.clouds?.size ?: -1}; indexed meshes, depth and lighting enabled")
            }
        } catch (error: Exception) { fail(error) }
    }

    private data class Face(val x: Float = 0f, val y: Float = 0.08f, val z: Float = 0.67f, val mouthZ: Float = z)

    private fun drawFigure(figure: CloudFriend, time: Float, x: Float, y: Float, scale: Float, faceVisible: Boolean, active: Boolean,
        z: Float = if (active) 0f else -0.35f, faceAlpha: Float = if (faceVisible) 1f else 0f) {
        form = figure.form; features = figure.features
        paintFace = faceVisible
        paintSpeaking = active && speaking
        paintListening = active && listening
        paintMood = if (active) mood else "neutral"
        val excited = paintMood == "excited" || paintMood == "신남"
        val sleepy = paintMood == "sleepy" || paintMood == "졸림"
        val breath = 1f + 0.018f * sin(time * if (excited) 3.0f else 1.7f)
        Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, x, y + 0.06f * sin(time * 1.25f) + if (excited) 0.035f * sin(time * 4f) else 0f, z)
        Matrix.rotateM(root, 0, -9f + 6f * sin(time * 0.43f), 0f, 1f, 0f)
        Matrix.rotateM(root, 0, if (paintListening) 7f + 1.7f * sin(time * 1.2f) else if (sleepy) -5f else 1.6f * sin(time * 0.7f), 0f, 0f, 1f)
        val size = scale * when { CloudFeature.BIG in features -> 1.08f; CloudFeature.SMALL in features -> 0.84f; else -> 1f }
        Matrix.scaleM(root, 0, size * breath * if (CloudFeature.LONG in features) 1.12f else 1f, size * breath, size * breath)
        paintAlpha = 1f
        val face = drawBody(time)
        drawTraits(time)
        if (faceVisible) {
            paintAlpha = faceAlpha
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            drawFace(face, time, sleepy)
            GLES20.glDisable(GLES20.GL_BLEND)
            paintAlpha = 1f
        }
    }

    private fun drawBody(time: Float): Face {
        when (form) {
            CloudForm.WHALE -> {
                ball(-0.18f, 0f, 0f, 1.12f, 0.62f, 0.68f, bodyColor)
                ball(-0.83f, 0.05f, 0.04f, 0.48f, 0.53f, 0.56f, bodyColor)
                ball(0.92f, -0.10f, -0.12f, 0.38f, 0.27f, 0.30f, bodyColor, -18f)
                ball(1.20f, 0.11f, -0.19f, 0.21f, 0.41f, 0.15f, paleBlue, -38f)
                ball(1.47f, 0.16f, -0.18f, 0.19f, 0.38f, 0.14f, paleBlue, 35f)
                ball(-0.48f, -0.48f, 0.18f, 0.16f, 0.35f, 0.27f, paleBlue, -40f)
                ball(0.44f, -0.47f, 0.10f, 0.16f, 0.33f, 0.27f, paleBlue, 38f)
                ball(-0.40f, 0.82f, 0f, 0.045f, 0.18f, 0.045f, blue, -20f)
                ball(-0.26f, 0.87f, 0f, 0.045f, 0.22f, 0.045f, blue, 20f)
                return Face(-0.36f, 0.06f, 0.69f)
            }
            CloudForm.DRAGON, CloudForm.DINOSAUR -> {
                ball(0.06f, -0.31f, 0f, 0.77f, 0.59f, 0.58f, bodyColor)
                ball(-0.28f, 0.25f, 0f, 0.40f, 0.70f, 0.40f, bodyColor, -12f)
                ball(-0.37f, 0.65f, 0.08f, 0.52f, 0.44f, 0.45f, bodyColor)
                ball(-0.37f, 0.47f, 0.51f, 0.38f, 0.19f, 0.29f, bodyColor)
                for (side in listOf(-1f, 1f)) {
                    ball(side * 0.42f, -0.86f, 0.12f, 0.24f, 0.22f, 0.33f, paleBlue)
                    ball(-0.37f + side * 0.32f, 1.06f, -0.01f, 0.09f, 0.27f, 0.10f, gold, side * -23f)
                }
                curvedTail(bodyColor, 0.63f, -0.48f, false)
                if (form == CloudForm.DRAGON) wings(time, true)
                return Face(-0.37f, 0.70f, 0.52f, 0.81f)
            }
            CloudForm.RABBIT -> {
                ball(0f, -0.32f, 0f, 0.62f, 0.65f, 0.54f, bodyColor)
                ball(0f, 0.34f, 0.04f, 0.60f, 0.52f, 0.54f, bodyColor)
                if (FriendPart.EARS !in latest) for (side in listOf(-1f, 1f)) {
                    ball(side * 0.29f, 1.04f, -0.03f, 0.17f, 0.60f, 0.18f, bodyColor, side * -10f)
                    ball(side * 0.29f, 1.06f, 0.125f, 0.085f, 0.43f, 0.045f, pink, side * -10f)
                }
                paws(); ball(0.64f, -0.49f, -0.22f, 0.25f, 0.25f, 0.25f, bodyColor)
                return Face(0f, 0.36f, 0.59f)
            }
            CloudForm.CAT, CloudForm.DOG -> {
                ball(0f, -0.31f, 0f, 0.65f, 0.62f, 0.53f, bodyColor)
                ball(0f, 0.32f, 0.05f, 0.62f, 0.51f, 0.55f, bodyColor)
                if (FriendPart.EARS !in latest) for (side in listOf(-1f, 1f)) {
                    if (form == CloudForm.CAT) ball(side * 0.40f, 0.81f, -0.02f, 0.18f, 0.33f, 0.19f, bodyColor, side * -20f)
                    else ball(side * 0.59f, 0.25f, 0.02f, 0.19f, 0.43f, 0.21f, paleBlue, side * 14f)
                }
                paws()
                return Face(0f, 0.37f, 0.61f)
            }
            CloudForm.BIRD -> {
                ball(0f, 0f, 0f, 0.77f, 0.73f, 0.63f, bodyColor)
                wings(time, false)
                ball(0f, -0.05f, 0.68f, 0.15f, 0.095f, 0.17f, gold)
                return Face(0f, 0.20f, 0.64f, 0.82f)
            }
            CloudForm.TURTLE -> {
                ball(0f, -0.30f, -0.17f, 0.96f, 0.55f, 0.78f, bodyColor)
                ball(0f, 0.28f, 0.47f, 0.45f, 0.41f, 0.45f, paleBlue)
                for (side in listOf(-1f, 1f)) { ball(side * 0.73f, -0.76f, 0.25f, 0.25f, 0.18f, 0.28f, paleBlue); ball(side * 0.68f, -0.68f, -0.63f, 0.20f, 0.17f, 0.23f, paleBlue) }
                return Face(0f, 0.33f, 0.93f)
            }
            CloudForm.BREAD -> {
                for (x in listOf(-0.46f, 0f, 0.46f)) ball(x, 0f, 0f, 0.37f, 0.73f, 0.55f, bodyColor)
                return Face(0f, 0.06f, 0.57f)
            }
            CloudForm.SHIP -> {
                ball(0f, -0.44f, 0f, 1.03f, 0.34f, 0.52f, bodyColor)
                ball(0f, 0.31f, -0.10f, 0.055f, 0.92f, 0.055f, paleBlue)
                ball(-0.36f, 0.43f, -0.11f, 0.35f, 0.52f, 0.10f, white, -17f)
                ball(0.32f, 0.28f, -0.10f, 0.28f, 0.39f, 0.10f, gold, 19f)
                return Face(0f, -0.35f, 0.53f)
            }
            CloudForm.HEART -> {
                ball(-0.36f, 0.32f, 0f, 0.54f, 0.51f, 0.52f, bodyColor, -20f)
                ball(0.36f, 0.32f, 0f, 0.54f, 0.51f, 0.52f, bodyColor, 20f)
                ball(0f, -0.20f, 0f, 0.57f, 0.59f, 0.52f, bodyColor)
                ball(0f, -0.61f, 0f, 0.23f, 0.31f, 0.29f, bodyColor)
                return Face(0f, 0.10f, 0.56f)
            }
            else -> {
                ball(0f, -0.06f, 0f, 0.83f, 0.59f, 0.63f, bodyColor)
                ball(-0.70f, -0.02f, -0.03f, 0.47f, 0.46f, 0.48f, bodyColor)
                ball(0.70f, -0.01f, -0.03f, 0.47f, 0.44f, 0.49f, bodyColor)
                ball(-0.31f, 0.40f, -0.11f, 0.49f, 0.47f, 0.48f, bodyColor)
                ball(0.33f, 0.35f, -0.10f, 0.46f, 0.43f, 0.45f, bodyColor)
                return Face()
            }
        }
    }

    private fun drawTraits(time: Float) {
        latest[FriendPart.EARS]?.let {
            val sway = it.trait == FriendTrait.SWAY_EARS
            val top = if (form == CloudForm.RABBIT) 1.06f else if (form == CloudForm.DRAGON || form == CloudForm.DINOSAUR) 1.12f else 0.91f
            val center = if (form == CloudForm.DRAGON || form == CloudForm.DINOSAUR) -0.35f else 0f
            for (side in listOf(-1f, 1f)) {
                val angle = side * -28f + if (sway) 4f * sin(time * 1.25f + side) else 0f
                ball(center + side * 0.43f, top, -0.02f, 0.20f, 0.48f, 0.13f, green, angle)
                ball(center + side * 0.43f, top, 0.108f, 0.025f, 0.35f, 0.025f, paleBlue, angle)
            }
        }
        latest[FriendPart.TAIL]?.let { memory ->
            val color = if (memory.source in listOf(ScenerySource.RIVER, ScenerySource.SEA, ScenerySource.WATER)) blue else gold
            curvedTail(color, 0.70f, -0.25f, memory.trait == FriendTrait.LONG_TAIL)
            if (memory.source == ScenerySource.SEA) {
                ball(1.57f, 0.18f, -0.32f, 0.18f, 0.34f, 0.13f, color, -35f)
                ball(1.83f, 0.20f, -0.32f, 0.18f, 0.34f, 0.13f, color, 34f)
            }
            if (memory.trait == FriendTrait.SPARKLE_TAIL) {
                ball(1.29f, -0.25f, 0.05f, 0.047f, 0.047f, 0.047f, white)
                ball(1.61f, -0.02f, -0.16f, 0.04f, 0.04f, 0.04f, white)
            }
        }
        if ((FriendPart.WINGS in latest || CloudFeature.WINGS in features) && form != CloudForm.DRAGON && form != CloudForm.BIRD) wings(time, false)
        if (latest[FriendPart.STEP]?.trait == FriendTrait.SWAY_STEP) {
            for (side in listOf(-1f, 1f)) ball(side * 0.43f, -0.84f + 0.025f * sin(time * 1.7f + side), 0.27f, 0.27f, 0.17f, 0.34f, paleBlue)
        }
        if (CloudFeature.LONG_TAIL in features && FriendPart.TAIL !in latest && form != CloudForm.DRAGON && form != CloudForm.DINOSAUR) curvedTail(paleBlue, 0.70f, -0.22f, true)
    }

    private fun curvedTail(color: FloatArray, x: Float, y: Float, long: Boolean) {
        val count = if (long) 7 else 5
        val length = if (long) 1.14f else 0.94f
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1)
            ball(x + t * length, y - 0.20f * sin(t * PI.toFloat()) + 0.30f * t,
                -0.28f - t * 0.08f, 0.23f - 0.09f * t, 0.22f - 0.08f * t, 0.22f - 0.08f * t, color)
        }
    }
    private fun wings(time: Float, dragon: Boolean) {
        for (side in listOf(-1f, 1f)) {
            ball(side * 0.86f, if (dragon) 0.39f else 0.10f, -0.36f,
                if (dragon) 0.31f else 0.27f, if (dragon) 0.73f else 0.62f, 0.14f,
                paleBlue, side * (-43f + 2f * sin(time * 1.3f)))
        }
    }
    private fun paws() { for (side in listOf(-1f, 1f)) ball(side * 0.38f, -0.86f, 0.19f, 0.26f, 0.19f, 0.34f, bodyColor) }

    private fun drawFace(face: Face, time: Float, sleepy: Boolean) {
        if (form == CloudForm.DOG || form == CloudForm.CAT) ball(0f, 0.14f, 0.64f, 0.08f, 0.055f, 0.045f, ink)
        val cycle = time % 4.8f
        val blink = if (cycle > 4.56f) max(0.10f, abs(cycle - 4.68f) / 0.12f) else if (sleepy) 0.37f else 1f
        val glance = if (paintListening) 0.025f else 0.009f * sin(time * 0.7f)
        for (side in listOf(-1f, 1f)) {
            ball(face.x + side * 0.22f + glance, face.y, face.z, 0.065f, 0.090f * blink, 0.041f, ink)
            if (blink > 0.3f) ball(face.x + side * 0.22f - 0.016f + glance, face.y + 0.026f * blink, face.z + 0.038f, 0.018f, 0.020f * blink, 0.012f, white)
            ball(face.x + side * 0.37f, face.y - 0.14f, face.z - 0.045f, 0.075f, 0.035f, 0.025f, pink)
            if (paintMood == "sad" || paintMood == "슬픔") ball(face.x + side * 0.22f, face.y + 0.15f, face.z, 0.09f, 0.017f, 0.02f, ink, side * 18f)
        }
        val open = if (paintSpeaking) 0.035f + 0.085f * abs(sin(time * 10.5f)) else if (paintMood == "happy" || paintMood == "excited" || paintMood == "기쁨") 0.047f else 0.020f
        ball(face.x, face.y - 0.20f, face.mouthZ + 0.025f, 0.11f, open, 0.032f, ink)
        if (paintSpeaking) ball(face.x, face.y - 0.20f - open * 0.37f, face.mouthZ + 0.055f, 0.055f, open * 0.31f, 0.010f, pink)
    }

    private fun ball(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray, rotation: Float = 0f) {
        System.arraycopy(root, 0, model, 0, 16)
        Matrix.translateM(model, 0, x, y, z)
        if (rotation != 0f) Matrix.rotateM(model, 0, rotation, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        check(Matrix.invertM(inverse, 0, modelView, 0)) { "Non-invertible mesh transform" }
        Matrix.transposeM(normal, 0, inverse, 0)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(mvLoc, 1, false, modelView, 0)
        GLES20.glUniformMatrix4fv(normalMatrixLoc, 1, false, normal, 0)
        GLES20.glUniform4f(colorLoc, color[0], color[1], color[2], color[3] * paintAlpha)
        sphere.vertices.position(0)
        GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, 24, sphere.vertices)
        sphere.vertices.position(3)
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, 24, sphere.vertices)
        GLES20.glEnableVertexAttribArray(positionLoc); GLES20.glEnableVertexAttribArray(normalLoc)
        sphere.indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphere.indexCount, GLES20.GL_UNSIGNED_SHORT, sphere.indices)
    }

    /** Render each complete solid cloud before fading it, so its overlapping meshes do not show through. */
    private fun prepareLayerBuffer() {
        if (layerFramebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(layerFramebuffer), 0)
        if (layerTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(layerTexture), 0)
        if (layerDepth != 0) GLES20.glDeleteRenderbuffers(1, intArrayOf(layerDepth), 0)
        val handle = IntArray(1)
        GLES20.glGenTextures(1, handle, 0); layerTexture = handle[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, layerTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenRenderbuffers(1, handle, 0); layerDepth = handle[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, layerDepth)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)
        GLES20.glGenFramebuffers(1, handle, 0); layerFramebuffer = handle[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, layerFramebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, layerTexture, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, layerDepth)
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Cloud fade framebuffer unavailable" }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun beginLayer() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, layerFramebuffer)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
    }

    private fun compositeLayer(opacity: Float) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(compositeProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, layerTexture)
        GLES20.glUniform1i(textureLoc, 0)
        GLES20.glUniform1f(opacityLoc, opacity.coerceIn(0f, 1f))
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(normalLoc)
        quad.position(0)
        GLES20.glVertexAttribPointer(quadPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glVertexAttribPointer(quadUv, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(quadPosition)
        GLES20.glEnableVertexAttribArray(quadUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(quadPosition)
        GLES20.glDisableVertexAttribArray(quadUv)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun fail(error: Exception) {
        if (failed) return
        failed = true
        val message = error.message ?: error.javaClass.simpleName
        Log.e(TAG, "3D rendering failed: $message", error)
        onFailure(message)
    }
    private fun compile(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create shader" }
        GLES20.glShaderSource(shader, code); GLES20.glCompileShader(shader)
        val success = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, success, 0)
        if (success[0] == 0) { val info = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); error("Shader compilation: $info") }
        return shader
    }
    private fun linkProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        var fs = 0; var linked = 0
        try {
            fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
            linked = GLES20.glCreateProgram()
            check(linked != 0) { "Could not create GL program" }
            GLES20.glAttachShader(linked, vs); GLES20.glAttachShader(linked, fs); GLES20.glLinkProgram(linked)
            val success = IntArray(1); GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, success, 0)
            check(success[0] != 0) { "Shader linking: ${GLES20.glGetProgramInfoLog(linked)}" }
            return linked
        } catch (error: Exception) { if (linked != 0) GLES20.glDeleteProgram(linked); throw error }
        finally { GLES20.glDeleteShader(vs); if (fs != 0) GLES20.glDeleteShader(fs) }
    }

    companion object {
        private const val TAG = "LandscapeFriend3D"
        private const val QUAD_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vUv = aUv; }
        """
        private const val QUAD_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uOpacity;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTexture, vUv) * uOpacity; }
        """
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModelView;
            uniform mat4 uNormalMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vPosition = (uModelView * vec4(aPosition, 1.0)).xyz;
                vNormal = (uNormalMatrix * vec4(aNormal, 0.0)).xyz;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 light = normalize(vec3(-0.45, 0.70, 0.85));
                vec3 eye = normalize(-vPosition);
                float diffuse = max(dot(n, light), 0.0);
                float softFill = max(dot(n, normalize(vec3(0.8, 0.2, 0.4))), 0.0);
                float shine = pow(max(dot(n, normalize(light + eye)), 0.0), 28.0);
                float rim = pow(1.0 - max(dot(n, eye), 0.0), 2.0);
                vec3 color = uColor.rgb * (0.48 + 0.40 * diffuse + 0.10 * softFill);
                color += vec3(0.12 * shine + 0.06 * rim);
                gl_FragColor = vec4(color, uColor.a);
            }
        """
    }
}

private class SphereMesh(rows: Int, columns: Int) {
    val vertexCount = (rows + 1) * (columns + 1)
    val indexCount = rows * columns * 6
    val vertices: FloatBuffer = ByteBuffer.allocateDirect(vertexCount * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    val indices: ShortBuffer = ByteBuffer.allocateDirect(indexCount * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    init {
        for (row in 0..rows) {
            val latitude = PI * row / rows
            for (column in 0..columns) {
                val longitude = 2.0 * PI * column / columns
                val x = (sin(latitude) * cos(longitude)).toFloat()
                val y = cos(latitude).toFloat()
                val z = (sin(latitude) * sin(longitude)).toFloat()
                vertices.put(x).put(y).put(z).put(x).put(y).put(z)
            }
        }
        for (row in 0 until rows) for (column in 0 until columns) {
            val top = row * (columns + 1) + column
            val bottom = top + columns + 1
            indices.put(top.toShort()).put(bottom.toShort()).put((top + 1).toShort())
            indices.put((top + 1).toShort()).put(bottom.toShort()).put((bottom + 1).toShort())
        }
        vertices.position(0); indices.position(0)
    }
}
