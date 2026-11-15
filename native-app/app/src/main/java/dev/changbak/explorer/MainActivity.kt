package dev.changbak.explorer

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.text.LineBreakConfig
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import dev.changbak.explorer.audio.VoiceGuide
import dev.changbak.explorer.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** The vehicle shell belongs to Pleos; only this app area is replaced. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    // The frame the app puts around a guardian's record is the app talking to the child, so it
    // follows the child's language. The guardian's own words inside it never do.
    private val album by lazy { FamilyAlbumStore(this) { prefs.profile.language } }
    private val prefs by lazy { JourneyPrefs(this) }
    /**
     * Read through on every use, so a guardian who changes an interest sees it at the next
     * place rather than at the next launch. Language is the exception: the speech engines are
     * told theirs once, so changing it recreates the screen instead.
     */
    private val profile: ChildProfile get() = prefs.profile
    private val say by lazy { Phrases.of(profile.language) }
    private fun catalog() = CuratorCatalog.places(profile.language) + album.load().ownCuratorPlaces()
    private val engine = RouteCurator(
        { catalog() },
        { placeId -> prefs.rounds(placeId) },
        { profile },
    )
    private var state = CuratorState()
    private lateinit var voice: VoiceGuide
    private var drive: CuratorDriveService? = null
    private var serviceBound = false
    /** What the service had said by the time the screen last looked. */
    private var answeredUpTo = 0L
    private var livePhase: CuratorPhase? = null
    private val driveObserver: () -> Unit = {
        drive?.let { service ->
            state=service.state;naviStatus=service.connectionStatus;playing=!service.paused
            // A real journey runs its turns inside the service, so the screen has to notice a
            // new step here. Without this the exchange counter kept climbing across places and
            // the microphone stopped being offered after the first two answers of the drive.
            if(livePhase!=state.phase) { livePhase=state.phase;talkTurn=0;showChoices=false }
            if(foreground) render()
            showCompanion()
            if(service.waitingForAnswer!=answeredUpTo) {
                answeredUpTo=service.waitingForAnswer
                offerBackSeatTurn()
            }
        }
    }

    /**
     * The real journey has just finished asking something out loud.
     *
     * This is the back seat's turn on an actual drive. It used to exist only in the stopped-car
     * demonstration: the live path handed every turn to the service, which speaks with a voice
     * that cannot listen, so the one feature built for a child who cannot reach the screen
     * worked everywhere except on the road. Every condition that governs the demonstration
     * governs this too — the guardian's setting, the car's restrictions, one turn at a time.
     */
    private fun offerBackSeatTurn() {
        if (!usable() || !talking) return
        if (!engine.expectsAnswer(state, talkTurn)) return
        if (!BackSeat.shouldListen(state.phase, prefs.backSeatVoice, foreground,
                restricted, dialog != null, mic)) return
        openMic()
    }
    private val serviceConnection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            drive=(binder as CuratorDriveService.LocalBinder).service
            live=true;demo=false;drive?.observe(driveObserver)
        }
        override fun onServiceDisconnected(name:ComponentName) { drive=null;naviStatus="연결이 중단됐어요. 다시 시작해 주세요.";if(foreground) render() }
    }
    private fun bindDrive(create:Boolean=false) {
        if(!serviceBound) serviceBound=bindService(Intent(this,CuratorDriveService::class.java),serviceConnection,if(create) BIND_AUTO_CREATE else 0)
    }
    private fun leaveDrive() {
        drive?.removeObserver(driveObserver);drive?.end();drive=null
        if(serviceBound) { unbindService(serviceConnection);serviceBound=false }
        stopService(Intent(this,CuratorDriveService::class.java))
    }
    private fun startLive() {
        if(!usable()) return
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),71);return
        }
        if(!getSystemService(android.location.LocationManager::class.java).isLocationEnabled) {
            show(AlertDialog.Builder(this).setTitle("차량 위치를 켜 주세요").setMessage("주변 장소를 찾으려면 차량의 위치 설정이 필요해요.").setPositiveButton("닫기",null).create());return
        }
        quiet();scene.stop();playing=false;demo=false;live=true;state=engine.start()
        naviStatus="내비게이션에 연결하고 있어요.";render()
        try { startForegroundService(Intent(this,CuratorDriveService::class.java));bindDrive(true) }
        catch(_:Exception) { naviStatus="음성 여행을 시작하지 못했어요. 위치 권한을 확인해 주세요.";render() }
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==71) handler.post {
            if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startLive()
            else { status=say.needsLocation;render() }
        }
    }
    private var car: Car? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var connected = false
    private var restricted = true
    private var foreground = false
    private var demo = false
    private var playing = false
    private var demoTime = 0
    private var selectedRoute = CuratorRoutes.routes.first()
    private var sequence = 0L
    private var mic = false
    /** Which turn of the current place's spoken exchange we are on. Resets with the phase. */
    private var talkTurn = 0
    /** Set when someone asks for the buttons, so this place finishes on screen. */
    private var showChoices = false
    /** Whether anything was actually said while the microphone was open this turn. */
    private var answered = false
    /** Whether the journey was running when this turn's microphone was opened by hand. */
    private var resumeAfterMic = false
    /** Voice is the way in whenever the guardian has turned it on and the engine can listen. */
    private val talking: Boolean get() = prefs.backSeatVoice && !showChoices &&
        ::voice.isInitialized && voice.canListen
    // Set once the phrase pack can be read, which needs the child's saved language.
    private var status = ""
    private var naviStatus = ""
    private var live = false
    private var narrationGeneration = 0L
    private var cardGeneration = 0L
    private var dialog: AlertDialog? = null
    private var lastCard: List<Any?>? = null
    private lateinit var card: LinearLayout
    private lateinit var cardPanel: View
    private lateinit var scrim: View
    private lateinit var rule: RouteRule
    private lateinit var heroCaption: View

    private lateinit var meetingChip: TextView
    private lateinit var tagline: TextView
    /** Whether the sheet is out. It leaves while the app is the one talking. */
    private var sheetOut = true
    /** The moment a tap asked for the sheet back, so the next redraw does not take it away. */
    private var sheetByHand: Pair<CuratorPhase, String?>? = null
    private lateinit var scene: CuratorMediaPanel
    private lateinit var mediaButton: Button
    private lateinit var badge: TextView
    private lateinit var placeName: TextView
    /** The photograph's year, its one line, and who it belongs to. */
    /** The small picture that appears while a question arrives. */
    private lateinit var sticker: TopicStickerView
    /** 두리, who does the talking. */
    private lateinit var companion: CompanionView
    /** The same character, large, for the moments when there is no place to show yet. */
    private lateinit var waiting: CompanionView
    /** Whether this screen's own voice is mid-sentence. */
    private var speaking = false
    private lateinit var photoYear: TextView
    private lateinit var photoLine: TextView
    private lateinit var photoCredit: TextView
    private lateinit var subline: TextView
    private lateinit var tagRow: LinearLayout
    private lateinit var compass: WindowCompass
    /** What the tag row is showing, so it is only rebuilt when the place changes. */
    private var lastTags: List<String> = emptyList()
    private var statusLabel: TextView? = null
    private val ink = Palette.ink
    /**
     * Secondary type.
     *
     * It was #688297, which gives 4.0:1 on white and 3.3:1 on the page — under the 4.5:1 that
     * small text needs, and the notes at caption size are small text. Darkened until both
     * surfaces clear it, which in a moving car is worth more than the lighter grey looked.
     */
    private val muted = Palette.muted
    private val accent = Palette.accent
    /**
     * Daylight. What the page is made of.
     *
     * This was a warm cream, which is both the most common background in generated design and
     * the wrong story for this app: a child in a car is looking at sky and city, not at paper.
     * The tint is the palette's own sky, lightened until type sits on it comfortably.
     */
    private val ground = Palette.ground
    /** The window. One flat colour, because it is one thing you look through. */
    private val deep = Palette.deep
    /**
     * What lies behind the view when there is no picture filling it.
     *
     * Darker and greyer than the brand blue: it reads as the road at dusk rather than as a
     * panel, which is the point — it is ground, not a surface.
     */
    private val road = Palette.road
    private val pale = Palette.pale
    private val sky = Palette.sky
    /** The palette's cream, kept as a mark rather than a mood. See [chipMark]. */
    private val cream = Palette.paper
    /** White at low alpha, for a chip that has to sit on the illustration without boxing it in. */
    private val onHero = Palette.onHero
    /** The credit line over a photograph: quieter than the caption, still plainly readable. */
    private val onPhoto = Palette.onPhoto
    private val timer = object : Runnable {
        override fun run() {
            if (foreground && demo && playing && !restricted && dialog == null) {
                demoTime = (demoTime + 10).coerceAtMost(selectedRoute.endSeconds(album.load().ownPlaceIds))
                feedDemo()
            }
            if (foreground) handler.postDelayed(this, 1000L)
        }
    }
    private val roleIdle = Runnable {
        if (usable() && !live && state.phase==CuratorPhase.PLAY && !mic) apply(engine.listenOnly(state))
    }
    private fun armRoleIdle() {
        handler.removeCallbacks(roleIdle)
        if(foreground && !live && state.phase==CuratorPhase.PLAY && dialog==null) handler.postDelayed(roleIdle,20_000L)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = say.preparingVoice
        naviStatus = say.naviBefore
        voice = VoiceGuide(this, onState = { value -> runOnUiThread {
            val wasListening=mic
            status = when(value) { "speaking" -> say.nowSpeaking; "listening" -> say.nowListening; else -> say.noNeedToSpeak }
            speaking = value == "speaking"
            showCompanion()
            if (::voice.isInitialized && !voice.enabled) mic = false
            if(wasListening && !mic) {
                drive?.suppress(false); armRoleIdle()
                // The microphone closed. If nothing was said, the place still takes its turn:
                // going silent here would read as the app having stopped working.
                if(talking && !answered) handler.post(::heardNothing)
                if(resumeAfterMic) { resumeAfterMic = false; handler.post { resumeAfterListening(true) } }
            }
            // The card shows whether it is listening, so it has to be redrawn when that changes.
            if(wasListening != mic) { lastCard = null; render() }
            statusLabel?.text = status
        } }, onCommand = {}, onError = { value -> runOnUiThread { mic = false;drive?.suppress(false);status = value;statusLabel?.text = value } },
            onUtterance = { words -> runOnUiThread { if (mic && usable()) { answered = true; quiet(); accept(words) } } },
            locale = profile.language.locale)
        buildLayout(); render()
        try {
            car = Car.createCar(this, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready -> runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                connected = ready
                if (ready) try {
                    restrictions = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    restrictions?.registerListener(::updateRestrictions)
                    restrictions?.currentCarUxRestrictions?.let(::updateRestrictions)
                } catch (_: Exception) { restricted = true; quiet() }
                else { restricted = true; quiet() }
                render()
            } }
        } catch (_: Exception) { restricted = true; render() }
    }
    private fun updateRestrictions(value: CarUxRestrictions) {
        restricted = value.isRequiresDistractionOptimization
        if (restricted) { playing = false; quiet(); scene.stop(); dialog?.dismiss() }
        render()
    }
    override fun onResume() { super.onResume(); foreground = true; bindDrive();driveObserver(); handler.removeCallbacks(timer); handler.post(timer); if (::card.isInitialized) render() }
    override fun onPause() {
        foreground = false; playing = false; handler.removeCallbacks(roleIdle); quiet(); scene.stop(); dialog?.dismiss();drive?.suppress(false)
        handler.removeCallbacks(timer); cardGeneration++; card.animate().cancel(); lastCard = null
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); narrationGeneration++; voice.release();drive?.removeObserver(driveObserver);if(serviceBound) { unbindService(serviceConnection);serviceBound=false }
        runCatching { restrictions?.unregisterListener(); car?.disconnect() }; super.onDestroy()
    }
    private fun usable() = foreground && connected && !restricted && dialog == null
    /**
     * Runs a guardian choice after its menu is actually gone.
     *
     * A dialog item click queues its own dismiss as a message, so a plain handler.post from the
     * click runs first, while `dialog` still holds the closing menu. Every usable() check inside
     * that runnable then reads "a dialog is already up" and the choice does nothing at all: the
     * jump to a demo position, the end of the day, the child's typed answer. Closing the menu
     * here, before the work is queued, is what lets a menu choice reach the app.
     */
    private fun menuAction(action: () -> Unit) {
        dialog?.let { previous -> previous.setOnDismissListener(null); previous.dismiss() }
        dialog = null
        drive?.suppress(false)
        handler.post { action(); armRoleIdle() }
    }
    /** EXPLAIN and FAMILY both wait for narration; each leaves by its own next step. */
    private fun advanceAfterNarration() =
        if (state.phase == CuratorPhase.FAMILY) engine.afterFamilyMemory(state)
        else engine.afterExplanation(state, album.load())
    private fun quiet() { narrationGeneration++; mic = false; if (::voice.isInitialized) voice.suspend() }
    private fun apply(turn: CuratorTurn) {
        if (!usable()) return
        if(live) { drive?.accept(state,turn);return }
        val changed = turn.state != state
        val previousPhase=state.phase
        state = turn.state
        if(state.phase!=CuratorPhase.PLAY) handler.removeCallbacks(roleIdle)
        else if(previousPhase!=CuratorPhase.PLAY) armRoleIdle()
        if (previousPhase != state.phase) { talkTurn = 0; showChoices = false }
        if (changed) {
            // A place that reaches its closing card has been met all the way through, so the
            // next journey past it asks something else. Skipping before the story does not count.
            if (previousPhase != CuratorPhase.MEMORY && state.phase == CuratorPhase.MEMORY) {
                state.currentId?.let(prefs::finished)
            }
            render(); if (state.phase in setOf(CuratorPhase.MEMORY, CuratorPhase.FINISHED)) saveSummary()
        }
        val text = turn.narration ?: return
        quiet(); val token = narrationGeneration
        // A narration that fails must still release the phase that is waiting for it, and a
        // question that has finished being read is the moment the back seat gets to answer.
        val release: () -> Unit = {
            if (token == narrationGeneration && usable()) {
                if (NarrationRecovery.shouldAdvance(state.phase, token, narrationGeneration, false, false)) {
                    apply(advanceAfterNarration())
                } else if (talking && engine.expectsAnswer(state, talkTurn) &&
                    BackSeat.shouldListen(state.phase, prefs.backSeatVoice, foreground,
                        restricted, dialog != null, mic)) {
                    openMic()
                }
            }
        }
        voice.speak(text, onAborted = release, onDone = release)
    }
    private fun startDemo(route: CuratorDemoRoute = selectedRoute) {
        selectedRoute = route
        quiet(); scene.stop(); leaveDrive(); live = false; demo = true; playing = true; demoTime = 0; sequence = 0
        state = engine.start(); lastCard = null; render(); feedDemo()
    }
    private fun feedDemo() {
        if(!demo || live) return
        val own = album.load().ownPlaceIds
        apply(engine.tick(state, selectedRoute.fix(demoTime, ++sequence, own)))
        if (demoTime >= selectedRoute.endSeconds(own)) playing = false
    }

    private fun jump(seconds: Int) { quiet(); demoTime = seconds; feedDemo() }
    /** The answers currently drawn, so a child naming one out loud picks the same button. */
    private fun spokenChoices(): List<String> = when (state.phase) {
        // While the exchange is spoken there is nothing printed to have chosen, so whatever the
        // child says is their own answer and goes to the conversation rather than to a button.
        CuratorPhase.PREDICT -> if (talking) emptyList() else engine.prompt(state)?.choices.orEmpty()
        CuratorPhase.PLAY -> engine.place(state)?.activity?.choices?.map { it.label }.orEmpty()
        else -> emptyList()
    }

    /**
     * A sentence from the back seat. The same path serves the microphone and the guardian's
     * typed relay, so both reach the journey as the child's own answer.
     */
    private fun accept(text: String) {
        if (!usable()) return
        drive?.suppress(false)
        when (val said = SpokenReply.of(text, spokenChoices(), profile.language)) {
            SpokenIntent.Rest -> { playing = false; drive?.setPaused(true); quiet(); status = say.resting; render() }
            SpokenIntent.Skip -> apply(engine.skip(state))
            SpokenIntent.Repeat -> if (live) drive?.repeat() else { quiet(); voice.speak(state.message) }
            SpokenIntent.Unclear -> { status = say.sayAgain; statusLabel?.text = status }
            is SpokenIntent.Choose -> when (state.phase) {
                CuratorPhase.PREDICT -> engine.prompt(state)?.choices?.getOrNull(said.index)
                    ?.let { apply(engine.predict(state, it)) }
                CuratorPhase.PLAY -> engine.place(state)?.activity?.choices?.getOrNull(said.index)
                    ?.let { apply(engine.perform(state, it.id)) }
                else -> Unit
            }
            is SpokenIntent.OwnWords -> {
                val compact = SpokenReply.compact(said.text)
                when (state.phase) {
                    CuratorPhase.PREDICT -> if (talking) converse(said.text) else apply(engine.predict(state, said.text))
                    // Whether the child can see it is decided in the child's own language. The
                    // screen asks an English child to say "I see it", so that is what has to work.
                    CuratorPhase.WATCH -> when (SpokenReply.seen(said.text, profile.language)) {
                        false -> apply(engine.seen(state, false))
                        true -> apply(engine.seen(state, true))
                        null -> { status = say.sayIfYouSee; statusLabel?.text = status }
                    }
                    CuratorPhase.PLAY -> apply(engine.respondToPlay(state, said.text))
                    else -> { status = say.justListening; statusLabel?.text = status }
                }
            }
        }
    }
    /**
     * Opens the microphone for one answer, with nothing further to press.
     *
     * A guardian who turned on 뒷자리 말하기 has already answered the question this would ask,
     * and asking it again on screen would hand the decision back to the only person who can
     * reach the screen. That is precisely what the back seat cannot do, so this path does not
     * stop to confirm. It still opens for one turn and closes itself.
     */
    private fun openMic(resumeWhenDone: Boolean = false) {
        if (!usable() || mic) { if (resumeWhenDone) resumeAfterListening(true); return }
        resumeAfterMic = resumeWhenDone
        answered = false
        drive?.suppress(true)
        mic = voice.enableCommands()
        if (!mic) drive?.suppress(false)
        status = if (mic) say.micOpen else say.micClosed
        render()
    }

    /**
     * One turn of the spoken exchange.
     *
     * The reply is narrated, and when the child still has a turn left the microphone reopens by
     * itself from the narration's own completion. That is the loop: ask, listen, say something
     * back, listen again — with nothing on the screen to point at.
     */
    private fun converse(text: String) {
        talkTurn += 1
        apply(engine.heard(state, text, talkTurn))
    }

    /** Nothing was heard at all. The place still gets its turn rather than going silent. */
    private fun heardNothing() {
        if (!usable() || !talking || state.phase != CuratorPhase.PREDICT) return
        if (!engine.expectsAnswer(state, talkTurn)) return
        converse("")
    }

    /** The on-screen way in. Whoever presses it is agreeing to the microphone right then. */
    private fun listen() {
        if (!usable()) return
        if (mic) { quiet();drive?.suppress(false);render(); return }
        // Pressing this is joining in, not stopping. The demonstration pauses only while the
        // question is being asked, and whatever it was doing before carries on afterwards:
        // a child who pressed a button to answer should not have to find a grown-up to
        // restart the journey, and the only person who can reach that menu is driving.
        val wasPlaying = playing
        quiet(); playing = false
        show(AlertDialog.Builder(this).setTitle(say.sayItMyself)
            .setMessage(say.micAsk)
            .setNegativeButton(say.micLater) { _, _ -> resumeAfterListening(wasPlaying) }
            .setPositiveButton(say.micGo) { _, _ -> menuAction { openMic(wasPlaying) } }
            .setOnCancelListener { resumeAfterListening(wasPlaying) }
            .create())
    }

    /** Puts the journey back the way the child found it, once their turn is over. */
    private fun resumeAfterListening(wasPlaying: Boolean) {
        if (!wasPlaying || playing || live) { render(); return }
        playing = true
        render()
        if (demo) handler.postDelayed({ if (demo && playing) feedDemo() }, 600)
    }
    /**
     * Pleos gives this app about 1680dp of width; the reference screen this layout was drawn for
     * is about 1020dp. One scale carries both type and spacing, so the same layout reads the same
     * on each instead of sitting small in the corner of the larger screen.
     */
    private val uiScale by lazy {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        (widthDp / 1020f).coerceIn(1f, 1.5f)
    }
    private fun dp(n: Int) = (n*uiScale*resources.displayMetrics.density).toInt()
    private fun sp(n: Float) = n*uiScale
    /**
     * Six sizes, and nothing between them.
     *
     * Thirteen different sizes were in use, most of them a point or two apart, which reads as
     * noise rather than as hierarchy: nothing on the screen said "I am the sentence to read
     * first". A child glancing up from the back seat has about a second, so the steps are far
     * enough apart to be told apart at a glance, and the sentence size is small enough that a
     * question fits on two lines instead of pushing the buttons off the card.
     */
    private object Text {
        /** One per panel at most: the place's name, or the screen's one question. */
        const val DISPLAY = 30f
        /** The sentence this turn is about. */
        const val TITLE = 22f
        /** Anything you press. */
        const val ACTION = 20f
        /** Supporting sentences: the hook, the lead, the answer to a quiz. */
        const val BODY = 18f
        /** Names of things: a step, a chip, a place in a list. */
        const val LABEL = 16f
        /** Notes about the app rather than the journey. */
        const val CAPTION = 13f
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun rounded(color: Int, radius: Int = 28) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    /** A shape with no straight run long enough to read as a box. Buttons and chips use it. */
    private fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(999).toFloat() }

    /**
     * A shape's corner says what the thing is.
     *
     * One radius on everything is the house style of generated interfaces, and it throws away a
     * free channel: here the window is the softest shape on screen, paper is nearly square, and
     * anything you press is a pill. You can tell what a thing is before you read it.
     */
    private object Corner {
        /** Paper: the sheet, and the ones the guardian types on. */
        const val PAPER = 10
    }



    /** A small round badge, the way the reference numbers its rows. */
    private fun chip(text: String, fill: Int, textColor: Int, size: Int = 46) = TextView(this).apply {
        this.text = text; textSize = sp(Text.LABEL); setTextColor(textColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER; background = pill(fill)
        minWidth = dp(size); minHeight = dp(size); includeFontPadding = false
    }

    /** A label on the illustration: pale type inside a translucent pill, never a hard box. */
    private fun heroChip(text: String) = TextView(this).apply {
        this.text = text; textSize = sp(Text.LABEL); setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = pill(onHero); setPadding(dp(18),dp(8),dp(18),dp(9)); includeFontPadding = false
    }

    /**
     * The step's name.
     *
     * The phrase packs carry headings like "01  먼저 상상해 보기", and this drew the number in
     * a circle beside the name. The dots under the card already say which of four steps this
     * is, so the circle was a second device for one fact; the number goes, the name stays.
     */
    private fun stepHeader(text: String): View {
        val named = Regex("^\\d{1,2}\\s+(.*)$").find(text.trim())
        return label(named?.groupValues?.get(1) ?: text, Text.LABEL, accent, true)
    }

    /**
     * Where this place is in its four steps, as dots.
     *
     * The same four beats the scene draws, said once more where the child's eyes already are.
     */
    /** The one thing the memory list still has to say, said as a mark rather than a sentence. */
    private fun seenMark(seen: SeenStatus): View = row().apply {
        val found = seen == SeenStatus.SEEN
        addView(View(this@MainActivity).apply {
            background = if (found) pill(accent) else pill(Palette.pale)
        }, LinearLayout.LayoutParams(dp(if (found) 34 else 14), dp(9)))
    }
    private fun stepDots(active: Int): View = row().apply {
        gravity = Gravity.CENTER
        (0..3).forEach { index ->
            val on = index == active
            addView(View(this@MainActivity).apply { background = pill(if (on) accent else Palette.pale) },
                LinearLayout.LayoutParams(dp(if (on) 30 else 11), dp(11)).apply { marginStart = if (index == 0) 0 else dp(8) })
        }
    }
    /**
     * The one typeface with a personality, kept for the one word that carries it.
     *
     * Two families, as far apart as two families can reasonably be: a rounded Korean display
     * face for the largest thing on a panel, and the system sans for everything a child has to
     * read while the car is moving. Personality belongs where the eye lands first; legibility
     * belongs everywhere else. Loaded once, and the app falls back to the system face if the
     * file is ever missing rather than failing to draw a place's name.
     */
    private val displayFace: Typeface? by lazy {
        runCatching { Typeface.createFromAsset(assets, "fonts/Jua.ttf") }.getOrNull()
    }

    private fun label(text: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp(size); setTextColor(color); includeFontPadding = false; setLineSpacing(dp(5).toFloat(),1f)
        if (bold) typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL)
        // The display tier, and only the display tier, gets the display face.
        if (size >= Text.DISPLAY) displayFace?.let { typeface = it }
        wholeWords()
    }

    /**
     * Break Korean lines between words, and across even lines.
     *
     * Android's default for Korean is to wrap wherever the line runs out, which splits a word
     * down the middle: "달라지거 / 든." A six year old is sounding these out one syllable at a
     * time, and half a word at the end of a line is a word they cannot read. Android 13 added
     * phrase-aware breaking for exactly this; on anything older the default stands.
     */
    private fun TextView.wholeWords() {
        // Even lines, not a greedy first one. Breaking at words alone fixes the split syllable
        // and introduces a new fault: the first line fills up, one whole word drops to the
        // next, and a short line sits above a nearly empty one. Balanced spreads the words
        // over the lines it was going to use anyway, so neither line is a remnant.
        breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
        if (Build.VERSION.SDK_INT >= 33) {
            lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
        }
    }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = sp(Text.ACTION); isAllCaps = false
        // Dark type on the bright one. White on lime is unreadable, and this is the button a
        // child presses from the back seat.
        setTextColor(if (primary) Palette.onPrimary else ink)
        if (primary) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        // Fully rounded. A pill reads as something to press from across the back seat in a way
        // a softened rectangle does not.
        background = pill(if(primary) Palette.primary else pale); minHeight = dp(72)
        setPadding(dp(16),dp(12),dp(16),dp(12)); setOnClickListener { if (usable()) action() }
        wholeWords()
    }
    private fun action(text: String, primary: Boolean = false, fn: () -> Unit) {
        card.addView(button(text,primary,fn),LinearLayout.LayoutParams(-1,dp(74)).apply { topMargin = dp(10) })
    }
    private fun gap(parent: LinearLayout,n: Int) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    /**
     * The view is the screen.
     *
     * The picture is not in a panel; it is the ground everything else sits on, because the one
     * thing this app is for is getting a child to look at a view. The turn being taken arrives
     * as a sheet of paper laid over the lower right, and leaves again when the app is the one
     * talking. Along the bottom runs the drive itself: today's places as marks, the leg to the
     * next one filling as it comes up.
     *
     * Nothing here is a container for its own sake. The ground says what to look at, the sheet
     * says it is your turn, and the rule says where we are — so the composition has told the
     * child what kind of moment this is before they read a word of it.
     */
    private fun buildLayout() {
        val stage = FrameLayout(this).apply { setBackgroundColor(road) }

        scene = CuratorMediaPanel(this).also { stage.addView(it, FrameLayout.LayoutParams(-1,-1)) }
        scene.places={ catalog() }; scene.language=profile.language
        scene.onPhotoStage = { stage -> showPhotoWords(stage) }

        // The ground is a photograph as often as a flat colour, and the place's name has to be
        // readable on both. One gradient along the bottom does that without a box.
        scrim = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(70,4,32,46), Color.argb(170,4,32,46),
                    Color.argb(222,4,32,46), Color.argb(246,4,32,46)))
        }
        // Tall enough to reach the photograph's own caption. The caption used to carry a second
        // gradient of its own, which ended in a hard line across the picture — two washes doing
        // one job, and the seam between them read as a fault. One wash, long and slow.
        stage.addView(scrim, FrameLayout.LayoutParams(-1, dp(440), Gravity.BOTTOM))

        rule = RouteRule(this)
        stage.addView(rule, FrameLayout.LayoutParams(-1, dp(74), Gravity.BOTTOM))

        // Top left, and small. Every photograph in this app is of something tall with sky
        // around it, so that corner is empty in all of them; the picture the child is being
        // asked to find is in the middle, and the sheet is on the right.
        // Before the first place, and between places, the screen used to hold a cream circle
        // with a question mark on it. It told a child nothing and it could not move. 두리
        // stands there instead, breathing, so the screen is somebody waiting with you rather
        // than a picture of nothing in particular.
        waiting = CompanionView(this)
        stage.addView(waiting, FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER)
            // Centred on the part of the screen the sheet is not covering, and low enough
            // that the road still runs away behind it rather than being covered by it.
            .apply { marginEnd = sheetWidth() / 2; topMargin = dp(150) })

        sticker = TopicStickerView(this)
        stage.addView(sticker, FrameLayout.LayoutParams(dp(164), dp(150), Gravity.TOP or Gravity.START)
            .apply { leftMargin = dp(20); topMargin = dp(16) })

        // Which window to look out of, in the corner the photograph can spare.
        compass = WindowCompass(this).apply { visibility = View.GONE }
        stage.addView(compass, FrameLayout.LayoutParams(dp(132), dp(158), Gravity.TOP or Gravity.START)
            .apply { leftMargin = dp(20); topMargin = dp(16) })

        // The place's name is part of the picture, not a caption under it: set large, low, and
        // over the view itself.
        val nameBlock = column().apply { setPadding(dp(34),0,dp(34),dp(18)) }
        // The photograph's own words. They used to be painted into the picture, where the
        // screen's wash dimmed them to grey; here they sit in the same layer as the chips and
        // the name, so white reads as white and the credit line stays legible.
        photoYear = label("",Text.LABEL,cream,true).also { nameBlock.addView(it,LinearLayout.LayoutParams(-2,-2)) }
        photoLine = label("",Text.BODY,Color.WHITE).also { nameBlock.addView(it,LinearLayout.LayoutParams(-2,-2)) }
        photoCredit = label("",Text.CAPTION,onPhoto).also {
            nameBlock.addView(it,LinearLayout.LayoutParams(-2,-2).apply { topMargin = dp(2); bottomMargin = dp(12) })
        }
        val facts = row()
        subline = heroChip("경로에서 만나는 발견").also { facts.addView(it,LinearLayout.LayoutParams(-2,-2)) }
        meetingChip = heroChip("").apply { visibility = View.GONE }
            .also { facts.addView(it,LinearLayout.LayoutParams(-2,-2).apply { marginStart = dp(8) }) }
        nameBlock.addView(facts,LinearLayout.LayoutParams(-2,-2))
        gap(nameBlock,12)
        // Wrap, explicitly. A vertical LinearLayout re-measures a full-width child to the
        // width of its widest sibling, so the name would otherwise be cut to the chip above it.
        placeName = label("어떤 곳을 만나게 될까?",Text.DISPLAY,Color.WHITE,true).apply {
            maxWidth = resources.displayMetrics.widthPixels - sheetWidth() - dp(96)
        }
        nameBlock.addView(placeName,LinearLayout.LayoutParams(-2,-2))
        // The place's own words, under its name where they belong: a name, then what it is.
        // One chip each, because three hashtags run together into one long word otherwise.
        tagRow = row().also {
            nameBlock.addView(it,LinearLayout.LayoutParams(-2,-2).apply { topMargin = dp(12) })
        }
        mediaButton=button(say.replayFilm) { scene.replay() }.apply {
            textSize=sp(Text.LABEL); setTextColor(Color.WHITE); background = pill(onHero)
        }
        nameBlock.addView(mediaButton,LinearLayout.LayoutParams(-2,dp(54)).apply { topMargin=dp(14) })
        heroCaption = nameBlock
        stage.addView(nameBlock, FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START)
            .apply { bottomMargin = dp(74) })

        // The sheet. Paper laid on the view, not the other half of a split.
        card = column().apply { setPadding(dp(34),dp(30),dp(34),dp(30)) }
        val sheet = ScrollView(this).apply {
            background = rounded(Color.WHITE,Corner.PAPER); clipToOutline = true
            isFillViewport = true; isVerticalScrollBarEnabled = false
            // A long turn gets cut by the sheet's own edge, and a button sliced in half reads
            // as a fault. Fading the cut says what it is: there is more of this below.
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(28))
            elevation = dp(10).toFloat()
            addView(card,FrameLayout.LayoutParams(-1,-2))
        }
        cardPanel = sheet
        // Sized by what is on it. A sheet of paper is as big as what is written on it; a panel
        // that always runs floor to ceiling is a container, and the empty half of it says the
        // app expected more to say than it had. Tall turns fill the room left and scroll.
        stage.addView(sheet, FrameLayout.LayoutParams(sheetWidth(), -2, Gravity.END or Gravity.BOTTOM)
            .apply { topMargin = dp(64); bottomMargin = dp(88); marginEnd = dp(26) })

        // The header rides on the view as chips, since there is no bar for it to sit in.
        val header = row().apply { setPadding(dp(30),dp(22),dp(26),0) }
        val titles = column()
        tagline = heroChip(say.tagline).also { titles.addView(it,LinearLayout.LayoutParams(-2,-2)) }
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        badge = heroChip("시연 경로").also { header.addView(it,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(12) }) }
        header.addView(button("보호자") { parentMenu() }.apply {
            textSize=sp(Text.LABEL); setTextColor(Color.WHITE); background = pill(onHero)
        }, LinearLayout.LayoutParams(-2,dp(58)))
        stage.addView(header, FrameLayout.LayoutParams(-1,-2,Gravity.TOP))

        setContentView(stage)
        // The band exists before anything animates, so the first drawing drawn already clears it.
        stage.post { if (sheetOut) scene.coveredBottom = (heroCaption.height + dp(74)).toFloat() }
        scene.coveredRight = sheetWidth().toFloat()
    }

    /**
     * The three lines that belong to the photograph on screen, or none.
     *
     * A place with no freely licensed photograph keeps its drawing and these stay empty, which
     * is the same rule the picture itself follows: nothing is said about a photograph that is
     * not there.
     */
    private fun showPhotoWords(stage: PhotoStage?) {
        if (!::photoYear.isInitialized) return
        val language = profile.language
        photoYear.text = stage?.yearIn(language).orEmpty()
        photoLine.text = stage?.captionIn(language).orEmpty()
        photoCredit.text = stage?.credit.orEmpty()
        listOf(photoYear, photoLine, photoCredit).forEach {
            it.visibility = if (it.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    /** How much of the screen the question's sheet takes when it is out. */
    private fun sheetWidth() = (resources.displayMetrics.widthPixels * .42f).toInt()

    /**
     * The sheet comes and goes, and the picture takes the room it leaves.
     *
     * Every step before the explanation points at the window, so the question sits on a sheet
     * over the lower right while the view carries on behind it. The explanation is the one beat
     * that is about the picture itself, so the sheet slides off and a drawing re-centres and
     * grows into the whole screen. A photograph was already filling it and simply loses the
     * thing lying on top.
     */
    private fun setSheetOut(out: Boolean) {
        if (!::cardPanel.isInitialized || sheetOut == out) return
        sheetOut = out
        val span = if (ValueAnimator.areAnimatorsEnabled()) 420L else 0L
        val width = sheetWidth().toFloat()
        // The name and the route rule are the view's own chrome. The name goes with the sheet,
        // since the explanation is about the picture and names it aloud anyway; the rule stays,
        // because where we are on the drive is true in every beat.
        heroCaption.animate().alpha(if (out) 1f else 0f).setDuration(span).start()
        cardPanel.animate().alpha(if (out) 1f else 0f).translationX(if (out) 0f else width)
            .setDuration(span).setInterpolator(DecelerateInterpolator()).start()
        // A tap on the picture brings the sheet back, so nobody is left looking at a drawing
        // with the buttons gone. Held for this place's explanation only.
        if (out) { scene.setOnClickListener(null); scene.isClickable = false }
        else scene.setOnClickListener {
            sheetByHand = state.phase to state.currentId
            setSheetOut(true)
        }
        // The drawing's free area follows the sheet rather than snapping, so the picture is
        // seen to grow into the space instead of jumping once the sheet has gone.
        ValueAnimator.ofFloat(scene.coveredRight, if (out) width else 0f).apply {
            duration = span; interpolator = DecelerateInterpolator()
            addUpdateListener { scene.coveredRight = it.animatedValue as Float }
        }.start()
        val band = (if (out) heroCaption.height + dp(74) else dp(74)).toFloat()
        ValueAnimator.ofFloat(scene.coveredBottom, band).apply {
            duration = span; interpolator = DecelerateInterpolator()
            addUpdateListener { scene.coveredBottom = it.animatedValue as Float }
        }.start()
    }

    private fun render() {
        if (!::card.isInitialized || isDestroyed) return
        badge.text = if(live) "실제 내비 / ${if(playing) "음성 여행" else "잠시 쉼"}"
            else if(demo) "${say.routeNames[selectedRoute.id] ?: selectedRoute.title} / 시간 10배" else "정차 시연"
        val place = engine.place(state)
        placeName.text = place?.name ?: say.noPlaceYet
        tagline.visibility = if (state.phase==CuratorPhase.READY) View.VISIBLE else View.GONE
        // Which meeting this is, as it was when the meeting began. Reading the store here
        // meant that the moment the closing card wrote this visit down, the chip started
        // calling the same visit the next one.
        val met = place?.let { engine.roundOf(state, it) } ?: 0
        // Under the name: how far away it still is while it is still ahead, and once the car is
        // beside it, what it actually is. "이 지점 근처를 지났어요" used to sit here, which tells a
        // child looking straight at the place nothing at all.
        val eta = state.etaSeconds?.takeIf { it > 0 }?.let { say.minutesAway((it+59)/60) }
        subline.text = when {
            state.phase==CuratorPhase.FINISHED -> say.todaysRoute
            eta != null -> eta
            else -> say.routeDiscovery
        }
        // Not while the question is still open. 청진옥 asks what year it opened and #1937년개업
        // sat under the two answers; 숭례문 asks what the gate did at night and #밤에닫는문 said
        // so. The tags are what the place is, and the child earns them by getting there.
        val told = state.phase in setOf(CuratorPhase.EXPLAIN, CuratorPhase.FAMILY,
            CuratorPhase.PLAY, CuratorPhase.COMPARE, CuratorPhase.MEMORY)
        val tags = if (!told) emptyList()
            else place?.let { PlaceTags.of(it.id, prefs.profile.language, it.category) }.orEmpty()
        if (tags != lastTags) {
            lastTags = tags
            tagRow.removeAllViews()
            tags.forEach { tag ->
                tagRow.addView(heroChip(tag),LinearLayout.LayoutParams(-2,-2)
                    .apply { marginEnd = dp(8) })
            }
        }
        tagRow.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        // Which meeting this is explains, without a word of instruction, why the question
        // changed since last time. It is its own fact, so it gets its own chip.
        val showMeeting = met > 0 && state.phase !in setOf(CuratorPhase.FINISHED,CuratorPhase.READY)
        meetingChip.text = if (showMeeting) say.meeting(met+1) else ""
        meetingChip.visibility = if (showMeeting) View.VISIBLE else View.GONE
        showCompass(place)
        scene.visibility=if(restricted) View.INVISIBLE else View.VISIBLE
        scene.present(state, !restricted && foreground && dialog==null)
        // The explanation is the one beat that is about the picture rather than the window.
        showSticker()
        // The big one is for the waiting moments only; once a place is on screen the place is
        // what the child should be looking at.
        waiting.visibility =
            if (!restricted && engine.place(state) == null) View.VISIBLE else View.GONE
        val explaining = state.phase==CuratorPhase.EXPLAIN && !restricted && foreground
        setSheetOut(!explaining || sheetByHand == state.phase to state.currentId)
        showRoute()
        // A guardian's own place has no bundled film, so the button must not promise one.
        val hasFilm=engine.place(state)?.mediaKey?.isNotBlank()==true
        mediaButton.visibility=if(hasFilm && state.phase in setOf(CuratorPhase.EXPLAIN,CuratorPhase.PLAY,CuratorPhase.MEMORY)) View.VISIBLE else View.GONE
        val snapshot = listOf(state.phase,state.currentId,state.message,state.visits,restricted,connected,demo,live,playing,naviStatus,mic,state.routeValid,drive?.timedCount,drive?.nearbyCount)
        if(snapshot==lastCard) return
        lastCard=snapshot; val generation=++cardGeneration
        card.animate().cancel()
        if(foreground && !restricted && card.childCount>0 && ValueAnimator.areAnimatorsEnabled()) {
            card.animate().alpha(0f).setDuration(130).withEndAction {
                if(generation==cardGeneration && foreground) { populate(); card.animate().alpha(1f).setDuration(260).withEndAction(null).start() }
            }.start()
        } else { card.alpha=1f; populate() }
    }
    /**
     * The small picture for this turn.
     *
     * It belongs to the question being asked, so it comes and goes with the place and the
     * round rather than with every redraw. Only the two steps where something is actually
     * being asked get one: the explanation is about the picture that is already filling the
     * screen, and a sticker over that would be a second drawing arguing with the first.
     */
    /**
     * The arrow that points at a window, while the child is looking for the place.
     *
     * Only during the watching step: before it there is nothing out there yet, and after it the
     * place has gone past. It takes the top corner the question's sticker leaves empty, and
     * moves to the other corner when this place's photograph has its subject on that side —
     * the one thing it must never do is sit on top of the building the child is looking for.
     */
    private fun showCompass(place: CuratorPlace?) {
        if (!::compass.isInitialized) return
        val pointing = state.phase == CuratorPhase.WATCH && !restricted && place != null &&
            state.side != WindowSide.UNKNOWN
        if (!pointing) { compass.point(WindowSide.UNKNOWN, ""); return }
        val focus = PhotoStages.forRecognising(place?.id).firstOrNull()?.focusX ?: .5f
        val params = compass.layoutParams as FrameLayout.LayoutParams
        val wanted = if (focus < .42f) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.START
        if (params.gravity != wanted) {
            params.gravity = wanted
            // Clear of the sheet when it has to sit on the right.
            params.leftMargin = dp(20)
            params.rightMargin = sheetWidth() + dp(20)
            compass.layoutParams = params
        }
        compass.point(state.side, when (state.side) {
            WindowSide.LEFT -> say.windowLeft
            WindowSide.RIGHT -> say.windowRight
            else -> say.windowAhead
        })
    }

    private fun showSticker() {
        if (!::sticker.isInitialized) return
        val asking = state.phase in setOf(CuratorPhase.PREDICT, CuratorPhase.PLAY)
        val place = engine.place(state)
        if (!asking || place == null || restricted || !foreground) {
            sticker.showScrap(null, null); sticker.show(null); return
        }
        // Never the same picture twice.
        //
        // The clipping is cut from the very photograph that fills the screen while the child
        // is being asked to find the place, so showing it there put the same image on screen
        // in two sizes — which tells a child nothing they were not already looking at. It
        // belongs to the steps that are about a drawing: there it is the real thing beside the
        // diagram of it, which is a second picture rather than a copy of the first.
        val clipping = if (scene.showingPhoto) null else scrapFor(place.id)
        if (clipping != null) { sticker.showScrap(clipping, place.id); return }
        sticker.showScrap(null, null)
        // The favourite only counts while its line is on screen, which is the predict step.
        val spoken = if (state.phase == CuratorPhase.PREDICT) engine.favouriteFor(place) else null
        sticker.show(TopicPicture.pick(spoken, engine.prompt(state)?.field, place.visual))
    }

    /**
     * What 두리 is doing: listening to the child, saying something, or waiting.
     *
     * On a real drive the service does the speaking, so its flag counts as much as this
     * screen's own. Either way the child sees the same face move when words are coming out.
     */
    private fun showCompanion() {
        if (!::companion.isInitialized) return
        companion.setDoing(when {
            mic -> CompanionView.Doing.LISTENING
            speaking || drive?.speaking == true -> CompanionView.Doing.SPEAKING
            else -> CompanionView.Doing.RESTING
        })
    }

    /** Clippings are small and few, so they are kept once rather than decoded per turn. */
    private val clippings = mutableMapOf<String, android.graphics.Bitmap?>()

    private fun scrapFor(placeId: String): android.graphics.Bitmap? = clippings.getOrPut(placeId) {
        PlaceScrap.of(placeId)?.let { asset ->
            runCatching { assets.open(asset).use(android.graphics.BitmapFactory::decodeStream) }.getOrNull()
        }
    }

    /**
     * Today's drive along the bottom.
     *
     * A demo route knows its whole list of places, so the rule can show where we are among them.
     * A real journey does not: the SDK gives the next place and its remaining time and nothing
     * further, so the rule honestly shows only the leg we are on. Either way the filling part is
     * the same fact — how close the place now coming up is.
     */
    private fun showRoute() {
        val eta = state.etaSeconds
        // 300 seconds is where this app starts talking about a place, so it is where the leg
        // starts filling. Past the place it is full.
        val approach = when {
            state.currentId == null -> null
            eta == null -> 1f
            else -> (1f - eta / 300f).coerceIn(0f, 1f)
        }
        // Nothing to say before a journey starts, and nothing to say while the screen is
        // resting. The rule hides itself when it is left with neither fact.
        val quiet = restricted || state.phase == CuratorPhase.READY
        val ids = if (demo && !quiet) selectedRoute.stops(album.load().ownPlaceIds) else emptyList()
        val named = catalog()
        val stops = ids.mapNotNull { id -> named.firstOrNull { it.id == id }?.name }
        val passed = state.phase !in setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH)
        rule.show(if (stops.size == ids.size) stops else emptyList(),
            ids.indexOf(state.currentId), if (quiet) null else approach, passed)
    }

    private fun populate() {
        card.removeAllViews(); statusLabel=null
        if(!connected || restricted) {
            card.addView(label(if(!connected) "차량과 연결하고 있어요" else "지금은 쉬어가요",Text.DISPLAY,ink,true)); gap(card,20)
            card.addView(label(if(live) "화면은 쉬고, 시작한 음성 여행은 계속돼요." else "차가 멈추면 이야기를 이어가요.",Text.BODY,muted)); return
        }
        if(live && (state.phase in setOf(CuratorPhase.READY,CuratorPhase.WAITING) || !state.routeValid)) {
            // Four sentences used to stand here, two of them about how the app decides when
            // to speak. A six year old cannot choose anything from a paragraph, and nothing
            // here was theirs to decide anyway. One line about what is happening, one number
            // worth knowing, and the two things they can press.
            card.addView(label("길을 찾는 중이야",Text.TITLE,ink,true));gap(card,14)
            card.addView(label(naviStatus,Text.BODY,muted));gap(card,18)
            val ready = drive?.let { it.timedCount + it.nearbyCount } ?: 0
            if(ready > 0) {
                card.addView(label("곧 만날 장소 ${ready}곳",Text.BODY,accent,true));gap(card,16)
            }
            action(if(playing) say.pause else say.resume,true) { drive?.setPaused(!drive!!.paused) ?: startLive() }
            action("지금 바로 해 보기") { routeMenu() };return
        }
        if(state.phase==CuratorPhase.READY) {
            card.addView(label(say.readyTitle,Text.DISPLAY,ink,true)); gap(card,20)
            card.addView(label(say.readySubtitle,Text.BODY,muted)); gap(card,18)
            action(say.startLive,true) { startLive() }
            CuratorRoutes.routes.forEach { route ->
                action(say.routeNames[route.id] ?: route.title) { startDemo(route) }
            }; return
        }
        // The first line names what kind of turn this is. A child who has met this place before
        // should be able to tell at a glance that today is not the same question again.
        val round=engine.prompt(state)
        val askHeading=when(round?.kind) {
            RoundKind.DEEPER -> say.stepPredictDeeper
            RoundKind.QUIZ -> say.stepPredictQuiz
            RoundKind.HUNT -> say.stepPredictHunt
            else -> say.stepPredictImagine
        }
        val heading=when(state.phase) { CuratorPhase.PREDICT -> askHeading; CuratorPhase.WATCH -> say.stepWatch; CuratorPhase.EXPLAIN -> say.stepExplain; CuratorPhase.FAMILY -> say.stepFamily; CuratorPhase.PLAY -> say.stepPlayPrefix+(engine.place(state)?.activity?.role ?: ""); CuratorPhase.COMPARE -> say.stepPlayPrefix; CuratorPhase.MEMORY -> say.stepMemory; CuratorPhase.FINISHED -> say.stepFinished; else -> say.stepWaiting }
        // 두리 and the name of this step, side by side: the face belongs to the words, so it
        // sits with them rather than floating somewhere else on the screen.
        card.addView(row().apply {
            companion = CompanionView(this@MainActivity)
            addView(companion, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(12) })
            addView(stepHeader(heading))
        })
        gap(card,18)
        showCompanion()
        // The hook is the half of the question that makes a six year old look up, so it belongs
        // on screen as well as in the speaker. On a later meeting it says what changed instead.
        if(state.phase==CuratorPhase.PREDICT) {
            // The line about what this child loves comes first, the same way it is spoken.
            engine.place(state)?.let { engine.bridgeFor(it) }?.let {
                card.addView(label(it,Text.BODY,accent)); gap(card,10)
            }
            round?.lead?.takeIf { it.isNotBlank() }?.let {
                card.addView(label(it,Text.BODY,muted)); gap(card,12)
            }
        }
        val text=when(state.phase) {
            CuratorPhase.PREDICT -> engine.prompt(state)?.question ?: state.message
            CuratorPhase.PLAY -> engine.place(state)?.activity?.prompt ?: state.message
            CuratorPhase.EXPLAIN -> when(val visual = engine.place(state)?.visual) {
                // A guardian's place has no headline of ours to give it. Theirs is the only true one.
                CuratorVisual.FAMILY, null -> state.message
                else -> say.visualSummary[visual] ?: state.message
            }
            else -> state.message
        }
        card.addView(label(text,Text.TITLE,ink,true)); gap(card,16)
        when(state.phase) {
            CuratorPhase.PREDICT -> {
                engine.memory(state)?.guess?.let { guess ->
                    card.addView(label(say.youSaid+guess,Text.LABEL,accent)); gap(card,10)
                    // What the app said back. It is spoken, but a guardian glancing over should
                    // be able to see the exchange, and a child who missed it can be read it.
                    if(talking) { card.addView(label(state.message,Text.BODY,ink)); gap(card,12) }
                }
                if(talking) {
                    // Nothing to point at. Printing the two answers here would turn a question
                    // the child has to think about into one they only have to read.
                    card.addView(label(if(mic) say.listening else say.waitingForYou,Text.BODY,if(mic) accent else muted))
                    gap(card,16)
                    action(say.askAgain) { quiet(); voice.speak(state.message) { heardNothing() } }
                    action(say.useTheScreen) { showChoices = true; lastCard = null; render() }
                } else if (engine.memory(state)?.guess != null) {
                    // Already answered. A quiz owes the answer to the eyes as well as the ears,
                    // and then the turn is over: leaving the choices up asks the child to pick
                    // between two answers we have just finished telling them the answer to.
                    round?.reveal?.let { card.addView(label(it,Text.BODY,ink)) }
                } else {
                    engine.prompt(state)?.choices?.forEach { guess -> action(guess) { apply(engine.predict(state,guess)) } }
                    action(say.sayItMyself,true) { listen() }
                }
            }
            CuratorPhase.WATCH -> if(talking) {
                card.addView(label(if(mic) say.watchListening else say.watchWaiting,Text.BODY,if(mic) accent else muted)); gap(card,14)
                action(say.useTheScreen) { showChoices = true; lastCard = null; render() }
            } else { action(say.sawIt,true) { apply(engine.seen(state,true)) }; action(say.didNotSee) { apply(engine.seen(state,false)) } }
            CuratorPhase.EXPLAIN -> {
                action(say.myTurn) { quiet(); apply(advanceAfterNarration()) }
                action(say.readTheStory) {
                    show(AlertDialog.Builder(this).setTitle(engine.place(state)?.name).setMessage(state.message).setPositiveButton("닫기",null).create())
                }
            }
            CuratorPhase.FAMILY -> {
                card.addView(label(say.guardianNote,Text.CAPTION,muted)); gap(card,8)
                // Both answers go to the same place. The child is never graded on remembering.
                //
                // Which pair of answers, though, depends on whose memory this is. A story a
                // parent wrote down is not something the child can remember, so it is not
                // offered as a memory question.
                val written = album.load().wrote(state.currentId)
                action(if(written) say.heardIt else say.rememberIt,true) { quiet(); apply(engine.afterFamilyMemory(state)) }
                action(if(written) say.imagineIt else say.notSure) { quiet(); apply(engine.afterFamilyMemory(state)) }
            }
            CuratorPhase.PLAY -> {
                card.addView(label(say.pretendPlay,Text.CAPTION,muted)); gap(card,8)
                if(talking) {
                    card.addView(label(if(mic) say.playListening else say.playWaiting,Text.BODY,if(mic) accent else muted)); gap(card,14)
                    action(say.useTheScreen) { showChoices = true; lastCard = null; render() }
                } else {
                    engine.place(state)?.activity?.choices?.forEach { choice -> action(choice.label,true) { apply(engine.perform(state,choice.id)) } }
                    action(say.inMyOwnWords) { listen() }
                }
            }
            CuratorPhase.MEMORY -> { memoryCard(engine.memory(state)); action(say.keepWatching,true) { if(live) drive?.setPaused(false) else { playing=true;render();feedDemo() } } }
            CuratorPhase.FINISHED -> { state.visits.forEach { memoryCard(it) }; action("새 시연 여행",true) { startDemo() } }
            else -> { action(if(playing) say.pause else say.resume,true) { playing=!playing; render() } }
        }
        // Where this place is in its four beats. Nothing to press, just a place marker.
        val step = when(state.phase) {
            CuratorPhase.PREDICT -> 0; CuratorPhase.WATCH -> 1
            CuratorPhase.EXPLAIN, CuratorPhase.FAMILY -> 2; CuratorPhase.PLAY -> 3
            else -> -1
        }
        if(step >= 0) { gap(card,18); card.addView(stepDots(step),LinearLayout.LayoutParams(-1,-2)) }
        if(state.phase !in setOf(CuratorPhase.FINISHED,CuratorPhase.WAITING)) {
            gap(card,18); val small=row()
            small.addView(button(say.listenAgain) { if(live) drive?.repeat() else { quiet();voice.speak(state.message) } },LinearLayout.LayoutParams(0,dp(68),1f))
            small.addView(button(say.skip) { quiet(); apply(engine.skip(state)) },LinearLayout.LayoutParams(0,dp(68),1f).apply { marginStart=dp(10) }); card.addView(small)
        }
        gap(card,14); statusLabel=label(status,Text.CAPTION,muted).also { card.addView(it) }
    }
    private fun memoryCard(memory: PlaceMemory?) {
        memory ?: return
        val p=CuratorCatalog.places.firstOrNull { it.id==memory.placeId } ?: return
        card.addView(label(p.name,Text.LABEL,accent,true))
        // Only what the child said, in their words.
        //
        // This used to add a line of bookkeeping to every place: whether they had told us if
        // they saw it, that a game had been "left for later". That is a note to an adult,
        // written in the middle of a six year old's screen, and it was there for every place
        // whether anything had happened or not. A child who said nothing now reads nothing,
        // and the drive carries on to the next window.
        val said=listOfNotNull(memory.guess?.trim()?.ifEmpty { null },memory.activityLabel?.trim()?.ifEmpty { null })
        if(said.isNotEmpty()) { gap(card,8); card.addView(label(said.joinToString("\n"),Text.BODY,muted)) }
        // Seen or not, said without a sentence: a full mark for a place they found out of the
        // window, an outline for one they only heard about.
        gap(card,8); card.addView(seenMark(memory.seen)); gap(card,16)
    }
    private fun show(next: AlertDialog) {
        // A guardian-menu item opens its screen from a posted runnable. The list dialog's own
        // dismiss callback is queued after that runnable, so `dialog` still holds the closing
        // menu when the new screen asks to open. usable() would read that as "a dialog is
        // already up" and drop the screen without a word, which is what hid every guardian
        // screen behind the menu. Replace the open dialog here instead of refusing.
        if(!foreground || !connected || restricted) return
        // Drop the old listener first: once it runs it would clear the dialog we just stored.
        dialog?.let { previous -> previous.setOnDismissListener(null); previous.dismiss() }
        quiet();drive?.suppress(true);handler.removeCallbacks(roleIdle);scene.stop();dialog=next
        next.setOnDismissListener { if(dialog===next) { dialog=null;drive?.suppress(false);render();armRoleIdle() } }
        // Every panel on the screen behind this is rounded. A square sheet on top of them reads
        // as a system window that wandered in rather than part of the app.
        next.window?.setBackgroundDrawable(InsetDrawable(rounded(Color.WHITE,Corner.PAPER),dp(26),dp(18),dp(26),dp(18)))
        next.show()
    }
    private fun parentMenu() {
        val items=arrayOf(if(playing) "이야기 잠깐 멈추기" else "이야기 이어서 재생", "시점 이동해서 확인", "말 대신 입력", "가족이 함께 갔던 곳 기록", "오늘 이야기 마치기", "설명 출처", "지난 시연 기억", "실제 내비 음성 여행 시작", "오늘 길에서 들려줄 이야기",
            "뒷자리 말하기     ${if(prefs.backSeatVoice) "켜짐" else "꺼짐"}", "질문을 처음 만남으로 되돌리기",
            "아이 설정     ${profileSummary()}")
        show(AlertDialog.Builder(this).setTitle("보호자 / 정차 시연").setItems(items) { _,i -> menuAction {
            when(i) {
                0 -> { if(live) drive?.setPaused(!drive!!.paused) else if(demo) { playing=!playing;render() } }
                1 -> demoControls()
                2 -> textInput()
                3 -> familyMenu()
                4 -> { playing=false;if(live) drive?.end() else apply(engine.finish(state)) }
                5 -> sources()
                6 -> previousSummary()
                7 -> startLive()
                8 -> todaysPlan()
                9 -> backSeatSetting()
                10 -> resetRounds()
                11 -> childSetup()
            }
        } }.setNegativeButton("닫기",null).create())
    }
    /**
     * What this drive has time to tell, measured.
     *
     * The road is fixed, so its length is known, and each place's story can be timed from the
     * words it is made of. A guardian can see before setting off which stories the drive will
     * reach and which category each one will be told in — and, when the road is short, which
     * ones it will not reach rather than hearing three of them cut off.
     */
    private fun todaysPlan() {
        val route = CuratorRoutes.fixed
        val own = album.load().ownPlaceIds
        val drive = route.endSeconds(own)
        val all = RoutePlan.segments(catalog(), route.stops(own), profile.language)
        val plan = RoutePlan.fit(all, drive, profile.fields)
        val told = RoutePlan.totalSeconds(plan)
        val body = buildString {
            append("${route.subtitle}\n")
            append("길이 약 ${(drive + 59) / 60}분, 이야기가 약 ${(told + 59) / 60}분.\n\n")
            plan.forEachIndexed { index, segment ->
                append("${index + 1}. ${segment.placeName}  ·  ${FIELD_LABELS.getValue(segment.field)}")
                append("  ·  약 ${(segment.seconds + 59) / 60}분\n")
            }
            val missed = all.map { it.placeId }.distinct() - plan.map { it.placeId }.toSet()
            if (missed.isNotEmpty()) {
                append("\n이 길에서는 여기까지예요. 시작한 이야기는 끝까지 들려주고,\n")
                append("남는 곳은 다음에 같은 길을 지날 때 만나요.")
            } else {
                append("\n한 장소에 하나씩, 시작한 이야기는 끝까지 들려줘요.")
            }
            if (profile.fields.isEmpty()) {
                append("\n\n아이 설정에서 분야를 고르면 그 분야의 이야기를 먼저 골라요.")
            }
        }
        show(AlertDialog.Builder(this).setTitle("오늘 길에서 들려줄 이야기")
            .setMessage(body).setPositiveButton("닫기", null).create())
    }

    /** How a guardian may say when, in the language they are setting up in. */
    private fun whenChoices() = Narration.of(profile.language).whenChoices

    /** What is set right now, short enough to sit on a menu line. */
    private fun profileSummary(): String {
        val saved = prefs.profile
        val fields = saved.fields.joinToString(" ") { FIELD_LABELS.getValue(it) }
        return LANGUAGE_LABELS.getValue(saved.language) + "   " + fields.ifBlank { "관심사 없음" }
    }

    /**
     * What the guardian knows about their child that the app cannot work out.
     *
     * Interests choose which question a place asks, not how a place is explained. A place only
     * offers a field it honestly has a question in, so this narrows what the child is asked
     * rather than rewriting any place to match them.
     */
    private fun childSetup() {
        val saved = prefs.profile
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(10), dp(24), 0)
        }
        column.addView(label("아이가 쓰는 말", Text.LABEL, accent, true))
        val languages = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        ChildLanguage.entries.forEachIndexed { index, option ->
            languages.addView(RadioButton(this).apply {
                id = index + 1; text = LANGUAGE_LABELS.getValue(option); textSize = Text.ACTION
                isChecked = saved.language == option
            })
        }
        column.addView(languages)
        column.addView(label("질문을 고를 관심 분야", Text.LABEL, accent, true))
        column.addView(label("고른 분야에 질문이 있는 장소에서는 그 질문을 먼저 해요. 없는 곳은 그 장소의 질문을 그대로 해요.", Text.CAPTION, muted))
        // Which categories this road can actually answer. A guardian choosing one that no
        // place on the route has a question for would be choosing nothing, so it says so.
        val answerable = RoutePlan.segments(catalog(), CuratorRoutes.fixed.placeIds)
            .map { it.field }.toSet()
        val fields = InterestField.entries.map { field ->
            CheckBox(this).apply {
                text = FIELD_LABELS.getValue(field) +
                    if (field in answerable) "" else "     이 길에는 아직 없어요"
                textSize = Text.ACTION; isChecked = field in saved.fields
            }.also(column::addView)
        }
        column.addView(label("요즘 좋아하는 것 (최대 ${ChildProfile.MAX_FAVOURITES}개)", Text.LABEL, accent, true))
        column.addView(label("정말로 이어지는 장소에서만 한마디 덧붙여요. 억지로 엮지 않아요.", Text.CAPTION, muted))
        val favourites = FavouriteTopic.entries.map { topic ->
            CheckBox(this).apply { text = FAVOURITE_LABELS.getValue(topic); textSize = Text.ACTION; isChecked = topic in saved.favourites }
                .also(column::addView)
        }
        show(AlertDialog.Builder(this).setTitle("아이 설정").setView(ScrollView(this).apply { addView(column) })
            .setNegativeButton("닫기", null)
            .setPositiveButton("저장하기") { _, _ -> menuAction {
                val language = ChildLanguage.entries.getOrNull(languages.checkedRadioButtonId - 1) ?: saved.language
                prefs.profile = ChildProfile(
                    fields = InterestField.entries.filterIndexed { index, _ -> fields[index].isChecked }.toSet(),
                    favourites = FavouriteTopic.entries.filterIndexed { index, _ -> favourites[index].isChecked }
                        .take(ChildProfile.MAX_FAVOURITES).toSet(),
                    language = language,
                )
                // The speech engines are told their language once, at start-up, so a change
                // takes a fresh screen rather than a locale swapped under a half-spoken line.
                if (language != saved.language) { quiet(); recreate() }
                else { status = "아이 설정을 저장했어요"; lastCard = null; render() }
            } }.create())
    }

    /**
     * The back seat's microphone, and the guardian's decision about it.
     *
     * The child cannot reach the screen, so without this they cannot answer at all. That is a
     * real reason to open a microphone and not a good enough one to do it unasked, so it starts
     * off and this screen says plainly what turning it on means.
     */
    private fun backSeatSetting() {
        val on = prefs.backSeatVoice
        show(AlertDialog.Builder(this).setTitle("뒷자리에서 말로 답하기")
            .setMessage(if(on)
                "지금은 켜져 있어요. 질문을 읽어 준 뒤 한 번씩 마이크가 열려서, 아이가 화면에 손을 대지 않고 대답할 수 있어요.\n\n한 번의 질문에 한 번만 열리고, 대답하거나 잠시 조용하면 바로 닫혀요. 목소리는 차 안에서 처리하고 밖으로 보내지 않아요. 녹음해서 남기지도 않아요."
                else "아이는 화면 뒤에 앉아 있어서 버튼을 누를 수 없어요. 이걸 켜면 질문을 읽어 준 뒤 마이크가 한 번씩 열려서, 아이가 그냥 말하면 돼요.\n\n한 번의 질문에 한 번만 열리고, 대답하거나 잠시 조용하면 바로 닫혀요. 목소리는 차 안에서 처리하고 밖으로 보내지 않아요. 녹음해서 남기지도 않아요.")
            .setNegativeButton("닫기",null)
            .setPositiveButton(if(on) "끄기" else "켜기") { _,_ -> menuAction {
                prefs.backSeatVoice = !on
                status = if(prefs.backSeatVoice) "뒷자리에서 말로 답할 수 있어요" else "이제 화면으로만 답해요"
                if(!prefs.backSeatVoice && mic) quiet()
                render()
            } }.create())
    }

    /**
     * Back to first meetings. Offered because a family may want the hooks again for a younger
     * child, and because nothing the app counted about them should be beyond their reach.
     */
    private fun resetRounds() {
        show(AlertDialog.Builder(this).setTitle("질문을 처음 만남으로")
            .setMessage("장소마다 몇 번째 만남인지 세어 둔 것을 지워요. 다음 번에는 모든 장소가 처음 질문으로 다시 시작해요. 가족의 기억 기록은 그대로 남아요.")
            .setNegativeButton("닫기",null)
            .setPositiveButton("되돌리기") { _,_ -> menuAction {
                prefs.resetRounds(); status = "다음 여행에서는 처음 질문으로 만나요"; render()
            } }.create())
    }

    private fun routeMenu() {
        show(AlertDialog.Builder(this).setTitle("가는 길의 이야기를 골라요")
            .setItems(CuratorRoutes.routes.map { "${it.title}\n${it.subtitle}" }.toTypedArray()) { _,i -> menuAction { startDemo(CuratorRoutes.routes[i]) } }
            .setNegativeButton("닫기",null).create())
    }
    private fun demoControls() {
        if(!demo) { routeMenu(); return }
        val entries=selectedRoute.placeIds.flatMap { id ->
            val name=CuratorCatalog.places.first { it.id==id }.name
            listOf("$name 약 5분 전", "$name 가까이", "$name 통과")
        }
        show(AlertDialog.Builder(this).setTitle("시연 위치 신호").setItems(entries.toTypedArray()) { _,i -> menuAction {
            val index=i/3;val stage=i%3
            val chosen=selectedRoute.placeIds[index]
            // A jump is a fresh, isolated preview. Earlier unfinished scenes must not block it.
            quiet();state=engine.start();sequence=0L;playing=false
            val seconds=selectedRoute.passageAt(index)
            demoTime=seconds-300
            // The jump builds its own signal, so it has to carry the same window the drive
            // would have carried. Without it the guardian's preview was the one place in the
            // app that could not say which way to look.
            val window=selectedRoute.sides[chosen] ?: WindowSide.UNKNOWN
            apply(engine.tick(state,CuratorFix(selectedRoute.id,++sequence,listOf(PlaceApproach(chosen,300,side=window)))))
            if(stage>0) {demoTime=seconds+if(stage==1) -20 else 10;apply(engine.tick(state,CuratorFix(selectedRoute.id,++sequence,listOf(PlaceApproach(chosen,if(stage==1)20 else -10,stage==2,side=window)))))}
        } }.setNegativeButton("닫기",null).create())
    }
    /**
     * The guardian's own record of where the family has been. The app never fills this in from
     * location history; a place is listed here only because a guardian chose it. Everything
     * written here stays in this car.
     */
    private fun familyMenu() {
        playing=false
        val saved=album.load()
        val places=saved.ownCuratorPlaces()+CuratorCatalog.places
        val labels=(listOf("지금 여기를 기억해 두기")+places.map { place ->
            saved.of(place.id)?.let { "${place.name}     ${it.whenLabel}" } ?: place.name
        }).toTypedArray()
        show(AlertDialog.Builder(this).setTitle("가족이 함께 갔던 곳")
            .setItems(labels) { _,index -> menuAction {
                if(index==0) rememberHere() else familyEntry(places[index-1])
            } }
            .setNegativeButton("닫기",null).create())
    }
    /**
     * Saves where the car is standing as a place of the family's own. This is how a family gets
     * past the app's six places without the app inventing anything: the guardian is parked at a
     * place that matters to them, and every word of its story is theirs.
     *
     * The guardian menu only opens while the car is stopped, so this cannot be done on the move.
     */
    private fun rememberHere() {
        playing=false
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            show(AlertDialog.Builder(this).setTitle("위치 권한이 필요해요")
                .setMessage("지금 있는 곳을 저장하려면 위치 권한을 허용해 주세요. 저장한 위치는 차 안에만 남습니다.")
                .setPositiveButton("닫기",null).create());return
        }
        val here=currentPoint()
        if(here==null) {
            show(AlertDialog.Builder(this).setTitle("아직 위치를 못 찾았어요")
                .setMessage("차량 위치 설정을 켜고 잠시 뒤에 다시 눌러 주세요.")
                .setPositiveButton("닫기",null).create());return
        }
        val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),dp(24),0) }
        column.addView(label("지금 차가 서 있는 곳을 우리 가족의 장소로 저장해요. 위치는 차 안에만 남아요.",Text.CAPTION,muted))
        val name=EditText(this).apply {
            hint="이곳을 뭐라고 부를까요. 예: 할머니 집"
            textSize=Text.ACTION
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NAME))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(name)
        val choices=RadioGroup(this).apply { orientation=LinearLayout.VERTICAL }
        whenChoices().forEachIndexed { index,choice ->
            choices.addView(RadioButton(this).apply { id=index+1;text=choice;textSize=Text.ACTION;isChecked=index==0 })
        }
        column.addView(choices)
        val note=EditText(this).apply {
            hint="같이 무엇을 했는지 한 줄로 적어도 좋아요"
            textSize=Text.ACTION
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NOTE))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(note)
        show(AlertDialog.Builder(this).setTitle("지금 여기를 기억해 두기").setView(column)
            .setNegativeButton("닫기",null)
            .setPositiveButton("저장하기") { _,_ -> menuAction {
                val called=name.text.toString().trim()
                if(called.isEmpty()) { status="이곳을 부를 이름을 적어 주세요.";render();return@menuAction }
                val picked=whenChoices().getOrNull(choices.checkedRadioButtonId-1)
                    ?: whenChoices().first()
                album.rememberHere(called,here,picked,note.text.toString().trim().takeIf(String::isNotEmpty))
                status="${called}을 우리 가족의 장소로 저장했어요."
                render()
            } }.create())
    }
    /** Last known position only. This never starts its own location updates. */
    private fun currentPoint(): GeoPoint? = runCatching {
        val manager=getSystemService(android.location.LocationManager::class.java)
        if(!manager.isLocationEnabled) return null
        manager.getProviders(true)
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude,it.longitude) }
            ?.takeIf { it.valid() }
    }.getOrNull()

    private fun familyEntry(place: CuratorPlace) {
        playing=false
        val existing=album.load().of(place.id)
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(24),dp(12),dp(24),0)
        }
        column.addView(label("이 장소에서 아이에게 들려줄 이야기를 적어 주세요.",Text.LABEL,accent,true))
        column.addView(label("적으신 문장을 그대로 들려줘요. 앱이 고치거나 바꾸지 않아요.",Text.CAPTION,muted))
        column.addView(label("예) 엄마 아빠가 처음 만난 곳이야.\n예) 네가 다섯 살 때 여행 가기 전에 들렀던 가게야. 어찌나 울었는지.",Text.CAPTION,muted))
        // Built here, added below the story: the story is what this screen is for, and the
        // time is the fallback for a guardian who only wants to mark that they were here.
        val choices=RadioGroup(this).apply { orientation=LinearLayout.VERTICAL }
        whenChoices().forEachIndexed { index,choice ->
            choices.addView(RadioButton(this).apply {
                id=index+1; text=choice; textSize=Text.LABEL
                isChecked=existing?.whenLabel==choice || (existing==null && index==0)
            })
        }
        val note=EditText(this).apply {
            hint="여기에 이야기를 적어 주세요"
            textSize=Text.ACTION; setText(existing?.note.orEmpty())
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NOTE))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
            isSingleLine=false; minLines=2
        }
        column.addView(note)
        column.addView(label("언제 있었던 일인지 (이야기를 적으면 말하지 않아요)",Text.CAPTION,muted))
        column.addView(choices)
        val builder=AlertDialog.Builder(this).setTitle(place.name).setView(column)
            .setNegativeButton("닫기",null)
            .setPositiveButton("기록하기") { _,_ -> menuAction {
                val picked=whenChoices().getOrNull(choices.checkedRadioButtonId-1)
                    ?: whenChoices().first()
                album.remember(FamilyVisit(place.id,picked,note.text.toString().trim().takeIf(String::isNotEmpty)))
                status="${place.name}을 가족의 기억으로 적어 두었어요."; render()
            } }
        if(existing!=null) builder.setNeutralButton("기록 지우기") { _,_ -> menuAction {
            album.forget(place.id); status="${place.name}의 기록을 지웠어요."; render()
        } }
        show(builder.create())
    }
    private fun textInput() {
        playing=false
        val field=EditText(this).apply { hint="아이가 말할 법한 생각을 입력해 보세요"; textSize=Text.ACTION; filters=arrayOf(android.text.InputFilter.LengthFilter(160)); importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        show(AlertDialog.Builder(this).setTitle("말 대신 입력하기").setView(field).setNegativeButton("닫기",null)
            .setPositiveButton("전하기") { _,_ -> menuAction { accept(field.text.toString()) } }.create())
    }
    private fun sources() {
        val text=CuratorCatalog.places.joinToString("\n\n") { "${it.name}\n${it.sourceTitle}\n${it.sourceUrl}" }
        show(AlertDialog.Builder(this).setTitle("설명 출처와 현재 범위").setMessage("$text\n\n설명은 위 자료를 바탕으로 작성했어요. 그림과 무음 영상은 원리를 단순화해 자체 제작한 모형이며 실사나 정확한 구조 도면은 아니에요. 역할 놀이는 별도의 상상이에요. 시연 위치와 도착 시간은 가상 데이터이며 실제 시야를 감지하지 않아요. 실제 연결에서는 목적지와 경유지의 장소별 예상 시간으로 예고해요. 그 밖의 장소는 GPS로 근처에 왔을 때 소개하고 멀어지는 흐름을 확인해 해설해요. 전체 경로 좌표를 받는 기능은 현재 SDK에서 확인되지 않았어요. 서울 6곳의 해설을 지원하고 여의도는 넓은 지역이라 목적지나 경유지로 등록된 경우만 안내해요. 위치와 경로는 저장하거나 외부로 보내지 않아요.").setPositiveButton("닫기",null).create())
    }
    private fun saveSummary() {
        if(!demo || state.visits.isEmpty()) return
        val array=JSONArray()
        state.visits.forEach { memory ->
            val p=CuratorCatalog.places.firstOrNull { it.id==memory.placeId } ?: return@forEach
            array.put(JSONObject().put("id",p.id).put("guess",memory.guess?.let { if (it in p.guesses) it else "자유롭게 생각했어" } ?: "답하지 않았어")
                .put("seen",memory.seen.name).put("activity",memory.activityLabel ?: "이번에는 듣기만 했어"))
        }
        getSharedPreferences("route_curator_v1",MODE_PRIVATE).edit().putString("demo_summary",array.toString()).apply()
    }
    private fun previousSummary() {
        val raw=getSharedPreferences("route_curator_v1",MODE_PRIVATE).getString("demo_summary",null)
        val text=runCatching {
            val array=JSONArray(raw ?: "[]")
            (0 until minOf(array.length(),10)).mapNotNull { index ->
                val item=array.getJSONObject(index); val p=CuratorCatalog.places.firstOrNull { it.id==item.optString("id") } ?: return@mapNotNull null
                "${p.name}\n처음 생각: ${item.optString("guess")}\n${item.optString("activity", "이번에는 듣기만 했어")}"
            }.joinToString("\n\n")
        }.getOrDefault("").ifBlank { "아직 남긴 시연 기억이 없어요." }
        show(AlertDialog.Builder(this).setTitle("지난 시연에서 만난 곳").setMessage(text+"\n\n자유롭게 말한 문장과 음성은 저장하지 않아요.").setPositiveButton("닫기",null).create())
    }

    private companion object {
        val LANGUAGE_LABELS = mapOf(
            ChildLanguage.KOREAN to "한국어",
            ChildLanguage.ENGLISH to "English",
        )
        val FIELD_LABELS = mapOf(
            InterestField.SCIENCE to "과학",
            InterestField.HISTORY to "역사",
            InterestField.ART to "예술",
            InterestField.NATURE to "자연",
            // Named for what it actually holds on this road: a 1937 shop's soup, and what the
            // people shut outside a city gate did about dinner.
            InterestField.LIVING to "음식과 생활",
        )
        val FAVOURITE_LABELS = mapOf(
            FavouriteTopic.DINOSAUR to "공룡",
            FavouriteTopic.SPACE to "우주",
            FavouriteTopic.ANIMALS to "동물",
            FavouriteTopic.VEHICLES to "탈것",
            FavouriteTopic.DRAWING to "그림",
            FavouriteTopic.MUSIC to "음악",
            FavouriteTopic.ROBOTS to "로봇",
        )
    }
}
