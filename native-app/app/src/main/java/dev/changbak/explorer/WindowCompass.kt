package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import dev.changbak.explorer.domain.WindowSide

/**
 * Which window to look out of, while the place is going past.
 *
 * The narration names the side once — "곧 숭례문 근처야. 오른쪽 창밖을 봐." — and a child who
 * was looking the other way when it was said has nothing to go back to. This stays on screen
 * for the whole watching step and keeps pointing.
 *
 * It is not a compass of the needle-and-north kind. North is no use to a six year old in a car
 * seat: the question is "left or right", and the answer is a fat arrow that leans that way with
 * the word under it. Drawn in the same hand as the rest of the app's small pictures — flat
 * shapes, thick outline, one soft colour — rather than assembled out of photographs, because
 * this is a sign, not a thing in the world.
 */
class WindowCompass(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val path = Path()
    private val box = RectF()

    private var side = WindowSide.UNKNOWN
    private var words = ""
    private var beat = 0f
    private var bob: ValueAnimator? = null

    /** Point at this window, or nowhere when the journey cannot say which. */
    fun point(which: WindowSide, label: String) {
        if (which == side && label == words) return
        side = which; words = label
        visibility = if (which == WindowSide.UNKNOWN) GONE else VISIBLE
        if (which == WindowSide.UNKNOWN) stop() else start()
        invalidate()
    }

    private fun start() {
        if (bob != null || !ValueAnimator.areAnimatorsEnabled()) return
        bob = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { beat = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stop() { bob?.cancel(); bob = null; beat = 0f }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        if (side == WindowSide.UNKNOWN) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val side0 = minOf(w, h * .78f)
        canvas.save()
        canvas.translate((w - side0) / 2f, 0f)
        canvas.scale(side0 / 100f, side0 / 100f)

        // The lean: towards the window it is pointing at, and a small rise for "ahead".
        val sway = (beat - .5f) * 2f
        when (side) {
            WindowSide.LEFT -> canvas.translate(-4f * (beat), 0f)
            WindowSide.RIGHT -> canvas.translate(4f * (beat), 0f)
            else -> canvas.translate(0f, -4f * beat)
        }
        canvas.rotate(sway * 3f, 50f, 50f)

        line.strokeWidth = 5f
        // The badge: a soft round-cornered window, not a brass instrument.
        fill.color = Palette.paper
        path.reset()
        path.addRoundRect(RectF(8f, 8f, 92f, 92f), 30f, 30f, Path.Direction.CW)
        canvas.drawPath(path, fill)
        line.color = Palette.ink
        canvas.drawPath(path, line)

        // A fat arrow, rounded at every corner, pointing the way.
        canvas.save()
        canvas.rotate(when (side) {
            WindowSide.LEFT -> 180f
            WindowSide.AHEAD -> -90f
            else -> 0f
        }, 50f, 50f)
        fill.color = Palette.accent
        path.reset()
        path.moveTo(30f, 42f)
        path.lineTo(56f, 42f)
        path.lineTo(56f, 30f)
        path.lineTo(78f, 50f)
        path.lineTo(56f, 70f)
        path.lineTo(56f, 58f)
        path.lineTo(30f, 58f)
        path.close()
        val round = android.graphics.CornerPathEffect(7f)
        fill.pathEffect = round; line.pathEffect = round
        canvas.drawPath(path, fill)
        line.color = Palette.ink
        canvas.drawPath(path, line)
        fill.pathEffect = null; line.pathEffect = null
        canvas.restore()

        // Two cheeks, which is most of the difference between a sign and a friend.
        fill.color = Color.argb(110, 246, 150, 140)
        canvas.drawCircle(24f, 68f, 7f, fill)
        canvas.drawCircle(76f, 68f, 7f, fill)
        canvas.restore()

        if (words.isBlank()) return
        label.textSize = side0 * .20f
        label.color = Color.WHITE
        canvas.drawText(words, w / 2f, h - label.textSize * .25f, label)
    }
}
