package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.ChildLanguage
import dev.changbak.explorer.domain.PhotoStage
import kotlin.math.max
import kotlin.math.min

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

    /** Which photograph is showing, so the host can put its words above the wash. */
    var onStage: (PhotoStage?) -> Unit = {}
    private var shown: PhotoStage? = null

    /**
     * How much of the right edge the question's sheet is covering.
     *
     * The photograph still runs to every edge — it is the view, and a photograph that stopped
     * at the sheet would read as the other half of a split screen. What moves is the framing:
     * the thing the photograph is *of* is pushed into the part of the screen that is actually
     * visible, because a child being asked to find a building cannot find it behind a sheet
     * of paper.
     */
    var coveredRight: Float = 0f
        set(value) { if (field != value) { field = value; invalidate() } }

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
        announce(null)
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
        draw(canvas, from, w, h, 1f)
        if (to != null && fade > 0f) draw(canvas, to, w, h, fade)
        announce(if (to != null && fade > .5f) to.stage else from.stage)
    }

    /**
     * Tell the host which photograph is on screen.
     *
     * The year, the line about it and the credit used to be painted into this view. They are
     * text over the lower part of the screen, which is exactly where the screen's own wash for
     * the place name sits — so the wash dimmed them, and the one line that legally has to be
     * readable came out grey. Text belongs in the layer above the wash, with the chips and the
     * name, so the host draws it and this view says only what is showing.
     */
    private fun announce(stage: PhotoStage?) {
        if (stage === shown) return
        shown = stage
        // Never from inside a draw: the host changes views when it hears this.
        post { onStage(stage) }
    }

    /**
     * Fills the screen, and puts the building where the child can actually see it.
     *
     * Filling alone is not enough once a sheet of paper covers two fifths of the screen: the
     * middle of the file lands behind the sheet, and the middle of the file is usually the
     * thing the child has been asked to find. So the picture is scaled up until the subject
     * can sit in the middle of the free area while the frame is still covered edge to edge,
     * then slid across until it does.
     *
     * Two limits keep this honest. The extra zoom stops at half again, because a photograph
     * cropped past that stops being the view out of the window. And the slide is clamped to
     * what still covers the screen, so a bare strip never appears beside the sheet — if the
     * subject cannot reach the middle of the free area it goes as far as it can.
     */
    private fun draw(canvas: Canvas, frame: Frame, w: Float, h: Float, alpha: Float) {
        val image = frame.image
        val fx = frame.stage.focusX.coerceIn(.05f, .95f)
        val fy = frame.stage.focusY.coerceIn(.05f, .95f)
        val fill = max(w / image.width, h / image.height)
        val free = (w - coveredRight).coerceAtLeast(1f)
        val wanted = (w - free / 2f) / (1f - fx) / image.width
        val scale = max(fill, min(wanted, fill * 1.5f))
        val drawnW = image.width * scale
        val drawnH = image.height * scale
        val left = (free / 2f - fx * drawnW).coerceIn(w - drawnW, 0f)
        val top = (h / 2f - fy * drawnH).coerceIn(h - drawnH, 0f)
        source.set(0, 0, image.width, image.height)
        target.set(left, top, left + drawnW, top + drawnH)
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.drawBitmap(image, source, target, paint)
        paint.alpha = 255
    }

    init { setBackgroundColor(Color.rgb(238, 246, 252)) }
}
