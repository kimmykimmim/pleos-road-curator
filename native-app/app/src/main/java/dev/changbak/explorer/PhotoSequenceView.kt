package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.ChildLanguage
import dev.changbak.explorer.domain.PhotoStage
import kotlin.math.max

/**
 * Real photographs of one place, laid over each other in the order the change happened.
 *
 * The change is the point, so each photograph is held still long enough to be read and then
 * dissolved into the next from the same framing. A child watching the window sees the place as
 * it is; this shows the same place as it was, in the same spot on the screen.
 *
 * Every photograph carries its year and its source on screen for as long as it is visible.
 * Nothing here is generated: a stage with no file or no source never reaches this view.
 */
class PhotoSequenceView(context: Context) : View(context) {
    private class Frame(val stage: PhotoStage, val image: Bitmap)

    private val frames = mutableListOf<Frame>()
    private var index = 0
    private var fade = 1f
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cream = Color.rgb(255, 239, 196)
    private val source = Rect()
    private val target = RectF()

    /** One photograph is enough to recognise a place; two or more also show a change. */
    val hasPhotos: Boolean get() = frames.isNotEmpty()

    /** The caption is read by the child, so it follows the language they are spoken to in. */
    var language: ChildLanguage = ChildLanguage.KOREAN

    /** How long one photograph is held before it starts dissolving into the next. */
    private val holdMs = 2600L
    private val fadeMs = 900L

    /**
     * Loads what is actually on the device. A stage whose file is missing is skipped rather
     * than drawn as a gap, so a half-supplied set still shows the change it can show.
     */
    fun load(stages: List<PhotoStage>) {
        release()
        val width = max(width, 960)
        stages.forEach { stage ->
            val bitmap = runCatching {
                context.assets.open("photos/${stage.asset}").use { input ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val bytes = input.readBytes()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    // A car screen never needs the full archive scan; sampling keeps memory sane.
                    var sample = 1
                    while (bounds.outWidth / sample > width * 2) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
            if (bitmap != null) frames.add(Frame(stage, bitmap))
        }
        index = 0
        fade = 1f
        invalidate()
    }

    fun start() {
        if (frames.size < 2) return
        stop()
        if (!ValueAnimator.areAnimatorsEnabled()) { fade = 1f; invalidate(); return }
        step()
    }

    fun restart() { index = 0; fade = 1f; start() }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    fun release() {
        stop()
        frames.forEach { it.image.recycle() }
        frames.clear()
    }

    private fun step() {
        if (frames.size < 2) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs
            startDelay = holdMs
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator !== animation) return
                    // Stop on the last photograph: the place as it is now is where to rest.
                    if (index < frames.size - 2) { index += 1; fade = 0f; step() }
                    else { index = frames.size - 2; fade = 1f; invalidate() }
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        if (frames.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(238, 246, 252))
        val from = frames[index.coerceIn(0, frames.lastIndex)]
        val to = frames.getOrNull(index + 1)
        draw(canvas, from.image, w, h, 1f)
        if (to != null && fade > 0f) draw(canvas, to.image, w, h, fade)
        caption(canvas, if (to != null && fade > .5f) to.stage else from.stage, w, h)
    }

    /** Fills the frame and crops the overflow, so every stage sits in the same place. */
    private fun draw(canvas: Canvas, image: Bitmap, w: Float, h: Float, alpha: Float) {
        val scale = max(w / image.width, h / image.height)
        val drawnW = image.width * scale
        val drawnH = image.height * scale
        source.set(0, 0, image.width, image.height)
        target.set((w - drawnW) / 2, (h - drawnH) / 2, (w + drawnW) / 2, (h + drawnH) / 2)
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.drawBitmap(image, source, target, paint)
        paint.alpha = 255
    }

    private fun caption(canvas: Canvas, stage: PhotoStage, w: Float, h: Float) {
        // Sized against the frame's width, not its height.
        //
        // These were fractions of the height, which was fine while the view was always 16:9.
        // Once the panel started taking whatever height it was given, a tall frame blew the
        // caption up until it collided with the chips over it. The width is what the line has
        // to fit across, so the width is what should set its size.
        val base = minOf(h, w / 1.78f)
        val pad = base * .045f
        val barTop = h - base * .33f
        paint.shader = LinearGradient(0f, barTop, 0f, h, Color.TRANSPARENT,
            Color.argb(215, 11, 45, 63), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, barTop, w, h, paint)
        paint.shader = null

        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textSize = base * .085f
        text.color = cream
        canvas.drawText(stage.yearIn(language), pad, h - pad * 3.4f, text)

        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        text.textSize = base * .062f
        text.color = Color.WHITE
        canvas.drawText(stage.captionIn(language), pad, h - pad * 1.8f, text)

        // The source rides with the photograph. A picture with no stated origin is the one
        // thing this app must never put in front of a child as if it were the place.
        //
        // On its own line under the caption. It used to be right-aligned on almost the same
        // baseline, so a long caption ran straight into the attribution and neither could be
        // read — the one line that legally has to be legible.
        text.textSize = base * .042f
        text.color = Color.argb(205, 255, 255, 255)
        canvas.drawText(stage.credit, pad, h - pad * .6f, text)
    }

    init { setBackgroundColor(Color.rgb(238, 246, 252)) }
}
