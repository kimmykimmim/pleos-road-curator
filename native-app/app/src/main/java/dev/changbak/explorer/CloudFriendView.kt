package dev.changbak.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import dev.changbak.explorer.domain.CloudFeature
import dev.changbak.explorer.domain.CloudForm
import dev.changbak.explorer.domain.CloudFriend

/** A quiet illustration of the child's description, never a view of the real sky. */
class CloudFriendView(
    context: Context,
    private val friend: CloudFriend? = null,
    private val reveal: Boolean = false,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = Color.rgb(255, 255, 255)
    private val outline = Color.rgb(171, 201, 224)
    private val ink = Color.rgb(64, 100, 132)
    private val blue = Color.rgb(221, 238, 250)
    private val gold = Color.rgb(249, 225, 165)
    private val blush = Color.rgb(247, 220, 214)

    init {
        isFocusable = false
        contentDescription = if (reveal && friend != null) {
            "내가 말한 모습으로 그린 ${friend.nickname ?: friend.label}"
        } else "구름 친구 그림"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val scale = minOf(width / 500f, height / 360f)
        canvas.save()
        canvas.translate((width - 500f * scale) / 2, (height - 360f * scale) / 2)
        canvas.scale(scale, scale)
        backdrop(canvas)

        val features = if (reveal) friend?.features.orEmpty() else emptyList()
        val size = when {
            CloudFeature.BIG in features -> 1.08f
            CloudFeature.SMALL in features -> 0.82f
            else -> 1f
        }
        val stretch = if (CloudFeature.LONG in features) 1.08f else 1f
        canvas.save()
        canvas.translate(250f, 190f)
        canvas.scale(size * stretch, size * if (CloudFeature.ROUND in features) 1.04f else 1f)
        canvas.translate(-250f, -190f)

        val form = if (reveal) friend?.form ?: CloudForm.CLOUD else CloudForm.CLOUD
        if (CloudFeature.LONG_TAIL in features && form != CloudForm.WHALE) longTail(canvas)
        if (CloudFeature.WINGS in features && form != CloudForm.DRAGON && form != CloudForm.BIRD) wings(canvas)
        when (form) {
            CloudForm.WHALE -> whale(canvas, CloudFeature.LONG_TAIL in features)
            CloudForm.DRAGON -> dragon(canvas)
            CloudForm.RABBIT -> rabbit(canvas)
            CloudForm.BIRD -> bird(canvas)
            CloudForm.DOG -> dog(canvas)
            CloudForm.CAT -> cat(canvas)
            CloudForm.TURTLE -> turtle(canvas)
            CloudForm.DINOSAUR -> dinosaur(canvas)
            CloudForm.BREAD -> bread(canvas)
            CloudForm.SHIP -> ship(canvas)
            CloudForm.HEART -> heart(canvas)
            CloudForm.CLOUD, CloudForm.OTHER -> cloud(canvas)
        }
        if (CloudFeature.FLUFFY in features) {
            // A few soft curls keep the selected outline readable.
            stroke(canvas, path { moveTo(205f, 247f); cubicTo(207f, 231f, 230f, 231f, 230f, 246f) }, blue, 4f)
            stroke(canvas, path { moveTo(266f, 260f); cubicTo(270f, 244f, 289f, 246f, 288f, 260f) }, blue, 4f)
        }
        canvas.restore()
        if (CloudFeature.BRIGHT in features) {
            sparkle(canvas, 98f, 123f, 9f)
            sparkle(canvas, 399f, 236f, 10f)
            sparkle(canvas, 349f, 68f, 7f)
        }
        canvas.restore()
    }

    private fun backdrop(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(250f, 30f, 250f, 322f,
            Color.rgb(230, 241, 251), Color.rgb(244, 249, 253), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(35f, 28f, 465f, 330f, 108f, 108f, paint)
        paint.shader = null
        fill(canvas, circle(390f, 90f, 24f), Color.rgb(255, 243, 208))
        fill(canvas, oval(141f, 304f, 362f, 321f), Color.rgb(222, 235, 246))
        fill(canvas, circle(97f, 202f, 4f), Color.rgb(190, 214, 234))
        fill(canvas, circle(413f, 276f, 3f), Color.rgb(190, 214, 234))
    }

    private fun cloud(canvas: Canvas) {
        val cloudBody = merge(
            rounded(119f, 186f, 382f, 262f, 38f),
            circle(158f, 197f, 42f), circle(213f, 168f, 62f),
            circle(279f, 174f, 56f), circle(334f, 207f, 43f),
        )
        body(canvas, cloudBody)
        face(canvas, 250f, 213f)
        fill(canvas, oval(198f, 225f, 217f, 234f), blush)
        fill(canvas, oval(282f, 225f, 301f, 234f), blush)
    }

    private fun whale(canvas: Canvas, long: Boolean) {
        val tail = if (long) path {
            moveTo(295f, 208f)
            cubicTo(360f, 252f, 404f, 207f, 405f, 166f)
            cubicTo(390f, 153f, 386f, 137f, 388f, 123f)
            quadTo(415f, 129f, 421f, 151f)
            quadTo(434f, 126f, 457f, 125f)
            quadTo(458f, 153f, 436f, 172f)
            cubicTo(431f, 251f, 380f, 288f, 292f, 252f)
            close()
        } else path {
            moveTo(300f, 204f)
            quadTo(349f, 227f, 373f, 193f)
            quadTo(365f, 170f, 374f, 153f)
            quadTo(397f, 161f, 399f, 182f)
            quadTo(413f, 162f, 438f, 168f)
            quadTo(428f, 195f, 406f, 205f)
            quadTo(371f, 271f, 295f, 255f)
            close()
        }
        body(canvas, merge(tail, path {
            moveTo(120f, 208f)
            cubicTo(117f, 150f, 168f, 126f, 221f, 137f)
            cubicTo(283f, 145f, 320f, 181f, 332f, 222f)
            cubicTo(318f, 264f, 220f, 284f, 159f, 253f)
            quadTo(120f, 237f, 120f, 208f)
            close()
        }))
        body(canvas, path {
            moveTo(237f, 222f); quadTo(275f, 223f, 283f, 250f)
            quadTo(255f, 260f, 237f, 222f); close()
        }, blue)
        eye(canvas, 173f, 201f)
        smile(canvas, 178f, 222f, 29f)
        stroke(canvas, path { moveTo(207f, 119f); quadTo(205f, 93f, 188f, 91f) }, outline, 4f)
        stroke(canvas, path { moveTo(211f, 118f); quadTo(217f, 96f, 232f, 98f) }, outline, 4f)
        fill(canvas, circle(184f, 89f, 5f), blue)
        fill(canvas, circle(236f, 98f, 4f), blue)
    }

    private fun dragon(canvas: Canvas) {
        body(canvas, path {
            moveTo(249f, 211f); quadTo(257f, 125f, 322f, 95f)
            quadTo(315f, 127f, 330f, 151f)
            quadTo(350f, 143f, 366f, 145f)
            quadTo(354f, 172f, 366f, 192f)
            quadTo(314f, 177f, 279f, 226f); close()
        }, blue)
        stroke(canvas, path { moveTo(267f, 206f); quadTo(290f, 163f, 322f, 119f) }, outline, 2.5f)
        body(canvas, path {
            moveTo(143f, 212f)
            cubicTo(112f, 212f, 103f, 184f, 132f, 175f)
            lineTo(155f, 175f)
            cubicTo(149f, 141f, 170f, 124f, 197f, 135f)
            quadTo(210f, 123f, 223f, 121f)
            lineTo(223f, 149f)
            cubicTo(251f, 160f, 255f, 190f, 255f, 211f)
            cubicTo(289f, 193f, 315f, 219f, 328f, 244f)
            quadTo(373f, 274f, 414f, 235f)
            quadTo(396f, 293f, 339f, 286f)
            quadTo(304f, 283f, 279f, 264f)
            lineTo(262f, 287f); lineTo(233f, 287f); lineTo(227f, 263f)
            lineTo(201f, 266f); lineTo(188f, 288f); lineTo(159f, 288f)
            lineTo(168f, 250f); quadTo(147f, 236f, 143f, 212f); close()
        })
        fill(canvas, path { moveTo(177f, 137f); lineTo(171f, 117f); quadTo(191f, 118f, 196f, 136f); close() }, gold)
        fill(canvas, oval(187f, 217f, 220f, 248f), blue)
        eye(canvas, 181f, 176f)
        fill(canvas, circle(130f, 191f, 2.7f), ink)
        smile(canvas, 143f, 203f, 23f)
    }

    private fun rabbit(canvas: Canvas) {
        body(canvas, merge(
            rounded(194f, 61f, 230f, 192f, 22f),
            rounded(265f, 70f, 301f, 192f, 22f),
            oval(184f, 146f, 310f, 257f), oval(196f, 219f, 302f, 293f),
            oval(179f, 268f, 225f, 296f), oval(276f, 267f, 323f, 296f),
            circle(315f, 247f, 22f),
        ))
        fill(canvas, rounded(206f, 82f, 219f, 158f, 8f), blush)
        fill(canvas, rounded(276f, 92f, 289f, 157f, 8f), blush)
        face(canvas, 247f, 195f, 25f)
        fill(canvas, circle(246f, 211f, 3.7f), blush)
        stroke(canvas, path { moveTo(212f, 239f); quadTo(223f, 250f, 220f, 264f) }, outline, 2.5f)
        stroke(canvas, path { moveTo(278f, 239f); quadTo(267f, 250f, 270f, 264f) }, outline, 2.5f)
    }

    private fun bird(canvas: Canvas) {
        body(canvas, merge(
            path { moveTo(291f, 217f); lineTo(365f, 178f); quadTo(358f, 205f, 346f, 217f); lineTo(374f, 219f); quadTo(349f, 258f, 298f, 256f); close() },
            oval(185f, 176f, 326f, 282f), circle(193f, 179f, 44f),
        ))
        body(canvas, path { moveTo(227f, 224f); quadTo(238f, 151f, 302f, 135f); quadTo(295f, 207f, 227f, 224f); close() }, blue)
        fill(canvas, path { moveTo(151f, 178f); lineTo(127f, 190f); lineTo(157f, 198f); close() }, gold)
        eye(canvas, 182f, 176f)
        fill(canvas, circle(184f, 197f, 7f), blush)
    }

    private fun dog(canvas: Canvas) {
        body(canvas, merge(oval(190f, 214f, 313f, 289f), circle(246f, 187f, 63f),
            rounded(177f, 257f, 222f, 293f, 17f), rounded(278f, 257f, 323f, 293f, 17f),
            path { moveTo(302f, 246f); quadTo(350f, 249f, 342f, 217f); quadTo(368f, 239f, 338f, 262f); close() }))
        body(canvas, oval(173f, 142f, 211f, 234f), blue)
        body(canvas, oval(284f, 144f, 321f, 234f), blue)
        face(canvas, 247f, 184f, 23f)
        fill(canvas, rounded(238f, 200f, 256f, 211f, 6f), ink)
    }

    private fun cat(canvas: Canvas) {
        body(canvas, path { moveTo(297f, 265f); cubicTo(359f, 275f, 377f, 215f, 348f, 205f); cubicTo(336f, 203f, 327f, 218f, 343f, 225f); cubicTo(347f, 243f, 324f, 249f, 296f, 245f); close() })
        body(canvas, merge(oval(190f, 213f, 313f, 292f), path {
            moveTo(186f, 192f); lineTo(187f, 119f); lineTo(224f, 151f)
            quadTo(247f, 141f, 270f, 152f); lineTo(310f, 120f); lineTo(311f, 192f)
            cubicTo(312f, 258f, 186f, 259f, 186f, 192f); close()
        }))
        fill(canvas, path { moveTo(199f, 142f); lineTo(201f, 175f); lineTo(220f, 164f); close() }, blush)
        fill(canvas, path { moveTo(296f, 142f); lineTo(278f, 164f); lineTo(298f, 175f); close() }, blush)
        face(canvas, 249f, 190f, 24f)
        fill(canvas, circle(249f, 207f, 3.5f), blush)
        for (y in listOf(209f, 220f)) {
            stroke(canvas, path { moveTo(194f, y); lineTo(216f, y + 2f) }, outline, 2f)
            stroke(canvas, path { moveTo(282f, y + 2f); lineTo(304f, y) }, outline, 2f)
        }
    }

    private fun turtle(canvas: Canvas) {
        body(canvas, merge(oval(171f, 249f, 204f, 283f), oval(257f, 249f, 291f, 283f),
            circle(328f, 229f, 35f), path { moveTo(159f, 242f); lineTo(130f, 254f); lineTo(164f, 263f); close() }))
        body(canvas, merge(rounded(161f, 212f, 306f, 266f, 24f), oval(165f, 149f, 303f, 275f)))
        stroke(canvas, path { moveTo(183f, 200f); quadTo(235f, 169f, 285f, 201f); moveTo(179f, 231f); quadTo(236f, 204f, 290f, 231f); moveTo(234f, 166f); lineTo(234f, 252f) }, blue, 6f)
        eye(canvas, 337f, 221f)
        smile(canvas, 335f, 235f, 18f)
    }

    private fun dinosaur(canvas: Canvas) {
        body(canvas, path {
            moveTo(170f, 239f); quadTo(142f, 259f, 112f, 240f)
            quadTo(120f, 284f, 178f, 269f); lineTo(187f, 290f); lineTo(216f, 290f)
            lineTo(220f, 267f); lineTo(267f, 267f); lineTo(271f, 290f); lineTo(300f, 290f)
            lineTo(310f, 224f); quadTo(317f, 202f, 316f, 182f)
            quadTo(359f, 181f, 353f, 153f); quadTo(351f, 125f, 317f, 128f)
            quadTo(286f, 126f, 282f, 163f); lineTo(274f, 212f)
            quadTo(202f, 186f, 170f, 239f); close()
        })
        fill(canvas, path { moveTo(183f, 226f); lineTo(180f, 206f); lineTo(202f, 214f); lineTo(217f, 191f); lineTo(232f, 210f); lineTo(250f, 194f); lineTo(259f, 214f); close() }, blue)
        eye(canvas, 326f, 149f)
        smile(canvas, 329f, 165f, 18f)
    }

    private fun bread(canvas: Canvas) {
        body(canvas, merge(rounded(155f, 176f, 348f, 284f, 28f),
            circle(189f, 172f, 45f), circle(250f, 158f, 54f), circle(311f, 172f, 45f)))
        stroke(canvas, path { moveTo(187f, 161f); quadTo(178f, 172f, 184f, 184f); moveTo(248f, 142f); quadTo(237f, 155f, 245f, 169f); moveTo(311f, 161f); quadTo(302f, 173f, 308f, 185f) }, gold, 7f)
        face(canvas, 251f, 230f)
    }

    private fun ship(canvas: Canvas) {
        stroke(canvas, path { moveTo(251f, 109f); lineTo(251f, 248f) }, outline, 5f)
        body(canvas, path { moveTo(241f, 122f); quadTo(178f, 147f, 155f, 216f); lineTo(241f, 216f); close() })
        body(canvas, path { moveTo(265f, 145f); quadTo(310f, 161f, 336f, 216f); lineTo(265f, 216f); close() }, blue)
        body(canvas, path { moveTo(130f, 240f); lineTo(370f, 240f); quadTo(351f, 294f, 296f, 293f); lineTo(202f, 293f); quadTo(151f, 291f, 130f, 240f); close() })
        face(canvas, 250f, 260f, 25f)
        fill(canvas, circle(160f, 309f, 8f), blue)
        fill(canvas, circle(345f, 310f, 6f), blue)
    }

    private fun heart(canvas: Canvas) {
        body(canvas, path {
            moveTo(250f, 291f)
            cubicTo(226f, 268f, 145f, 224f, 147f, 173f)
            cubicTo(149f, 117f, 214f, 104f, 250f, 154f)
            cubicTo(286f, 104f, 351f, 117f, 353f, 173f)
            cubicTo(355f, 224f, 274f, 268f, 250f, 291f); close()
        })
        face(canvas, 250f, 211f)
        fill(canvas, oval(198f, 222f, 219f, 232f), blush)
        fill(canvas, oval(281f, 222f, 302f, 232f), blush)
    }

    private fun wings(canvas: Canvas) {
        body(canvas, path { moveTo(192f, 210f); cubicTo(151f, 201f, 111f, 152f, 126f, 128f); quadTo(166f, 136f, 192f, 177f); close() }, blue)
        body(canvas, path { moveTo(308f, 210f); cubicTo(349f, 201f, 389f, 152f, 374f, 128f); quadTo(334f, 136f, 308f, 177f); close() }, blue)
    }

    private fun longTail(canvas: Canvas) {
        body(canvas, path {
            moveTo(291f, 244f); cubicTo(358f, 269f, 412f, 255f, 399f, 214f)
            cubicTo(394f, 195f, 410f, 186f, 421f, 200f)
            cubicTo(454f, 249f, 394f, 301f, 299f, 272f); close()
        })
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, size: Float) {
        fill(canvas, path {
            moveTo(x, y - size); quadTo(x + 2f, y - 2f, x + size, y)
            quadTo(x + 2f, y + 2f, x, y + size)
            quadTo(x - 2f, y + 2f, x - size, y)
            quadTo(x - 2f, y - 2f, x, y - size); close()
        }, gold)
    }

    private fun face(canvas: Canvas, x: Float, y: Float, distance: Float = 23f) {
        eye(canvas, x - distance, y)
        eye(canvas, x + distance, y)
        smile(canvas, x, y + 15f, 25f)
    }

    private fun eye(canvas: Canvas, x: Float, y: Float) = fill(canvas, oval(x - 3.6f, y - 4.6f, x + 3.6f, y + 4.6f), ink)

    private fun smile(canvas: Canvas, x: Float, y: Float, width: Float) = stroke(canvas,
        path { moveTo(x - width / 2f, y); quadTo(x, y + 12f, x + width / 2f, y) }, ink, 3f)

    private fun body(canvas: Canvas, body: Path, color: Int = white) {
        fill(canvas, body, color)
        stroke(canvas, body, outline, 2.8f)
    }

    private fun fill(canvas: Canvas, path: Path, color: Int) {
        paint.shader = null; paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    private fun stroke(canvas: Canvas, path: Path, color: Int, width: Float) {
        paint.shader = null; paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun circle(x: Float, y: Float, radius: Float) = path { addCircle(x, y, radius, Path.Direction.CW) }
    private fun oval(left: Float, top: Float, right: Float, bottom: Float) = path { addOval(RectF(left, top, right, bottom), Path.Direction.CW) }
    private fun rounded(left: Float, top: Float, right: Float, bottom: Float, radius: Float) = path {
        addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW)
    }
    private fun merge(vararg paths: Path) = Path().apply { paths.forEach { op(it, Path.Op.UNION) } }
}
