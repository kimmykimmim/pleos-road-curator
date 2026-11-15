package dev.changbak.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/**
 * Today's drive, drawn as the road it is.
 *
 * A line across the bottom of the screen with a mark for every place this journey holds, and a
 * filling segment that shortens as the next one comes up. It is the one structural device in
 * this app that belongs to no other: a route is what a car journey is, and the two facts it
 * carries — where we are among today's places, and how close the next one is — are the two the
 * child keeps asking out loud.
 *
 * Nothing here is decoration. With no places to show it draws only the approach; with no
 * approach it draws only the line. If it has neither it draws nothing at all.
 */
class RouteRule(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var stops: List<String> = emptyList()
    private var current = -1
    /** How far along the leg to the current place we are, 0 at first mention and 1 alongside. */
    private var approach: Float? = null
    private var passed = false

    /** White at low alpha: these sit on a photograph as often as on a flat colour. */
    private val line = Color.argb(70, 255, 255, 255)
    private val lit = Palette.primary
    private val faint = Color.argb(150, 255, 255, 255)

    fun show(stops: List<String>, current: Int, approach: Float?, passed: Boolean) {
        if (stops == this.stops && current == this.current &&
            approach == this.approach && passed == this.passed) return
        this.stops = stops; this.current = current; this.approach = approach; this.passed = passed
        visibility = if (stops.isEmpty() && approach == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val pad = h * .34f
        val y = h * .34f
        text.textSize = h * .23f

        paint.strokeCap = Paint.Cap.ROUND
        paint.color = line
        paint.strokeWidth = h * .055f
        canvas.drawLine(pad, y, w - pad, y, paint)

        if (stops.isEmpty()) { drawApproachOnly(canvas, pad, w - pad, y, h); return }

        val span = (w - pad * 2)
        val step = if (stops.size > 1) span / (stops.size - 1) else 0f
        fun xOf(index: Int) = if (stops.size > 1) pad + step * index else w / 2f

        // The road behind us is solid; the road ahead is faint. The leg we are on fills as the
        // place approaches, which is the whole drama of this app in one shrinking gap.
        if (current > 0) {
            paint.color = faint
            canvas.drawLine(pad, y, xOf(current - 1), y, paint)
        }
        if (current >= 0) {
            val from = if (current > 0) xOf(current - 1) else pad
            val filled = from + (xOf(current) - from) * (if (passed) 1f else approach ?: 1f)
            paint.color = lit
            canvas.drawLine(from, y, filled, y, paint)
        }

        stops.indices.forEach { index ->
            val x = xOf(index)
            val here = index == current
            paint.color = when {
                here -> lit
                index < current -> faint
                else -> line
            }
            canvas.drawCircle(x, y, h * (if (here) .13f else .075f), paint)
            if (here) {
                paint.color = Color.argb(60, 168, 224, 95)
                canvas.drawCircle(x, y, h * .24f, paint)
            }
            text.color = if (here) lit else faint
            text.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                stops.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(stops[index], x, h * .92f, text)
        }
    }

    /** A live journey has no list of today's stops, only the place now coming up. */
    private fun drawApproachOnly(canvas: Canvas, from: Float, to: Float, y: Float, h: Float) {
        val fraction = approach ?: return
        paint.color = lit
        canvas.drawLine(from, y, from + (to - from) * fraction.coerceIn(0f, 1f), y, paint)
    }
}
