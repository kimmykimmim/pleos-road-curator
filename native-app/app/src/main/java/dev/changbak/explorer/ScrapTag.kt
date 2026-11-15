package dev.changbak.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * One of the place's words, torn out and stuck on.
 *
 * The hashtags were grey pills — the chrome of a web page, sitting under a photograph in an
 * app whose every other picture is a newspaper cutting. Same words, same place on screen, but
 * cut out of paper now: a torn edge, a slight angle, a shadow. The app says one thing in one
 * language.
 *
 * Each tag leans a little differently, from its own text, so a row of them looks stuck on one
 * at a time rather than printed in a strip.
 */
class ScrapTag(context: Context, private val word: String) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = Palette.ink
    }
    private val path = Path()

    /** Degrees, from the word itself: the same tag always leans the same way. */
    private val tilt = (sin(word.hashCode() * .37) * 3.4).toFloat()

    var textSizePx = 0f
        set(value) { field = value; ink.textSize = value; requestLayout() }

    private val padX get() = textSizePx * .62f
    private val padY get() = textSizePx * .46f
    /** Room for the lean and the torn edge, so neither is clipped. */
    private val bleed get() = textSizePx * .55f

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = ink.measureText(word) + padX * 2 + bleed * 2
        val h = textSizePx + padY * 2 + bleed * 2
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (textSizePx <= 0f) return
        val w = width.toFloat(); val h = height.toFloat()
        canvas.save()
        canvas.rotate(tilt, w / 2f, h / 2f)

        val left = bleed; val right = w - bleed
        val top = bleed; val bottom = h - bleed
        val tear = textSizePx * .17f
        path.reset()
        path.moveTo(left, top + tear * .5f)
        // Top edge: a shallow tear.
        var x = left
        var step = 0
        while (x < right) {
            x = minOf(x + (right - left) / 9f, right)
            path.lineTo(x, top + if (step % 2 == 0) 0f else tear * .7f)
            step++
        }
        path.lineTo(right, bottom - tear * .5f)
        // Bottom edge, torn the other way.
        while (x > left) {
            x = maxOf(x - (right - left) / 9f, left)
            path.lineTo(x, bottom - if (step % 2 == 0) 0f else tear * .8f)
            step++
        }
        path.close()

        fill.color = Color.argb(70, 0, 0, 0)
        canvas.save(); canvas.translate(0f, tear * .45f); canvas.drawPath(path, fill); canvas.restore()
        fill.color = Palette.paper
        canvas.drawPath(path, fill)

        // The word, sitting on the paper rather than in a box.
        val baseline = h / 2f + textSizePx * .36f
        canvas.drawText(word, left + padX, baseline, ink)
        canvas.restore()
    }

    /** For checks: how far this tag leans, which must stay small enough to read. */
    val leanDegrees: Float get() = abs(tilt)
}
