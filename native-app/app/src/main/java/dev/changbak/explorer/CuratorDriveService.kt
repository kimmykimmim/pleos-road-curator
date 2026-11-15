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
    // The frame the app puts around a guardian's record is the app talking to the child, so it
    // follows the child's language. The guardian's own words inside it never do.
    private val album by lazy { FamilyAlbumStore(this) { prefs.profile.language } }
    private val prefs by lazy { JourneyPrefs(this) }
    // The catalog and the proximity list both grow when a guardian adds a place of their own.
    // The round count is read the same way the screen reads it, so a place asks the same
    // question whether the journey is being watched or only heard.
    // Read through, like the screen. The language is fixed for the journey because the speech
    // engines are told theirs when this service starts.
    private val profile: ChildProfile get() = prefs.profile
    private val engine=RouteCurator(
        { CuratorCatalog.places(profile.language)+album.load().ownCuratorPlaces() },
        { placeId -> prefs.rounds(placeId) },
        { profile },
    )
    private val discovery=LiveNavigation { LivePlaces.all+album.load().ownLocatedPlaces() }
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
    private lateinit var stillness:VehicleStillness
    /** The place whose short film was already offered, so a long red light does not repeat it. */
    private var offeredFilmFor:String?=null
    var carStopped=false;private set
    /** Whether the journey is saying something right now, so the screen can show it being said. */
    var speaking=false;private set
    /**
     * Rises each time the journey has finished saying something and has not moved on by itself.
     *
     * That is the moment the app is waiting for the back seat, and on a real drive it is the
     * only way the screen can know: the speaking happens here, in the service, so the question
     * finishes being read where nothing can open a microphone. The screen watches this number
     * and decides — it owns the guardian's setting, the car's restrictions and the microphone.
     */
    var waitingForAnswer=0L;private set
    /**
     * Standing still is the one moment when looking at the screen costs the child nothing,
     * so the film is offered then instead of being nagged about on the move.
     */
    private fun onStillnessChanged(value:Boolean) {
        carStopped=value
        if(!value) { publish();return }
        val place=engine.place(state)
        if(started && !paused && !suppressAudio && place!=null && offeredFilmFor!=place.id &&
            state.phase in setOf(CuratorPhase.EXPLAIN,CuratorPhase.FAMILY,CuratorPhase.PLAY,CuratorPhase.MEMORY)) {
            offeredFilmFor=place.id
            narrate("차가 멈췄네. 지금은 화면을 봐도 괜찮아. ${place.name} 짧은 영상 같이 볼래?")
        }
        publish()
    }
    /** Each waiting phase leaves by the same path the child's own button uses. */
    private fun advanceAfterNarration(token:Long) {
        if(!NarrationRecovery.shouldAdvance(state.phase,token,generation,paused,suppressAudio)) return
        main.removeCallbacks(explainWatchdog)
        perform(if(state.phase==CuratorPhase.FAMILY) engine.afterFamilyMemory(state)
                else engine.afterExplanation(state,album.load()))
    }
    /**
     * Say that the journey is now waiting.
     *
     * Only when this narration is still the current one: if the line that just ended moved the
     * journey along, the next line is already being read and nobody is waiting for anything.
     */
    private fun awaitAnswer(token:Long) {
        if(token!=generation || paused || suppressAudio) return
        waitingForAnswer++;publish()
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
        // The screen draws 두리 talking, and on a real drive the talking happens here.
        voice=VoiceGuide(this,onState={ value ->
            val now=value=="speaking"
            if(now!=speaking) { speaking=now;publish() }
        },onCommand={},onError={ connectionStatus=it;publish() },initializeListening=false,locale=profile.language.locale)
        stillness=VehicleStillness(this,::onStillnessChanged).also { it.start() }
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
            .setContentTitle(if(paused) "창밖 이야기 잠시 쉬는 중" else "창밖을 보라")
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
        // A place that reached its closing card was met all the way through, so the next journey
        // past it asks something else. Counted here as well as on screen, because a real drive
        // may run with the app in the background and never pass through the screen's own path.
        if(previous!=CuratorPhase.MEMORY && state.phase==CuratorPhase.MEMORY) state.currentId?.let(prefs::finished)
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
        val release:()->Unit={ advanceAfterNarration(token);awaitAnswer(token) }
        voice.speak(text,onAborted=release,onDone=release)
    }
    fun end() {
        silence();navi.close();started=false;paused=true;state=engine.finish(state).state;updatePlayback();publish()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() { main.removeCallbacksAndMessages(null);navi.close();stillness.release();voice.release();session.release();observers.clear();super.onDestroy() }
}
