package dev.changbak.explorer

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import dev.changbak.explorer.audio.VoiceGuide
import dev.changbak.explorer.domain.*

/** User-started audio journey. No background microphone, route mutation, network upload or location history. */
class CuratorDriveService:Service() {
    inner class LocalBinder:Binder() { val service get()=this@CuratorDriveService }
    private val binder=LocalBinder()
    private val main=Handler(Looper.getMainLooper())
    private val engine=RouteCurator(CuratorCatalog.places)
    private val album by lazy { FamilyAlbumStore(this) }
    private val discovery=LiveNavigation()
    private lateinit var voice:VoiceGuide
    private lateinit var navi:CuratorNaviSource
    private lateinit var session:MediaSession
    private var generation=0L
    private var started=false
    private var suppressAudio=false
    var paused=false;private set
    var state=CuratorState();private set
    var connectionStatus="연결 준비 중";private set
    var timedCount=0;private set
    var nearbyCount=0;private set
    private val observers=mutableSetOf<()->Unit>()
    private val roleIdle=Runnable { if(!paused && state.phase==CuratorPhase.PLAY) perform(engine.listenOnly(state)) }
    private var explainToken=0L
    /** Each waiting phase leaves by the same path the child's own button uses. */
    private fun advanceAfterNarration(token:Long) {
        if(!NarrationRecovery.shouldAdvance(state.phase,token,generation,paused,suppressAudio)) return
        main.removeCallbacks(explainWatchdog)
        perform(if(state.phase==CuratorPhase.FAMILY) engine.afterFamilyMemory(state)
                else engine.afterExplanation(state,album.load()))
    }
    private val explainWatchdog=Runnable { advanceAfterNarration(explainToken) }
    fun observe(callback:()->Unit) { observers.add(callback);callback() }
    fun removeObserver(callback:()->Unit) { observers.remove(callback) }
    private fun publish() { observers.toList().forEach { it() };if(started) notificationManager().notify(71,notification()) }
    override fun onBind(intent:Intent)=binder
    override fun onCreate() {
        super.onCreate()
        notificationManager().createNotificationChannel(NotificationChannel("curator-journey","길 위의 음성 이야기",NotificationManager.IMPORTANCE_LOW))
        session=MediaSession(this,"CuratorJourney").apply {
            setCallback(object:MediaSession.Callback() {
                override fun onPause() { setPaused(true) }
                override fun onPlay() { setPaused(false) }
                override fun onStop() { end() }
            });isActive=true
        }
        voice=VoiceGuide(this,onState={},onCommand={},onError={ connectionStatus=it;publish() },initializeListening=false)
        navi=CuratorNaviSource(this,status={ connectionStatus=it;publish() },onFrame={ frame ->
            if(started && !paused) {
                val result=discovery.discover(frame)
                timedCount=result.timedCount;nearbyCount=result.nearbyCount
                if(!result.fix.fresh) {
                    if(state.routeValid) { silence();state=engine.navigationLost(state) }
                    if(frame.guiding) connectionStatus="최신 위치와 경로 정보를 기다리고 있어요."
                    publish()
                } else perform(engine.tick(state,result.fix))
            }
        })
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") { end();return START_NOT_STICKY }
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { stopSelf();return START_NOT_STICKY }
        try {
            startForeground(71,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch(_:Exception) { connectionStatus="위치 권한과 위치 설정을 확인해 주세요.";publish();stopSelf();return START_NOT_STICKY }
        if(!started) {
            started=true;paused=false;state=engine.start();updatePlayback();navi.connect()
            voice.speak("길에서 만날 이야기를 준비할게. 화면을 보지 않고 편하게 들어도 좋아.") {}
        }
        return START_NOT_STICKY
    }
    private fun notificationManager()=getSystemService(NotificationManager::class.java)
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,CuratorDriveService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"curator-journey").setSmallIcon(R.drawable.ic_explorer)
            .setContentTitle(if(paused) "길 위의 이야기 잠시 쉬는 중" else "길 위의 큐레이터")
            .setContentText(engine.place(state)?.name ?: "주변의 이야기를 기다리고 있어요")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"여행 끝내기",stop).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0)).build()
    }
    private fun updatePlayback() {
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
            .setState(if(paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f).build())
    }
    private fun silence() { generation++;voice.suspend();main.removeCallbacks(roleIdle);main.removeCallbacks(explainWatchdog) }
    fun setPaused(value:Boolean) {
        if(paused==value) return
        paused=value;silence()
        if(value) { navi.close();discovery.reset();state=engine.navigationLost(state) } else navi.connect()
        updatePlayback();publish()
    }
    /** A foreground dialog/answer must not be interrupted by automatic narration. */
    fun suppress(value:Boolean) {
        suppressAudio=value
        if(value) silence() else if(!paused && state.phase==CuratorPhase.PLAY) { main.removeCallbacks(roleIdle);main.postDelayed(roleIdle,20_000) }
    }
    fun accept(expected:CuratorState,turn:CuratorTurn) { if(started && !paused && state==expected) perform(turn) }
    fun repeat() { if(!paused) narrate(state.message) }
    private fun perform(turn:CuratorTurn) {
        // Navigation can continue tracking during a dialog, but cannot silently consume a spoken event.
        if(suppressAudio && turn.narration!=null) return
        val previous=state.phase;state=turn.state
        if(state.phase!=CuratorPhase.PLAY) main.removeCallbacks(roleIdle)
        if(state.phase==CuratorPhase.PLAY && previous!=state.phase) main.postDelayed(roleIdle,20_000)
        publish();turn.narration?.let(::narrate)
    }
    private fun narrate(text:String) {
        if(suppressAudio || paused || text.isBlank()) return
        generation++;val token=generation;voice.suspend();main.removeCallbacks(explainWatchdog)
        explainToken=token
        // Backstop for a narration that neither finishes nor reports a failure, so one silent
        // engine cannot hold the journey on a single place for the rest of the drive.
        main.postDelayed(explainWatchdog,(12_000L+text.length*200L).coerceAtMost(60_000L))
        val release:()->Unit={ advanceAfterNarration(token) }
        voice.speak(text,onAborted=release,onDone=release)
    }
    fun end() {
        silence();navi.close();started=false;paused=true;state=engine.finish(state).state;updatePlayback();publish()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() { main.removeCallbacksAndMessages(null);navi.close();voice.release();session.release();observers.clear();super.onDestroy() }
}
