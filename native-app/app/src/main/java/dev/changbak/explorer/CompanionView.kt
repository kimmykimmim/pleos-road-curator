package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * 두리 — the one who does the talking.
 *
 * Everything this app says was arriving from nowhere: a voice, and a sheet of paper that
 * changed. A six year old is being asked to answer out loud, and answering out loud to nobody
 * is a strange thing to ask. So there is somebody: a small face at the top of the sheet that
 * moves while the words are being said and listens while the child answers.
 *
 * It is drawn as a car window with a face in it, with one wing mirror on its left. The app's
 * whole request to the child is "look out of the window", so the thing doing the asking is a
 * window: the shape and the job are the same. That is also why it is ours and not a borrowed
 * one — shipping somebody else's character in a submitted app is not something to do on a
 * maybe, and this shape says what the app is for without a word.
 *
 * The name is 두리번거리다 — to look around you. That is the whole of what the app wants.
 */
class CompanionView(context: Context) : View(context) {
    /** What the companion is doing, which is the only thing that changes how it is drawn. */
    enum class Doing { RESTING, SPEAKING, LISTENING }

    private var doing = Doing.RESTING
    private var beat = 0f
    private var animator: ValueAnimator? = null

    /** Where the slow breath is, and how shut the eyes are. */
    private var breath = 0f
    private var blink = 0f
    private var life: ValueAnimator? = null

    fun setDoing(next: Doing) {
        if (next == doing) return
        doing = next
        animator?.cancel()
        if (next == Doing.RESTING || !ValueAnimator.areAnimatorsEnabled()) {
            beat = 0f; invalidate(); return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (next == Doing.SPEAKING) 520L else 1400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { beat = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /**
     * Breathing and blinking, which run whatever else is happening.
     *
     * A face that holds perfectly still is a picture of a face. The whole reason there is
     * somebody on this screen is so that a child answering out loud is answering to someone,
     * and someone is the thing that keeps moving a little while nothing is going on.
     *
     * One loop drives both, so the rhythm is the same every time rather than two timers
     * drifting against each other. The blinks sit at uneven points in the cycle: evenly spaced
     * blinking reads as an indicator lamp rather than as eyes.
     */
    private fun startLiving() {
        if (life != null || !ValueAnimator.areAnimatorsEnabled()) return
        life = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LIFE
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                val t = it.animatedValue as Float
                breath = kotlin.math.sin(t * 2.0 * Math.PI).toFloat()
                blink = maxOf(shutter(t, .44f), shutter(t, .89f))
                invalidate()
            }
            start()
        }
    }

    /** One blink: shut and open again inside a very short window. */
    private fun shutter(t: Float, at: Float): Float {
        val half = BLINK / 2f
        val away = kotlin.math.abs(t - at)
        return if (away > half) 0f else 1f - away / half
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startLiving() }

    override fun onDetachedFromWindow() {
        animator?.cancel(); life?.cancel(); life = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        draw(canvas, side, doing, beat, breath, blink)
        canvas.restore()
    }

    /**
     * The face itself, on a 100 by 100 field.
     *
     * Kept apart from the view so a check can render every state without a screen or a Looper,
     * the same way the small pictures are checked.
     */
    companion object {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        private val box = RectF()

        private val cream = Palette.paper
        private val ink = Palette.ink
        private val glass = Palette.sky
        private val lamp = Color.rgb(252, 255, 246)

        /** One breath in and out, with two blinks somewhere inside it. */
        const val LIFE = 4200L
        /** How much of that cycle one blink takes. */
        const val BLINK = .035f

        /**
         * @param breath -1 to 1, the slow rise and fall.
         * @param blink 0 with the eyes open, 1 with them shut.
         */
        fun draw(
            canvas: Canvas,
            side: Float,
            doing: Doing,
            beat: Float,
            breath: Float = 0f,
            blink: Float = 0f,
        ) {
            canvas.save()
            canvas.scale(side / 100f, side / 100f)
            line.strokeWidth = 4.2f
            // A settle rather than a swell. It has to be the kind of movement you only notice
            // once you have been looking for a moment.
            canvas.translate(0f, breath * 1.1f)
            canvas.scale(1f + breath * .012f, 1f + breath * .018f, 50f, 84f)

            // A small nod while talking, and a lean while listening. Neither moves far: this
            // sits beside the words and must not pull the eye off them.
            val nod = when (doing) {
                Doing.SPEAKING -> -1.6f * beat
                Doing.LISTENING -> 1.2f * beat
                Doing.RESTING -> 0f
            }
            canvas.translate(0f, nod)
            if (doing == Doing.LISTENING) canvas.rotate(5f * beat, 50f, 70f)

            // Listening shows an ear the only way a drawing can: something coming towards it.
            if (doing == Doing.LISTENING) {
                line.color = Color.argb((110 * beat).toInt(), 151, 215, 255)
                listOf(30f, 38f).forEachIndexed { index, r ->
                    box.set(50f - r, 52f - r, 50f + r, 52f + r)
                    canvas.drawArc(box, 196f, 52f - index * 8f, false, line)
                }
            }

            // The body: a car window, seen from inside the back seat.
            //
            // 두리 used to be the front of a car. This is the same idea one step closer to
            // what the app actually asks for: the child is told to look out of a window, and
            // the thing asking is the window. One wing mirror keeps the car in it.
            fill.color = Color.rgb(168, 224, 95)
            path.reset()
            path.addRoundRect(RectF(2f, 52f, 14f, 70f), 6f, 6f, Path.Direction.CW)
            canvas.drawPath(path, fill)
            line.color = ink
            canvas.drawPath(path, line)

            path.reset()
            path.moveTo(14f, 30f)
            path.quadTo(18f, 20f, 30f, 19f)
            path.lineTo(70f, 19f)
            path.quadTo(82f, 20f, 86f, 30f)
            path.lineTo(90f, 74f)
            path.quadTo(90f, 86f, 78f, 87f)
            path.lineTo(22f, 87f)
            path.quadTo(10f, 86f, 10f, 74f)
            path.close()
            fill.color = cream
            canvas.drawPath(path, fill)

            // The sky in the top of the window. Clipped to the glass so it cannot spill.
            canvas.save()
            canvas.clipPath(path)
            fill.color = glass
            path.reset()
            path.moveTo(10f, 30f)
            path.quadTo(18f, 19f, 30f, 18f)
            path.lineTo(70f, 18f)
            path.quadTo(82f, 19f, 90f, 30f)
            path.lineTo(90f, 36f)
            path.lineTo(10f, 36f)
            path.close()
            canvas.drawPath(path, fill)
            canvas.restore()

            // The frame, drawn last so the glass meets a clean edge.
            path.reset()
            path.moveTo(14f, 30f)
            path.quadTo(18f, 20f, 30f, 19f)
            path.lineTo(70f, 19f)
            path.quadTo(82f, 20f, 86f, 30f)
            path.lineTo(90f, 74f)
            path.quadTo(90f, 86f, 78f, 87f)
            path.lineTo(22f, 87f)
            path.quadTo(10f, 86f, 10f, 74f)
            path.close()
            line.color = ink
            canvas.drawPath(path, line)

            // Eyes. They close to a happy arc while listening, so the child can see they are
            // being attended to rather than looked at.
            if (doing == Doing.LISTENING) {
                line.color = ink
                listOf(37f, 63f).forEach { x ->
                    box.set(x - 10f, 50f, x + 10f, 68f)
                    canvas.drawArc(box, 200f, 140f, false, line)
                }
            } else if (blink > .82f) {
                line.color = ink
                listOf(37f, 63f).forEach { x ->
                    box.set(x - 10f, 52f, x + 10f, 68f)
                    canvas.drawArc(box, 200f, 140f, false, line)
                }
            } else {
                val open = 1f - blink
                listOf(37f, 63f).forEach { x ->
                    canvas.save()
                    canvas.scale(1f, open, x, 56f)
                    fill.color = Color.WHITE
                    canvas.drawCircle(x, 56f, 10f, fill)
                    line.color = ink
                    canvas.drawCircle(x, 56f, 10f, line)
                    fill.color = ink
                    canvas.drawCircle(x + 1f, 57f, 5.2f, fill)
                    // The catchlight. Without it the pupil is a hole, and a hole does not
                    // look at anybody.
                    fill.color = Color.WHITE
                    canvas.drawCircle(x + 3.4f, 54.4f, 2.1f, fill)
                    canvas.restore()
                }
            }

            // Cheeks. Two soft spots are most of the difference between a face that is pleased
            // to see you and a window with two lamps in it.
            fill.color = Color.argb(120, 246, 150, 140)
            canvas.drawCircle(24f, 72f, 6.5f, fill)
            canvas.drawCircle(76f, 72f, 6.5f, fill)

            // The mouth, which is the whole of whether this face is friendly. It smiles, and
            // the smile opens while the words come out.
            line.color = ink
            line.strokeWidth = 4.6f
            if (doing == Doing.SPEAKING) {
                val gape = 4f + 9f * beat
                fill.color = ink
                path.reset()
                path.moveTo(41f, 74f)
                path.cubicTo(45f, 74f + gape * .35f, 55f, 74f + gape * .35f, 59f, 74f)
                path.cubicTo(57f, 74f + gape, 43f, 74f + gape, 41f, 74f)
                path.close()
                canvas.drawPath(path, fill)
            } else {
                box.set(38f, 66f, 62f, 82f)
                canvas.drawArc(box, 25f, 130f, false, line)
            }
            line.strokeWidth = 4.2f
            canvas.restore()
        }
    }
}
