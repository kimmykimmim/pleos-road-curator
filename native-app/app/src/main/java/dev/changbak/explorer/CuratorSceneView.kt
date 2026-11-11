package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import dev.changbak.explorer.domain.*

/** Code-drawn landmark sketch, not a photograph or a claim about the current camera view. */
class CuratorSceneView(context: Context): View(context) {
    /**
     * Where to look a place up. A guardian's own place is not in the bundled catalog, and
     * falling back to the catalog would draw it with another place's artwork.
     */
    var places: () -> List<CuratorPlace> = { CuratorCatalog.places }
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
        if(progress<1f) { canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),((1f-progress)*255).toInt()); drawScene(canvas,width.toFloat(),height.toFloat(),prior,lookUp(prior)); canvas.restore() }
        canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(progress*255).toInt())
        drawScene(canvas,width.toFloat(),height.toFloat(),state,lookUp(state)); canvas.restore()
    }
    private fun lookUp(value: CuratorState) = places().firstOrNull { it.id==value.currentId }

    companion object {
        /**
         * [place] is passed in rather than looked up here: a guardian's own place lives in their
         * records, not in the bundled catalog, and defaulting would draw another place's artwork.
         */
        fun drawScene(c: Canvas,w: Float,h: Float,state: CuratorState,
                      place: CuratorPlace? = CuratorCatalog.places.firstOrNull { it.id==state.currentId }) {
            val p=Paint(Paint.ANTI_ALIAS_FLAG)
            fun line(x1:Float,y1:Float,x2:Float,y2:Float,color:Int,width:Float) {
                p.color=color;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;c.drawLine(x1,y1,x2,y2,p)
            }
            val teal=Color.rgb(8,98,136);val dark=Color.rgb(11,45,63);val pale=Color.rgb(183,214,238)
            p.color=Color.rgb(205,225,240);c.drawOval(w*.06f,h*.22f,w*.94f,h*.82f,p)
            val mystery=state.phase in setOf(CuratorPhase.READY,CuratorPhase.WAITING)
            if(mystery) {
                p.color=Color.rgb(255,239,196);c.drawCircle(w*.5f,h*.46f,w*.21f,p)
                p.color=teal;p.textAlign=Paint.Align.CENTER;p.textSize=h*.23f;p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                c.drawText("?",w*.5f,h*.54f,p)
            } else {
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

            val stage=when(state.phase) { CuratorPhase.PREDICT -> 0;CuratorPhase.WATCH -> 1;CuratorPhase.EXPLAIN -> 2;CuratorPhase.PLAY,CuratorPhase.COMPARE,CuratorPhase.MEMORY,CuratorPhase.FINISHED -> 3;else -> -1 }
            line(w*.16f,h*.9f,w*.84f,h*.9f,pale,w*.012f)
            if(stage>0) line(w*.16f,h*.9f,w*(.16f+stage*.2267f),h*.9f,teal,w*.012f)
            val names=listOf("예고","관찰","이야기","놀이")
            repeat(4) { i ->
                val x=w*(.16f+i*.2267f)
                p.color=if(i<=stage) teal else pale;c.drawCircle(x,h*.9f,w*.023f,p)
                p.textAlign=Paint.Align.CENTER;p.textSize=h*.043f;c.drawText(names[i],x,h*.985f,p)
            }
        }
    }
}
