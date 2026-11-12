package dev.changbak.explorer

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.changbak.explorer.domain.*

/** Bundled, silent clips never request audio focus or interrupt the spoken curator. */
class CuratorMediaPanel(context:Context): FrameLayout(context) {
    private val scene=CuratorSceneView(context)
    /** Set by the host so guardian places resolve here too, not just catalog ones. */
    var places: () -> List<CuratorPlace> = { CuratorCatalog.places }
        set(value) { field=value; scene.places=value }
    private val video=VideoView(context).apply { setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE); visibility=View.INVISIBLE }
    private val photos=PhotoSequenceView(context).apply { visibility=View.INVISIBLE }

    /** The photograph's caption is read by the child, so it follows their language. */
    var language: ChildLanguage = ChildLanguage.KOREAN
        set(value) { field=value; photos.language=value }
    private var photoPlaceId:String?=null
    private var current=CuratorState()
    private var permitted=false
    private var playingKey:String?=null
    private var generation=0L
    init {
        addView(video,LayoutParams(-1,-1,Gravity.CENTER));addView(scene,LayoutParams(-1,-1))
        addView(photos,LayoutParams(-1,-1))
    }

    /**
     * The artwork is drawn on a 960x540 field and centred, so a box taller than that ratio just
     * adds empty margin above and below the drawing. Keeping the panel at the drawing's own
     * shape puts that space back into the layout, where it reads as breathing room.
     */
    /**
     * Takes the height it is given, and only falls back to 16:9 when nobody has decided one.
     *
     * This used to force width times 540/960 always. In a tall panel that left a band of empty
     * colour as big as the picture itself between the picture and the place's name, which reads
     * as a layout fault rather than as space. The host now hands this view the room it has and
     * the content fits itself: a photograph fills and crops, a drawing sits centred at its own
     * proportions rather than stretching.
     */
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val width=MeasureSpec.getSize(widthSpec)
        val given=MeasureSpec.getSize(heightSpec)
        val height=if(MeasureSpec.getMode(heightSpec)==MeasureSpec.EXACTLY && given>0) given
            else (width*540f/960f).toInt().let { wide ->
                if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED && given in 1 until wide) given else wide
            }
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
    }
    fun present(state:CuratorState, allowed:Boolean) {
        current=state;permitted=allowed
        scene.present(state,allowed)
        // While the child is still hunting for the place, a photograph of the real thing is
        // what turns the window into "어? 이거구나". Once it has been passed the drawing takes
        // over, because it explains what the window could not show.
        if(allowed && showPhotos(state)) return
        hidePhotos()
        val key=if(state.phase==CuratorPhase.EXPLAIN && allowed && ValueAnimator.areAnimatorsEnabled()) mediaKey(state) else null
        if(playingKey==key) return
        stop()
        if(key!=null) play(key)
    }
    fun replay() {
        if(!permitted || current.phase !in setOf(CuratorPhase.EXPLAIN,CuratorPhase.PLAY,CuratorPhase.MEMORY)) return
        if(photos.visibility==View.VISIBLE) { photos.restart();return }
        val key=mediaKey(current) ?: return
        stop();play(key)
    }
    /** True when this place showed its own photographs, so nothing else should take the screen. */
    private fun showPhotos(state:CuratorState):Boolean {
        val stages=when(state.phase) {
            CuratorPhase.PREDICT,CuratorPhase.WATCH -> PhotoStages.forRecognising(state.currentId)
            CuratorPhase.EXPLAIN -> PhotoStages.forExplaining(state.currentId)
            else -> emptyList()
        }
        if(stages.isEmpty()) return false
        if(photoPlaceId!=state.currentId+state.phase.name) {
            stop()
            photos.load(stages)
            if(!photos.hasPhotos) { photoPlaceId=null;return false }
            photoPlaceId=state.currentId+state.phase.name
            photos.visibility=View.VISIBLE
            scene.animate().cancel();scene.alpha=0f
            photos.restart()
        }
        return true
    }
    private fun hidePhotos() {
        if(photoPlaceId==null && photos.visibility!=View.VISIBLE) return
        photoPlaceId=null
        photos.stop();photos.release();photos.visibility=View.INVISIBLE
        scene.animate().cancel();scene.alpha=1f
    }
    private fun mediaKey(state:CuratorState)=places().firstOrNull { it.id==state.currentId }?.mediaKey?.takeIf { it.isNotBlank() }
    private fun play(key:String) {
        val resource=resources.getIdentifier("curator_$key","raw",context.packageName)
        if(resource==0) return
        val token=++generation;playingKey=key
        video.visibility=View.VISIBLE
        video.setOnPreparedListener { player ->
            if(token==generation && permitted) { player.setVolume(0f,0f);player.isLooping=false;video.start() }
        }
        video.setOnInfoListener { _,what,_ ->
            if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && token==generation) {
                scene.animate().alpha(0f).setDuration(350).start()
            };false
        }
        video.setOnErrorListener { _,_,_ -> if(token==generation) stop();true }
        video.setVideoURI(Uri.parse("android.resource://${context.packageName}/$resource"))
    }
    fun stop() {
        generation++;playingKey=null;scene.animate().cancel();scene.alpha=1f
        video.stopPlayback();video.visibility=View.INVISIBLE
    }
    override fun onDetachedFromWindow() {stop();hidePhotos();super.onDetachedFromWindow()}
}
