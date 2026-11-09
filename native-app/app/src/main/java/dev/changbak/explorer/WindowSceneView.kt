package dev.changbak.explorer

import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.Environment

/** Quiet decorative illustration: never presents itself as a live vehicle camera. */
class WindowSceneView(context: Context, private val environment: Environment) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun fill(canvas: Canvas, color: String, draw: (Paint) -> Unit) {
        paint.color = Color.parseColor(color); paint.style = Paint.Style.FILL; draw(paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 420f, height / 360f)
        canvas.save(); canvas.translate((width - 420 * scale) / 2, (height - 360 * scale) / 2); canvas.scale(scale, scale)
        fill(canvas, "#FFFFFF") { canvas.drawRoundRect(14f, 14f, 406f, 310f, 62f, 62f, it) }
        val path = Path().apply { addRoundRect(RectF(30f, 30f, 390f, 294f), 48f, 48f, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        val night = environment == Environment.NIGHT || environment == Environment.TUNNEL
        fill(canvas, if (night) "#243961" else "#DAECFD") { canvas.drawRect(30f, 30f, 390f, 294f, it) }
        fill(canvas, if (night) "#FFE8A4" else "#FFE29A") { canvas.drawCircle(315f, 87f, 28f, it) }
        if (environment == Environment.CITY) {
            fill(canvas, "#B7CEE3") { canvas.drawRoundRect(48f, 125f, 130f, 270f, 8f, 8f, it); canvas.drawRoundRect(148f, 86f, 234f, 272f, 8f, 8f, it) }
            fill(canvas, "#EFF7FD") { for (x in listOf(65f, 99f, 166f, 201f)) for (y in listOf(148f, 180f, 212f)) canvas.drawRoundRect(x, y, x + 17, y + 16, 3f, 3f, it) }
        } else if (environment == Environment.TUNNEL) {
            fill(canvas, "#6C86AA") { canvas.drawRoundRect(60f, 54f, 360f, 340f, 110f, 110f, it) }
            fill(canvas, "#243961") { canvas.drawRoundRect(106f, 94f, 314f, 340f, 88f, 88f, it) }
            fill(canvas, "#FFF0B3") { for (x in listOf(81f, 329f)) for (y in listOf(135f, 190f, 245f)) canvas.drawRoundRect(x, y, x + 13, y + 20, 5f, 5f, it) }
        } else {
            fill(canvas, if (night) "#3B6D78" else "#A8D5BC") { canvas.drawOval(-40f, 182f, 280f, 380f, it) }
            fill(canvas, if (night) "#407D80" else "#79BD9D") { canvas.drawOval(150f, 184f, 480f, 400f, it) }
            fill(canvas, "#668777") { canvas.drawRoundRect(112f, 160f, 123f, 256f, 5f, 5f, it) }
            fill(canvas, if (night) "#679F90" else "#5BAD8A") { canvas.drawCircle(115f, 150f, 38f, it); canvas.drawCircle(92f, 179f, 32f, it); canvas.drawCircle(143f, 179f, 32f, it) }
            if (!night) fill(canvas, "#F7FCFF") { canvas.drawRoundRect(176f, 93f, 256f, 116f, 15f, 15f, it); canvas.drawCircle(203f, 92f, 19f, it); canvas.drawCircle(227f, 95f, 15f, it) }
        }
        canvas.restore()
        // One friendly companion, with no moving animation that competes with the window.
        fill(canvas, "#7FD3B0") { canvas.drawRoundRect(152f, 243f, 278f, 354f, 48f, 48f, it); canvas.drawCircle(176f, 256f, 22f, it); canvas.drawCircle(252f, 256f, 22f, it) }
        fill(canvas, "#173D3A") { canvas.drawCircle(194f, 290f, 5f, it); canvas.drawCircle(238f, 290f, 5f, it) }
        paint.color = Color.parseColor("#173D3A"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(205f, 295f, 227f, 313f), 15f, 150f, false, paint)
        canvas.restore()
    }
}
