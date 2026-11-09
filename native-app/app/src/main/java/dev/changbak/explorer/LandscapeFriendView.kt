package dev.changbak.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import dev.changbak.explorer.domain.CloudFeature
import dev.changbak.explorer.domain.CloudForm
import dev.changbak.explorer.domain.FriendPart
import dev.changbak.explorer.domain.FriendTrait
import dev.changbak.explorer.domain.ScenerySource
import dev.changbak.explorer.domain.TravelState

/** The child's remembered scenery becomes one character, revealed only after the journey. */
class LandscapeFriendView(
    context: Context,
    private val state: TravelState,
    private val reveal: Boolean = false,
) : View(context) {
    private val latest = state.memories.associateBy { it.trait.part }
    private val bodyModel = state.body?.let { body ->
        body.copy(features = body.features.filterNot {
            (it == CloudFeature.LONG_TAIL && FriendPart.TAIL in latest) ||
                (it == CloudFeature.WINGS && FriendPart.WINGS in latest)
        })
    }
    private val base = CloudFriendView(context, bodyModel, reveal)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Color.rgb(151, 188, 211)
    private val green = Color.rgb(155, 205, 165)
    private val greenEdge = Color.rgb(103, 157, 127)
    private val paleBlue = Color.rgb(222, 240, 252)
    private val riverBlue = Color.rgb(146, 204, 233)
    private val white = Color.WHITE

    init {
        isFocusable = false
        contentDescription = if (reveal) {
            "여행에서 기억한 모습으로 그린 ${state.body?.nickname ?: state.body?.label ?: "풍경 친구"}"
        } else "여행을 함께하는 구름 친구 그림"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // This detached, static drawing helper has the same viewport as the outer view.
        base.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        base.layout(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val tint = if (reveal) colorTint() else null
        if (tint == null) base.draw(canvas) else {
            paint.reset(); paint.isAntiAlias = true; paint.colorFilter = tint
            val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), paint)
            base.draw(canvas)
            canvas.restoreToCount(layer)
            paint.colorFilter = null
        }
        if (!reveal) return

        val fit = minOf(width / 500f, height / 360f)
        canvas.save()
        canvas.translate((width - 500f * fit) / 2f, (height - 360f * fit) / 2f)
        canvas.scale(fit, fit)
        val features = bodyModel?.features.orEmpty()
        val size = when {
            CloudFeature.BIG in features -> 1.08f
            CloudFeature.SMALL in features -> 0.82f
            else -> 1f
        }
        canvas.translate(250f, 190f)
        canvas.scale(size * if (CloudFeature.LONG in features) 1.08f else 1f,
            size * if (CloudFeature.ROUND in features) 1.04f else 1f)
        canvas.translate(-250f, -190f)
        val form = bodyModel?.form ?: CloudForm.CLOUD

        latest[FriendPart.TAIL]?.let { tail ->
            drawTail(canvas, form, tail.source, tail.trait == FriendTrait.LONG_TAIL, tail.trait == FriendTrait.SPARKLE_TAIL)
        }
        if (latest[FriendPart.WINGS]?.trait == FriendTrait.WINGS && form !in setOf(CloudForm.DRAGON, CloudForm.BIRD)) {
            drawWings(canvas)
        }
        latest[FriendPart.EARS]?.let { ears ->
            if (ears.trait == FriendTrait.LEAF_EARS || ears.trait == FriendTrait.SWAY_EARS) {
                drawEars(canvas, form, ears.trait == FriendTrait.SWAY_EARS)
            }
        }
        if (latest[FriendPart.STEP]?.trait == FriendTrait.SWAY_STEP) drawSteps(canvas)
        canvas.restore()
    }

    private fun colorTint(): ColorMatrixColorFilter? {
        val tint = when (latest[FriendPart.COLOR]?.trait) {
            FriendTrait.GREEN -> floatArrayOf(0.89f, 0.98f, 0.92f)
            FriendTrait.BLUE -> floatArrayOf(0.88f, 0.95f, 1f)
            FriendTrait.WARM_LIGHT -> floatArrayOf(1f, 0.97f, 0.87f)
            else -> return null
        }
        return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            tint[0], 0f, 0f, 0f, 0f,
            0f, tint[1], 0f, 0f, 0f,
            0f, 0f, tint[2], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }

    private fun drawEars(canvas: Canvas, form: CloudForm, sway: Boolean) {
        if (form == CloudForm.RABBIT) {
            leaf(canvas, 213f, 160f, if (sway) -14f else -3f, 87f, 17f)
            leaf(canvas, 282f, 160f, if (sway) 15f else 4f, 79f, 17f)
            return
        }
        val anchors = when (form) {
            CloudForm.WHALE -> floatArrayOf(165f, 155f, 211f, 146f)
            CloudForm.DRAGON -> floatArrayOf(167f, 153f, 210f, 148f)
            CloudForm.DINOSAUR -> floatArrayOf(304f, 138f, 336f, 143f)
            CloudForm.BIRD -> floatArrayOf(176f, 149f, 211f, 151f)
            CloudForm.TURTLE -> floatArrayOf(314f, 207f, 338f, 208f)
            CloudForm.DOG -> floatArrayOf(207f, 177f, 286f, 176f)
            CloudForm.CAT -> floatArrayOf(210f, 162f, 286f, 162f)
            else -> floatArrayOf(203f, 150f, 293f, 153f)
        }
        val length = if (form == CloudForm.TURTLE) 48f else 65f
        leaf(canvas, anchors[0], anchors[1], if (sway) -48f else -28f, length, 23f)
        leaf(canvas, anchors[2], anchors[3], if (sway) 43f else 27f, length * 0.94f, 22f)
        if (sway) {
            stroke(canvas, path { moveTo(anchors[0] - 57f, anchors[1] - 20f); quadTo(anchors[0] - 65f, anchors[1] - 36f, anchors[0] - 56f, anchors[1] - 49f) }, greenEdge, 2f)
        }
    }

    private fun leaf(canvas: Canvas, x: Float, y: Float, rotation: Float, length: Float, breadth: Float) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(rotation)
        fill(canvas, path {
            moveTo(0f, 0f)
            cubicTo(-breadth, -length * 0.32f, -breadth, -length * 0.70f, 0f, -length)
            cubicTo(breadth, -length * 0.70f, breadth, -length * 0.32f, 0f, 0f)
            close()
        }, green, greenEdge)
        stroke(canvas, path { moveTo(0f, -4f); quadTo(-3f, -length * 0.50f, 0f, -length + 11f) }, greenEdge, 2f)
        stroke(canvas, path {
            moveTo(0f, -length * 0.37f); lineTo(-breadth * 0.52f, -length * 0.51f)
            moveTo(0f, -length * 0.53f); lineTo(breadth * 0.45f, -length * 0.66f)
        }, Color.rgb(189, 226, 193), 2f)
        canvas.restore()
    }

    private fun drawTail(canvas: Canvas, form: CloudForm, source: ScenerySource, long: Boolean, sparkling: Boolean) {
        val anchor = when (form) {
            CloudForm.WHALE -> 307f to 228f
            CloudForm.DRAGON -> 314f to 257f
            CloudForm.RABBIT, CloudForm.DOG, CloudForm.CAT -> 300f to 249f
            CloudForm.TURTLE -> 163f to 251f
            else -> 310f to 246f
        }
        canvas.save(); canvas.translate(anchor.first, anchor.second)
        // Turtle faces right, so its tail grows left; other silhouettes have their rear on the right.
        if (form == CloudForm.TURTLE) canvas.scale(-0.68f, 0.82f)
        if (long) canvas.scale(1.08f, 1f)
        val sea = source == ScenerySource.SEA
        val water = source == ScenerySource.RIVER || sea || source == ScenerySource.WATER
        val silhouette = if (sea) path {
            moveTo(0f, 0f); cubicTo(37f, 11f, 68f, 2f, 76f, -29f)
            quadTo(65f, -54f, 83f, -67f); quadTo(101f, -57f, 103f, -39f)
            quadTo(118f, -55f, 135f, -46f); quadTo(129f, -20f, 108f, -12f)
            cubicTo(93f, 32f, 41f, 40f, 0f, 16f); close()
        } else path {
            moveTo(0f, 0f); cubicTo(39f, 8f, 87f, 25f, 104f, -7f)
            cubicTo(113f, -26f, 87f, -38f, 99f, -53f)
            quadTo(116f, -70f, 127f, -46f)
            cubicTo(153f, 14f, 99f, 48f, 45f, 32f)
            quadTo(16f, 25f, 0f, 16f); close()
        }
        paint.shader = LinearGradient(0f, 0f, 127f, -40f,
            if (water) Color.rgb(207, 237, 250) else if (sparkling) Color.rgb(255, 245, 211) else Color.rgb(249, 252, 255),
            if (water) riverBlue else if (sparkling) Color.rgb(235, 211, 157) else Color.rgb(208, 226, 242), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; paint.color = white
        canvas.drawPath(silhouette, paint); paint.shader = null
        stroke(canvas, silhouette, outline, 2.6f)
        stroke(canvas, path { moveTo(19f, 10f); cubicTo(53f, 21f, 86f, 26f, 107f, 2f) }, white, 3.5f)
        if (water || sparkling) {
            sparkle(canvas, 72f, 16f, 7f)
            sparkle(canvas, if (sea) 105f else 116f, if (sea) -29f else -24f, 6f)
            fill(canvas, circle(43f, 8f, 3f), white)
        }
        canvas.restore()
    }

    private fun drawWings(canvas: Canvas) {
        fill(canvas, path {
            moveTo(188f, 211f); cubicTo(148f, 205f, 108f, 154f, 121f, 129f)
            quadTo(153f, 135f, 173f, 161f); quadTo(179f, 145f, 191f, 143f)
            quadTo(203f, 180f, 188f, 211f); close()
        }, paleBlue, outline)
        fill(canvas, path {
            moveTo(312f, 211f); cubicTo(352f, 205f, 392f, 154f, 379f, 129f)
            quadTo(347f, 135f, 327f, 161f); quadTo(321f, 145f, 309f, 143f)
            quadTo(297f, 180f, 312f, 211f); close()
        }, paleBlue, outline)
        stroke(canvas, path { moveTo(183f, 200f); quadTo(151f, 174f, 132f, 146f); moveTo(317f, 200f); quadTo(349f, 174f, 368f, 146f) }, white, 3f)
    }

    private fun drawSteps(canvas: Canvas) {
        fill(canvas, oval(186f, 289f, 222f, 307f), paleBlue, outline)
        fill(canvas, oval(276f, 294f, 311f, 310f), paleBlue, outline)
        stroke(canvas, path { moveTo(170f, 295f); quadTo(160f, 302f, 166f, 311f) }, outline, 2f)
        stroke(canvas, path { moveTo(326f, 300f); quadTo(338f, 306f, 332f, 315f) }, outline, 2f)
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, radius: Float) = fill(canvas, path {
        moveTo(x, y - radius); quadTo(x + 2f, y - 2f, x + radius, y)
        quadTo(x + 2f, y + 2f, x, y + radius); quadTo(x - 2f, y + 2f, x - radius, y)
        quadTo(x - 2f, y - 2f, x, y - radius); close()
    }, white)

    private fun fill(canvas: Canvas, path: Path, color: Int, edge: Int? = null) {
        paint.shader = null; paint.colorFilter = null; paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        if (edge != null) stroke(canvas, path, edge, 2.5f)
    }
    private fun stroke(canvas: Canvas, path: Path, color: Int, width: Float) {
        paint.shader = null; paint.colorFilter = null; paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
    }
    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun circle(x: Float, y: Float, radius: Float) = path { addCircle(x, y, radius, Path.Direction.CW) }
    private fun oval(left: Float, top: Float, right: Float, bottom: Float) = path { addOval(RectF(left, top, right, bottom), Path.Direction.CW) }
}
