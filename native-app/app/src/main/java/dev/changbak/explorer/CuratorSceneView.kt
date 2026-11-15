package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.*

/** Code-drawn landmark sketch, not a photograph or a claim about the current camera view. */
class CuratorSceneView(context: Context): View(context) {
    init {
        // The explanation is made of photographs now, and the drawing routine has no context
        // of its own to read them with.
        ScenePhotos.attach(context)
    }

    /**
     * Where to look a place up. A guardian's own place is not in the bundled catalog, and
     * falling back to the catalog would draw it with another place's artwork.
     */
    var places: () -> List<CuratorPlace> = { CuratorCatalog.places }

    /**
     * How much of the right edge is covered by the sheet holding this turn's question.
     *
     * A photograph is the ground of the whole screen and is happy to run underneath the sheet.
     * A drawing is not: its labels sit at its edges, so it centres itself in the part of the
     * screen that is actually visible. Animated when the sheet comes and goes, which is what
     * makes an explanation feel like the picture growing into the room the sheet left.
     */
    var coveredRight = 0f
        set(value) { if (field != value) { field = value; invalidate() } }

    /**
     * How much of the bottom edge belongs to the drive's own chrome.
     *
     * The route rule and the place's name sit over the view. A photograph wants them there; a
     * drawing centred in the full height would have its lower labels sitting behind them, so it
     * centres above that band instead.
     */
    var coveredBottom = 0f
        set(value) { if (field != value) { field = value; invalidate() } }
    /** Reused for the drawing's rounded edge; allocating one per frame would be per frame. */
    private val page = Path()
    private var state = CuratorState()
    private var prior = state
    private var progress = 1f
    private var animator: ValueAnimator? = null
    fun present(next: CuratorState, animate: Boolean) {
        val visualChange=state.phase!=next.phase || state.currentId!=next.currentId
        if(!visualChange) { state=next; return }
        prior=state; state=next; animator?.cancel()
        if(!animate || !ValueAnimator.areAnimatorsEnabled()) { progress=1f; invalidate(); return }
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=700L
            addUpdateListener { progress=it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        if(progress<1f) { canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),((1f-progress)*255).toInt()); letterboxed(canvas,prior); canvas.restore() }
        canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(progress*255).toInt())
        letterboxed(canvas,state); canvas.restore()
    }

    /**
     * Draws the scene at its own proportions, centred in whatever box this view was given.
     *
     * The compositions are laid out for 16:9 and carry labels at their edges. Stretching one to
     * fill a taller panel would bend the building it is explaining, so the drawing keeps its
     * shape and the panel's own colour shows above and below it.
     */
    private fun letterboxed(canvas: Canvas, value: CuratorState) {
        val w=width.toFloat()
        if(w<=0f || height<=0) return
        // A page has an edge. Without this margin the drawing runs from the screen's edge to
        // the sheet's edge and the two read as the left and right halves of one split panel,
        // which is the layout this composition exists to get away from.
        val gap=20f*resources.displayMetrics.density
        val free=(width-coveredRight-gap*2).coerceAtLeast(1f)
        val room=(height-coveredBottom).coerceAtLeast(1f)
        val artW=if(free/room>RATIO) room*RATIO else free
        val artH=if(free/room>RATIO) room else free/RATIO
        val x=gap+(free-artW)/2f
        // Centred on the screen, and lifted only as far as the chrome at the bottom actually
        // requires. A drawing that is already narrower than the room it has does not need to
        // ride high just because there is a route rule somewhere beneath it.
        val y=((height-artH)/2f).coerceAtMost(room-artH).coerceAtLeast(0f)
        // A drawing is a sheet of paper, the same material the question arrives on. Rounding
        // its corners is what separates it from the window behind it: without that it reads as
        // a pale rectangle stuck to the screen rather than as something laid on top.
        val radius=12f*resources.displayMetrics.density
        canvas.save()
        page.reset(); page.addRoundRect(x,y,x+artW,y+artH,radius,radius,Path.Direction.CW)
        canvas.clipPath(page)
        canvas.translate(x,y)
        drawScene(canvas,artW,artH,value,lookUp(value))
        canvas.restore()
    }
    private fun lookUp(value: CuratorState) = places().firstOrNull { it.id==value.currentId }

    companion object {
        /** What the drawings are composed for. */
        private const val RATIO = 960f/540f

        /**
         * A road going away from you, bending twice.
         *
         * This is the one picture the app can honestly show before it knows anything: not a
         * place, not a question, just the drive. It narrows as it goes because that is how a
         * road looks from a car, and the dashes shorten with it.
         */
        private fun road(c: Canvas,w: Float,h: Float,p: Paint) {
            val far=h*.30f
            // Built out from the middle rather than as two free curves. Two curves drawn by
            // hand bowed apart near the bottom and the whole thing read as a hill; a width
            // measured off the centre line at every step can only ever be a road.
            val steps=28
            val lane=Path()
            (0..steps).forEach { i ->
                val t=i/steps.toFloat()
                val x=spineX(w,t)-halfWidth(w,t)
                if(i==0) lane.moveTo(x,spineY(h,t)) else lane.lineTo(x,spineY(h,t))
            }
            (steps downTo 0).forEach { i ->
                val t=i/steps.toFloat()
                lane.lineTo(spineX(w,t)+halfWidth(w,t),spineY(h,t))
            }
            lane.close()
            p.style=Paint.Style.FILL
            // Light enough to read as a surface. A dark band on a dark ground was a hill.
            p.shader=android.graphics.LinearGradient(0f,far,0f,h,
                Color.rgb(58,90,110),Color.rgb(104,132,150),android.graphics.Shader.TileMode.CLAMP)
            c.drawPath(lane,p)
            p.shader=null
            // The far end fades into the ground rather than stopping at a hard line.
            p.shader=android.graphics.LinearGradient(0f,far-h*.02f,0f,far+h*.22f,
                Color.rgb(12,45,62),Color.argb(0,58,90,110),android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f,far-h*.02f,w,far+h*.22f,p)
            p.shader=null
            // The centre line. Each dash is shorter and thinner than the one before it.
            p.style=Paint.Style.STROKE
            p.strokeCap=Paint.Cap.ROUND
            p.color=Color.argb(225,255,239,196)
            val dashes=7
            (0 until dashes).forEach { step ->
                val t0=step/dashes.toFloat()
                val t1=t0+.055f*(1f-t0*.7f)
                p.strokeWidth=w*(.015f*(1f-t0)+.002f)
                c.drawLine(spineX(w,t0),spineY(h,t0),spineX(w,t1),spineY(h,t1),p)
            }
            p.style=Paint.Style.FILL
        }

        /** Half the road's width at this point along it: wide underfoot, a thread at the end. */
        private fun halfWidth(w: Float,t: Float): Float {
            val u=1f-t
            return w*(.30f*u*u*kotlin.math.sqrt(u)+.02f)
        }

        /** The middle of the road, as a curve from the bottom of the screen to the far end. */
        private fun spineX(w: Float,t: Float): Float = cubic(w*.50f,w*.30f,w*.74f,w*.50f,t)
        private fun spineY(h: Float,t: Float): Float = cubic(h*1.06f,h*.80f,h*.52f,h*.30f,t)

        private fun cubic(a: Float,b: Float,cc: Float,d: Float,t: Float): Float {
            val u=1f-t
            return u*u*u*a+3*u*u*t*b+3*u*t*t*cc+t*t*t*d
        }

        /**
         * [place] is passed in rather than looked up here: a guardian's own place lives in their
         * records, not in the bundled catalog, and defaulting would draw another place's artwork.
         */
        fun drawScene(c: Canvas,w: Float,h: Float,state: CuratorState,
                      place: CuratorPlace? = CuratorCatalog.places.firstOrNull { it.id==state.currentId }) {
            val p=Paint(Paint.ANTI_ALIAS_FLAG)
            val teal=Color.rgb(8,98,136)
            val mystery=state.phase in setOf(CuratorPhase.READY,CuratorPhase.WAITING)
            if(mystery) {
                // The road itself, bending away from you. 두리 is drawn standing on it by the
                // screen above this, so that it can breathe and blink.
                //
                // There used to be a pale oval here with a question mark on it. It was the
                // ground for the role play drawings borrowed for a moment it did not fit, and
                // it said nothing a child could read. A road says where this is going.
                road(c,w,h,p)
            } else {
                p.color=Color.rgb(205,225,240);c.drawOval(w*.06f,h*.22f,w*.94f,h*.82f,p)
                val variant=state.visits.firstOrNull { it.placeId==state.currentId }?.activityId.orEmpty()
                c.save();c.translate(0f,h*.03f);c.scale(1f,.82f)
                val pose=when {
                    state.phase in setOf(CuratorPhase.PREDICT,CuratorPhase.WATCH) -> .15f
                    state.phase==CuratorPhase.PLAY -> when(place?.visual) {
                        CuratorVisual.TOWER -> .35f
                        CuratorVisual.GLASS -> .55f
                        CuratorVisual.GATE,CuratorVisual.RECIPE -> 0f
                        else -> 1f
                    }
                    else -> 1f
                }
                CuratorMediaArt.draw(c,w,h,place?.visual ?: CuratorVisual.GATE,pose,variant)
                c.restore()
            }

            // The four beats used to be drawn here as a labelled strip. The screen now shows
            // them as dots under the turn, where the child is already reading, and the picture
            // keeps the whole panel to itself. Two copies of the same thing was one too many,
            // and on the deep panel the strip's own colours could not be read at all.
        }
    }
}
