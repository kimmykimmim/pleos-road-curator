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
 * It is drawn from the front of a car — two round headlamps for eyes, a windscreen for a brow,
 * two wing mirrors — because the thing talking to the child really is the car they are sitting
 * in. That is also why it is ours and not a borrowed one: shipping somebody else's character
 * in a submitted app is not something to do on a maybe, and a car's own face is the honest
 * shape for this app anyway.
 *
 * The name is 두리번거리다 — to look around you. That is the whole of what the app wants.
 */
class CompanionView(context: Context) : View(context) {
    /** What the companion is doing, which is the only thing that changes how it is drawn. */
    enum class Doing { RESTING, SPEAKING, LISTENING }

    private var doing = Doing.RESTING
    private var beat = 0f
    private var animator: ValueAnimator? = null

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

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        draw(canvas, side, doing, beat)
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

        private val cream = Color.rgb(255, 239, 196)
        private val ink = Color.rgb(11, 45, 63)
        private val glass = Color.rgb(151, 215, 255)
        private val lamp = Color.rgb(255, 250, 230)

        fun draw(canvas: Canvas, side: Float, doing: Doing, beat: Float) {
            canvas.save()
            canvas.scale(side / 100f, side / 100f)
            line.strokeWidth = 4.2f

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

            // The body: a cabin with a windscreen, seen head on.
            fill.color = cream
            path.reset()
            path.addRoundRect(RectF(16f, 26f, 84f, 84f), 24f, 24f, Path.Direction.CW)
            canvas.drawPath(path, fill)
            line.color = ink
            canvas.drawPath(path, line)

            // The windscreen.
            fill.color = glass
            path.reset()
            path.moveTo(29f, 46f)
            path.cubicTo(34f, 34f, 66f, 34f, 71f, 46f)
            path.cubicTo(60f, 42f, 40f, 42f, 29f, 46f)
            path.close()
            canvas.drawPath(path, fill)

            // Wing mirrors, which is what makes it read as a car rather than a bear.
            fill.color = cream
            listOf(12f to 52f, 88f to 52f).forEach { (x, y) ->
                path.reset()
                path.addRoundRect(RectF(x - 7f, y - 5f, x + 7f, y + 7f), 4f, 4f, Path.Direction.CW)
                canvas.drawPath(path, fill)
                line.color = ink
                canvas.drawPath(path, line)
            }

            // Headlamp eyes. They close to a happy arc while listening, so the child can see
            // they are being attended to rather than looked at.
            if (doing == Doing.LISTENING) {
                line.color = ink
                listOf(36f, 64f).forEach { x ->
                    box.set(x - 9f, 50f, x + 9f, 68f)
                    canvas.drawArc(box, 200f, 140f, false, line)
                }
            } else {
                listOf(36f, 64f).forEach { x ->
                    fill.color = lamp
                    canvas.drawCircle(x, 59f, 9f, fill)
                    line.color = ink
                    canvas.drawCircle(x, 59f, 9f, line)
                    fill.color = ink
                    canvas.drawCircle(x + 1.5f, 59f, 4.2f, fill)
                }
            }

            // The mouth: a grille that opens on the beat while the words are being said.
            val open = if (doing == Doing.SPEAKING) 3f + 7f * beat else 3f
            fill.color = ink
            path.reset()
            path.addRoundRect(RectF(40f, 72f - open / 2f, 60f, 72f + open / 2f), open / 2f, open / 2f, Path.Direction.CW)
            canvas.drawPath(path, fill)
            canvas.restore()
        }
    }
}
