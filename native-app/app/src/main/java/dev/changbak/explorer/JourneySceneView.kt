package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.*
import kotlin.math.*

/** Transparent overlay keeps the existing 3D surface attached during the trail-to-island morph. */
class JourneySceneView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var journey = SkyJourney()
    private var distance = 0f
    private var island = 0f
    private var reveal = 1f
    private var tunnelShade = 0f
    private var animator: ValueAnimator? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false }
    fun present(next: SkyJourney, animate: Boolean = true) {
        if (journey == next) return
        val fromDistance = distance; val fromIsland = island; val fromShade = tunnelShade
        val newLandmark = journey.landmarks != next.landmarks
        journey = next
        animator?.cancel()
        val targetIsland = if (next.complete) 1f else 0f
        val targetShade = if (next.tunnel && !next.complete) 1f else 0f
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            distance = next.meters; island = targetIsland; tunnelShade = targetShade; reveal = 1f; invalidate(); return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            addUpdateListener {
                val t = it.animatedValue as Float
                distance = fromDistance + (next.meters - fromDistance) * t
                island = fromIsland + (targetIsland - fromIsland) * t
                tunnelShade = fromShade + (targetShade - fromShade) * t
                reveal = if (newLandmark) t else 1f
                invalidate()
            }
            start()
        }
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (journey.source == JourneySource.NONE && journey.landmarks.isEmpty() && !journey.complete) return
        drawScene(canvas, width.toFloat(), height.toFloat(), journey, distance, island, reveal, paint, tunnelShade)
    }
    companion object {
        fun drawScene(c: Canvas, w: Float, h: Float, journey: SkyJourney, distance: Float = journey.meters,
            island: Float = if (journey.complete) 1f else 0f, reveal: Float = 1f, p: Paint = Paint(Paint.ANTI_ALIAS_FLAG), tunnelShade: Float = if (journey.tunnel && !journey.complete) 1f else 0f) {
            val baseY = h * .82f
            if (tunnelShade > 0f) {
                p.color = Color.argb((80 * tunnelShade).toInt(), 32, 53, 101); c.drawRect(0f, 0f, w, h, p)
                p.shader = RadialGradient(w*.63f, h*.58f, w*.16f,
                    intArrayOf(Color.argb((150*tunnelShade).toInt(),255,232,158), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                c.drawCircle(w*.63f,h*.58f,w*.16f,p)
                p.shader = null
            }
            if (island > 0f) {
                p.color = Color.argb((255 * island).toInt(), 184, 218, 246)
                c.drawOval(w*.08f, baseY-h*.07f, w*.92f, h*.98f, p)
                p.color = Color.argb((255 * island).toInt(), 211, 237, 216)
                c.drawOval(w*.08f, baseY-h*.10f, w*.92f, h*.91f, p)
            }
            val path = Path()
            val positions = ArrayList<PointF>()
            val reach = (.14f + .86f * (1f-exp(-distance/700f))).coerceIn(.14f, 1f)
            for (i in 0..100) {
                val t = i/100f
                val openX = w*(.10f + .80f*t)
                val openY = baseY + sin(t*PI*2.5).toFloat()*h*.065f
                val closedX = w*.5f + cos(t*PI*2).toFloat()*w*.32f
                val closedY = baseY + sin(t*PI*2).toFloat()*h*.065f
                val x = openX+(closedX-openX)*island
                val y = openY+(closedY-openY)*island
                positions.add(PointF(x,y))
                if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            val measure = PathMeasure(path, false)
            val trail = Path(); measure.getSegment(0f, measure.length*(reach+(1f-reach)*island), trail, true)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w*.018f
            p.color = Color.rgb(255, 250, 222); c.drawPath(trail,p)
            p.strokeWidth = w*.0035f; p.color = Color.rgb(102,160,206); c.drawPath(trail,p); p.style = Paint.Style.FILL
            journey.landmarks.forEachIndexed { index, waypoint ->
                val fraction = if (journey.meters <= 0f) .08f else (waypoint.meters / journey.meters * reach).coerceIn(.03f,.97f)
                val pos = FloatArray(2); measure.getPosTan(measure.length*fraction, pos, null)
                val alpha = if (index == journey.landmarks.lastIndex) reveal else 1f
                val crowded = index > 0 && kotlin.math.abs(waypoint.meters - journey.landmarks[index-1].meters) < journey.meters*.15f
                val offsetY = if (crowded) (if (index % 2 == 0) -h*.045f else h*.025f) else 0f
                c.save(); c.translate(pos[0],pos[1]+offsetY); c.scale(alpha*.25f+.75f,alpha*.25f+.75f)
                p.alpha = (255*alpha).toInt(); val s = w*.032f
                when (waypoint.kind) {
                    WaypointKind.TREES -> {
                        for (offset in listOf(-1.1f,0f,1.1f)) {
                            p.color = Color.argb((255*alpha).toInt(),116,142,118); c.drawRoundRect(offset*s-s*.12f,-s*.4f,offset*s+s*.12f,s*.35f,s*.1f,s*.1f,p)
                            p.color = Color.argb((255*alpha).toInt(),92,176,126); c.drawCircle(offset*s,-s*.9f,s*.7f,p)
                        }
                    }
                    WaypointKind.BRIDGE -> { p.color=Color.argb((255*alpha).toInt(),156,145,208); p.style=Paint.Style.STROKE; p.strokeWidth=s*.3f; c.drawArc(-s*1.4f,-s*1.3f,s*1.4f,s*.8f,180f,180f,false,p); p.style=Paint.Style.FILL }
                    WaypointKind.MOUNTAIN -> { p.color=Color.argb((255*alpha).toInt(),129,175,185); val mountain=Path().apply { moveTo(-s,0f); lineTo(0f,-s*1.7f); lineTo(s,0f); close() }; c.drawPath(mountain,p) }
                    WaypointKind.WATER -> { p.color=Color.argb((255*alpha).toInt(),101,185,230); c.drawOval(-s*1.5f,-s*.6f,s*1.5f,s*.4f,p) }
                    WaypointKind.TUNNEL -> { p.color=Color.argb((255*alpha).toInt(),84,104,157); c.drawRoundRect(-s,-s*1.6f,s,s*.3f,s,s,p); p.color=Color.argb((255*alpha).toInt(),255,226,146); c.drawCircle(0f,-s*.6f,s*.35f,p) }
                    WaypointKind.LIGHT -> { p.color=Color.argb((255*alpha).toInt(),255,200,95); c.drawCircle(0f,-s*.7f,s*.7f,p) }
                }
                c.restore()
            }
            p.alpha = 255
            if (journey.source == JourneySource.DEMO) {
                p.color = Color.rgb(84,111,144); p.textSize = w*.027f; p.textAlign=Paint.Align.CENTER
                c.drawText("이동 시연",w*.5f,h*.985f,p)
            }
        }
    }
}
