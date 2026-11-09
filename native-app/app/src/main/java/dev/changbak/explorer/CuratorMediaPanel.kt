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
    private val video=VideoView(context).apply { setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE); visibility=View.INVISIBLE }
    private var current=CuratorState()
    private var permitted=false
    private var playingKey:String?=null
    private var generation=0L
    init { addView(video,LayoutParams(-1,-1,Gravity.CENTER));addView(scene,LayoutParams(-1,-1)) }
    fun present(state:CuratorState, allowed:Boolean) {
        current=state;permitted=allowed
        scene.present(state,allowed)
        val key=if(state.phase==CuratorPhase.EXPLAIN && allowed && ValueAnimator.areAnimatorsEnabled()) mediaKey(state) else null
        if(playingKey==key) return
        stop()
        if(key!=null) play(key)
    }
    fun replay() {
        if(!permitted || current.phase !in setOf(CuratorPhase.EXPLAIN,CuratorPhase.PLAY,CuratorPhase.MEMORY)) return
        val key=mediaKey(current) ?: return
        stop();play(key)
    }
    private fun mediaKey(state:CuratorState)=CuratorCatalog.places.firstOrNull { it.id==state.currentId }?.mediaKey
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
    override fun onDetachedFromWindow() {stop();super.onDetachedFromWindow()}
}
