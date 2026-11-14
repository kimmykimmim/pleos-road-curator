package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import dev.changbak.explorer.domain.TopicPicture

/**
 * A small cut-out that appears while a question arrives, and goes again.
 *
 * It is a sticker, not a panel: one shape in cream on the view, outlined so it holds its edge
 * over a bright sky or a dark city, with nothing behind it. It is deliberately small and sits
 * in the corner the photographs leave empty, because the thing a child is being asked to look
 * at is out of the window — a picture that covered the view would be arguing with the app.
 *
 * It leaves on its own. Something that stayed would become part of the furniture by the third
 * place and stop being noticed at all, which is the opposite of why it is here.
 */
class TopicStickerView(context: Context) : View(context) {
    private var picture: TopicPicture? = null
    /** A real photograph of this place with its background taken away, when there is one. */
    private var scrap: Bitmap? = null
    private var animator: ValueAnimator? = null
    private var rock: ValueAnimator? = null

    /** Where the rocking is, from -1 at one side to 1 at the other. */
    private var tilt = 0f

    /** The torn page behind a clipping, and the clipping's own printed ink. */
    private val page = Path()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(250, 246, 236)
        setShadowLayer(10f, 0f, 4f, Color.argb(90, 4, 24, 36))
    }
    private val news = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        // Newsprint: most of the colour gone and the ink pushed, the way a photograph looks
        // once it has been through a cheap press.
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(.22f)
            postConcat(ColorMatrix(floatArrayOf(
                1.18f, 0f, 0f, 0f, -14f,
                0f, 1.15f, 0f, 0f, -12f,
                0f, 0f, 1.08f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f,
            )))
        })
    }

    /** 0 while away, 1 while fully out. Drives both the fade and the small rise. */
    private var here = 0f

    /**
     * Show a photograph of the place instead of a drawing of its subject.
     *
     * Passing the same one again does nothing, so a card redrawing itself does not restart
     * the arrival.
     */
    fun showScrap(next: Bitmap?, key: String?) {
        if (key == scrapKey) return
        scrapKey = key
        scrap = next
        picture = null
        animator?.cancel()
        if (next == null) { stopRocking(); here = 0f; visibility = GONE; invalidate(); return }
        arrive()
    }

    private var scrapKey: String? = null

    /**
     * Show this picture for a moment, or clear what is showing.
     *
     * Asking for the picture already on screen does nothing: a card redrawing itself is not a
     * new thing to look at, and restarting the animation each time would make it flicker.
     */
    fun show(next: TopicPicture?) {
        if (next == picture && scrap == null) return
        picture = next
        scrap = null
        scrapKey = null
        animator?.cancel()
        if (next == null) { stopRocking(); here = 0f; visibility = GONE; invalidate(); return }
        arrive()
    }

    private fun arrive() {
        visibility = VISIBLE
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // Reduced motion: the picture is still worth having, so it appears and stays for
            // this turn rather than animating, and it does not rock at all.
            here = 1f; tilt = 0f; invalidate(); return
        }
        startRocking()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ARRIVE + HOLD + LEAVE
            interpolator = null
            addUpdateListener {
                val t = (it.animatedValue as Float) * duration
                here = when {
                    t < ARRIVE -> EASE.getInterpolation(t / ARRIVE)
                    t < ARRIVE + HOLD -> 1f
                    else -> 1f - EASE.getInterpolation((t - ARRIVE - HOLD) / LEAVE)
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator !== animation) return
                    stopRocking(); here = 0f; visibility = GONE; invalidate()
                }
            })
            start()
        }
    }

    /**
     * The slow sway, a scrap of paper held up in a moving car.
     *
     * It swings the whole span rather than settling, because the point of it is movement at
     * the edge of a child's eye while they are being asked to look somewhere else.
     */
    private fun startRocking() {
        rock?.cancel()
        rock = ValueAnimator.ofFloat(-1f, 1f).apply {
            duration = SWAY
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { tilt = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopRocking() { rock?.cancel(); rock = null; tilt = 0f }

    override fun onDetachedFromWindow() { stopRocking(); animator?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        if (here <= 0f) return
        val alpha = (255 * here).toInt().coerceIn(0, 255)
        val photo = scrap
        canvas.save()
        // The sway turns about the middle of the bottom edge, the way something pinned at the
        // top of a car would hang, rather than about its own centre.
        canvas.rotate(SWING * tilt, width / 2f, height.toFloat())
        if (photo != null) {
            drawScrap(canvas, photo, alpha)
        } else {
            val shape = picture
            val side = minOf(width, height).toFloat()
            if (shape != null && side > 0f) {
                // Arrives a little low and settles, which is what makes it read as put down
                // rather than switched on.
                canvas.translate((width - side) / 2f, (height - side) / 2f + side * .12f * (1f - here))
                draw(canvas, side, shape, alpha)
            }
        }
        canvas.restore()
    }

    /**
     * The photograph as a clipping: torn paper behind it, and the ink of cheap newsprint.
     *
     * Newspaper rather than a plain photograph because a plain one over a photograph of the
     * same place is just the same picture twice. Printed and torn, it reads as something
     * somebody kept, which is nearer what this app is doing with these places.
     */
    private fun drawScrap(canvas: Canvas, photo: Bitmap, alpha: Int) {
        val pad = width * .07f
        val room = RectF(pad, pad, width - pad, height - pad)
        val scale = minOf(room.width() / photo.width, room.height() / photo.height)
        val w = photo.width * scale
        val h = photo.height * scale
        val at = RectF(
            room.centerX() - w / 2, room.centerY() - h / 2,
            room.centerX() + w / 2, room.centerY() + h / 2,
        )
        torn(at, pad * .8f)
        paper.alpha = alpha
        canvas.drawPath(page, paper)
        news.alpha = alpha
        canvas.drawBitmap(photo, null, at, news)
    }

    /**
     * A torn edge, the same every time.
     *
     * Built from a fixed sequence rather than a random one: a scrap that tore differently each
     * time it appeared would be the app redrawing itself, which is exactly what a kept clipping
     * is not.
     */
    private fun torn(at: RectF, bleed: Float) {
        page.reset()
        val steps = 11
        var seed = 7
        fun jitter(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 100) / 100f }
        val left = at.left - bleed; val right = at.right + bleed
        val top = at.top - bleed; val bottom = at.bottom + bleed
        page.moveTo(left, top)
        (1..steps).forEach { page.lineTo(left + (right - left) * it / steps, top + bleed * (jitter() - .5f)) }
        (1..steps).forEach { page.lineTo(right + bleed * (jitter() - .5f), top + (bottom - top) * it / steps) }
        (1..steps).forEach { page.lineTo(right - (right - left) * it / steps, bottom + bleed * (jitter() - .5f)) }
        (1..steps).forEach { page.lineTo(left + bleed * (jitter() - .5f), bottom - (bottom - top) * it / steps) }
        page.close()
    }

    /**
     * The pictures themselves, drawn on a 100 by 100 field and scaled to whatever room there is.
     *
     * Kept here rather than in the view so a check can render every one of them without a
     * screen, an animator or a Looper. The view is the fade; this is the drawing.
     */
    companion object {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        private val piece = Path()
        private val box = RectF()

        private val cream = Color.rgb(255, 239, 196)
        private val ink = Color.rgb(11, 45, 63)
        private val sky = Color.rgb(151, 215, 255)

        const val ARRIVE = 420L
        const val HOLD = 2400L
        const val LEAVE = 600L
        /** How far the scrap swings to each side, in degrees. */
        const val SWING = 23f
        /** One sweep from one side to the other. Slow enough to read as hanging, not shaking. */
        const val SWAY = 1500L
        val EASE = DecelerateInterpolator()

        /**
         * @param side the square to fill.
         * @param alpha 0 to 255, so the view can fade the whole picture at once.
         */
        fun draw(canvas: Canvas, side: Float, picture: TopicPicture, alpha: Int = 255) {
            if (side <= 0f || alpha <= 0) return
            fill.alpha = alpha; line.alpha = alpha
            line.strokeWidth = side * .038f
            canvas.save()
            canvas.scale(side / 100f, side / 100f)
            when (picture) {
                TopicPicture.DINOSAUR -> dinosaur(canvas)
                TopicPicture.SPACE -> space(canvas)
                TopicPicture.ANIMAL -> animal(canvas)
                TopicPicture.VEHICLE -> vehicle(canvas)
                TopicPicture.PALETTE -> palette(canvas)
                TopicPicture.MUSIC -> music(canvas)
                TopicPicture.ROBOT -> robot(canvas)
                TopicPicture.GEAR -> gear(canvas)
                TopicPicture.HOURGLASS -> hourglass(canvas)
                TopicPicture.BRUSH -> brush(canvas)
                TopicPicture.LEAF -> leaf(canvas)
                TopicPicture.BOWL -> bowl(canvas)
            }
            canvas.restore()
            fill.alpha = 255; line.alpha = 255
        }

        /** Fill then outline, so every shape gets the same cut-out edge. */
        private fun shape(canvas: Canvas, colour: Int = cream, build: Path.() -> Unit) {
            path.reset(); path.build()
            fill.color = colour; canvas.drawPath(path, fill)
            line.color = ink; canvas.drawPath(path, line)
        }

        /**
         * One outline around several overlapping parts.
         *
         * A body, a neck and two legs drawn one after another are four outlines, and the seams
         * where they overlap turn the animal into a pile of shapes. Merging them first leaves
         * the single silhouette a cut-out is supposed to be.
         */
        private fun blob(canvas: Canvas, colour: Int = cream, vararg parts: Path.() -> Unit) {
            path.reset()
            parts.forEach { part ->
                piece.reset(); piece.part()
                path.op(piece, Path.Op.UNION)
            }
            fill.color = colour; canvas.drawPath(path, fill)
            line.color = ink; canvas.drawPath(path, line)
        }

        /** A flat spot of colour. No outline: at this size an outline would swallow it. */
        private fun dot(canvas: Canvas, x: Float, y: Float, r: Float, colour: Int) {
            fill.color = colour; canvas.drawCircle(x, y, r, fill)
        }

        private fun dinosaur(canvas: Canvas) {
            // Long neck, round body, long tail, two stubby legs: the silhouette a six year old
            // draws when asked for a dinosaur.
            blob(canvas, cream,
                { addOval(RectF(14f, 44f, 74f, 80f), Path.Direction.CW) },
                {
                    moveTo(30f, 56f); cubicTo(18f, 60f, 8f, 70f, 2f, 82f)
                    cubicTo(16f, 80f, 28f, 74f, 36f, 68f); close()
                },
                {
                    moveTo(48f, 70f); cubicTo(48f, 42f, 58f, 26f, 72f, 18f)
                    lineTo(84f, 32f); cubicTo(74f, 40f, 68f, 52f, 66f, 70f); close()
                },
                { addCircle(80f, 24f, 12f, Path.Direction.CW) },
                { addRoundRect(RectF(22f, 68f, 36f, 92f), 6f, 6f, Path.Direction.CW) },
                { addRoundRect(RectF(48f, 68f, 62f, 92f), 6f, 6f, Path.Direction.CW) },
            )
            dot(canvas, 84f, 21f, 3f, ink)
        }

        private fun space(canvas: Canvas) {
            // A planet with a ring, and one star well clear of it. Any closer and the two
            // shapes merge into a single lump at this size.
            shape(canvas) { addCircle(44f, 58f, 26f, Path.Direction.CW) }
            dot(canvas, 36f, 50f, 7f, sky)
            dot(canvas, 55f, 66f, 5f, sky)
            box.set(8f, 46f, 88f, 70f)
            piece.reset(); piece.addOval(box, Path.Direction.CW)
            line.color = ink
            canvas.save(); canvas.rotate(-20f, 48f, 58f)
            canvas.drawPath(piece, line); canvas.restore()
            shape(canvas) {
                moveTo(80f, 6f); lineTo(85f, 19f); lineTo(98f, 24f)
                lineTo(85f, 29f); lineTo(80f, 42f); lineTo(75f, 29f)
                lineTo(62f, 24f); lineTo(75f, 19f)
                close()
            }
        }

        private fun animal(canvas: Canvas) = shape(canvas) {
            // A rabbit's head. Two long ears say "animal" faster than any body.
            moveTo(34f, 46f)
            cubicTo(28f, 30f, 28f, 14f, 36f, 12f)
            cubicTo(44f, 10f, 46f, 26f, 46f, 42f)
            cubicTo(52f, 40f, 58f, 40f, 64f, 42f)
            cubicTo(64f, 26f, 68f, 10f, 76f, 12f)
            cubicTo(84f, 14f, 82f, 32f, 76f, 46f)
            cubicTo(86f, 54f, 86f, 74f, 72f, 82f)
            cubicTo(60f, 90f, 44f, 88f, 34f, 78f)
            cubicTo(24f, 68f, 26f, 52f, 34f, 46f)
            close()
        }

        private fun vehicle(canvas: Canvas) {
            // A cabin over a long body, with the wheels hanging below so they stay round.
            shape(canvas) {
                moveTo(8f, 66f)
                lineTo(12f, 50f)
                cubicTo(14f, 42f, 20f, 36f, 32f, 36f)
                lineTo(58f, 36f)
                cubicTo(70f, 36f, 78f, 42f, 86f, 52f)
                cubicTo(92f, 58f, 94f, 62f, 92f, 66f)
                close()
            }
            dot(canvas, 30f, 70f, 12f, ink)
            dot(canvas, 72f, 70f, 12f, ink)
            dot(canvas, 30f, 70f, 4f, cream)
            dot(canvas, 72f, 70f, 4f, cream)
        }

        private fun palette(canvas: Canvas) {
            // A painter's palette: a blob with a thumb hole bitten out of it, and the paint on
            // it. The paint is filled flat — an outline as thick as the rest would leave three
            // dark spots instead of three colours.
            shape(canvas) {
                addOval(RectF(10f, 18f, 90f, 82f), Path.Direction.CW)
                addCircle(68f, 58f, 11f, Path.Direction.CCW)
            }
            dot(canvas, 32f, 40f, 8f, sky)
            dot(canvas, 52f, 31f, 8f, Color.rgb(240, 150, 100))
            dot(canvas, 32f, 64f, 8f, Color.rgb(150, 200, 150))
        }

        private fun music(canvas: Canvas) = blob(canvas, cream,
            {
                moveTo(46f, 72f); lineTo(46f, 16f)
                cubicTo(64f, 20f, 80f, 26f, 80f, 42f)
                cubicTo(72f, 32f, 60f, 30f, 54f, 30f)
                lineTo(54f, 72f); close()
            },
            { addOval(RectF(24f, 60f, 56f, 86f), Path.Direction.CW) },
        )

        private fun robot(canvas: Canvas) {
            blob(canvas, cream,
                { addRoundRect(RectF(14f, 30f, 86f, 86f), 16f, 16f, Path.Direction.CW) },
                { addRoundRect(RectF(47f, 12f, 53f, 32f), 3f, 3f, Path.Direction.CW) },
                { addCircle(50f, 12f, 6f, Path.Direction.CW) },
            )
            dot(canvas, 36f, 52f, 8f, ink)
            dot(canvas, 64f, 52f, 8f, ink)
            fill.color = ink
            canvas.drawRoundRect(RectF(36f, 68f, 64f, 76f), 4f, 4f, fill)
        }

        private fun gear(canvas: Canvas) {
            // Eight teeth around a ring: the shape of "how does this work". Each tooth is
            // built at the top and turned into place, which keeps every one identical.
            val teeth: Array<Path.() -> Unit> = Array(8) { i ->
                {
                    addRoundRect(RectF(-10f, -44f, 10f, -24f), 4f, 4f, Path.Direction.CW)
                    val turn = Matrix()
                    turn.setRotate(i * 45f)
                    turn.postTranslate(50f, 50f)
                    transform(turn)
                }
            }
            blob(canvas, cream, *teeth, { addCircle(50f, 50f, 30f, Path.Direction.CW) })
            dot(canvas, 50f, 50f, 11f, ink)
        }

        private fun hourglass(canvas: Canvas) {
            shape(canvas) {
                moveTo(24f, 14f); lineTo(76f, 14f)
                cubicTo(76f, 36f, 56f, 46f, 56f, 50f)
                cubicTo(56f, 54f, 76f, 64f, 76f, 86f)
                lineTo(24f, 86f)
                cubicTo(24f, 64f, 44f, 54f, 44f, 50f)
                cubicTo(44f, 46f, 24f, 36f, 24f, 14f)
                close()
            }
            line.color = ink
            canvas.drawLine(20f, 14f, 80f, 14f, line)
            canvas.drawLine(20f, 86f, 80f, 86f, line)
        }

        private fun brush(canvas: Canvas) {
            // The handle, the ferrule, and a wet tip under it.
            shape(canvas) {
                moveTo(74f, 10f); lineTo(90f, 26f); lineTo(52f, 64f); lineTo(36f, 48f); close()
            }
            shape(canvas, ink) {
                moveTo(36f, 48f); lineTo(52f, 64f); lineTo(44f, 72f); lineTo(28f, 56f); close()
            }
            shape(canvas, sky) {
                moveTo(28f, 56f); lineTo(44f, 72f)
                cubicTo(36f, 84f, 22f, 88f, 14f, 86f)
                cubicTo(12f, 78f, 16f, 64f, 28f, 56f)
                close()
            }
        }

        private fun leaf(canvas: Canvas) {
            shape(canvas, Color.rgb(176, 220, 176)) {
                moveTo(20f, 82f)
                cubicTo(18f, 48f, 42f, 18f, 84f, 16f)
                cubicTo(86f, 58f, 56f, 82f, 20f, 82f)
                close()
            }
            line.color = ink
            canvas.drawLine(20f, 82f, 76f, 26f, line)
        }

        private fun bowl(canvas: Canvas) {
            shape(canvas) {
                moveTo(16f, 52f); lineTo(84f, 52f)
                cubicTo(84f, 76f, 70f, 88f, 50f, 88f)
                cubicTo(30f, 88f, 16f, 76f, 16f, 52f)
                close()
            }
            line.color = ink
            // Two curls of steam: the one mark that says the bowl is hot.
            piece.reset()
            piece.moveTo(40f, 40f); piece.cubicTo(32f, 32f, 48f, 26f, 40f, 14f)
            canvas.drawPath(piece, line)
            piece.reset()
            piece.moveTo(62f, 40f); piece.cubicTo(54f, 32f, 70f, 26f, 62f, 16f)
            canvas.drawPath(piece, line)
        }
    }
}
