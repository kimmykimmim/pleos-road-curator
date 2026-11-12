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
            val teal=Color.rgb(8,98,136)
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

            // The four beats used to be drawn here as a labelled strip. The screen now shows
            // them as dots under the turn, where the child is already reading, and the picture
            // keeps the whole panel to itself. Two copies of the same thing was one too many,
            // and on the deep panel the strip's own colours could not be read at all.
        }
    }
}
