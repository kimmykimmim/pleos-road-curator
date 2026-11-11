package dev.changbak.explorer

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.animation.ValueAnimator
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val album by lazy { FamilyAlbumStore(this) }
    private val engine = RouteCurator { CuratorCatalog.places + album.load().ownCuratorPlaces() }
    private var state = CuratorState()
    private lateinit var voice: VoiceGuide
    private var drive: CuratorDriveService? = null
    private var serviceBound = false
    private val driveObserver: () -> Unit = {
        drive?.let { service ->
            state=service.state;naviStatus=service.connectionStatus;playing=!service.paused
            if(foreground) render()
        }
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
            else { status="주변 장소를 찾으려면 정확한 위치 권한이 필요해요.";render() }
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
    private var status = "음성을 준비하고 있어요"
    private var naviStatus = "내비게이션 연결 전"
    private var live = false
    private var narrationGeneration = 0L
    private var cardGeneration = 0L
    private var dialog: AlertDialog? = null
    private var lastCard: List<Any?>? = null
    private lateinit var card: LinearLayout
    private lateinit var scene: CuratorMediaPanel
    private lateinit var mediaButton: Button
    private lateinit var badge: TextView
    private lateinit var placeName: TextView
    private lateinit var subline: TextView
    private var statusLabel: TextView? = null
    private val ink = Color.rgb(11,45,63)
    private val muted = Color.rgb(104,130,151)
    private val accent = Color.rgb(8,98,136)
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
        voice = VoiceGuide(this, onState = { value -> runOnUiThread {
            val wasListening=mic
            status = when(value) { "speaking" -> "이야기를 듣는 중"; "listening" -> "네 생각을 듣고 있어"; else -> "말하지 않아도 괜찮아" }
            if (::voice.isInitialized && !voice.enabled) mic = false
            if(wasListening && !mic) { drive?.suppress(false);armRoleIdle() }
            statusLabel?.text = status
        } }, onCommand = {}, onError = { value -> runOnUiThread { mic = false;drive?.suppress(false);status = value;statusLabel?.text = value } },
            onUtterance = { words -> runOnUiThread { if (mic && usable()) { quiet(); accept(words) } } })
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
        if (changed) { render(); if (state.phase in setOf(CuratorPhase.MEMORY, CuratorPhase.FINISHED)) saveSummary() }
        val text = turn.narration ?: return
        quiet(); val token = narrationGeneration
        // A narration that fails must still release the phase that is waiting for it.
        val release: () -> Unit = {
            if (token == narrationGeneration && usable() &&
                NarrationRecovery.shouldAdvance(state.phase, token, narrationGeneration, false, false)
            ) apply(advanceAfterNarration())
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
    private fun accept(text: String) {
        if (!usable()) return
        drive?.suppress(false)
        val compact = text.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        if (compact in setOf("쉬자","그만","그만할래","잠깐쉴래")) { playing = false; drive?.setPaused(true);quiet(); status = "쉬는 중이에요"; render(); return }
        if (compact in setOf("다음","넘어가자","건너뛸래")) { apply(engine.skip(state)); return }
        when (state.phase) {
            CuratorPhase.PREDICT -> apply(engine.predict(state, text))
            CuratorPhase.WATCH -> when {
                Regex("안보|못봤|못보|안봤").containsMatchIn(compact) -> apply(engine.seen(state,false))
                Regex("보여|봤어|보인다|찾았").containsMatchIn(compact) -> apply(engine.seen(state,true))
                else -> { status = "보이면 ‘봤어’, 안 보이면 ‘안 보여’라고 알려줘"; statusLabel?.text = status }
            }
            CuratorPhase.PLAY -> {
                apply(engine.respondToPlay(state,text))
            }

            else -> { status = "지금은 편하게 듣고 있어도 좋아"; statusLabel?.text = status }
        }
    }
    private fun listen() {
        if (!usable()) return
        if (mic) { quiet();drive?.suppress(false);render(); return }
        quiet(); playing = false
        show(AlertDialog.Builder(this).setTitle("네 생각을 들려줘")
            .setMessage("확인을 누르면 이번 답변을 듣기 시작해요. 음성은 기기 안에서 처리하고 외부 AI로 보내지 않아요.")
            .setNegativeButton("나중에", null).setPositiveButton("이야기할게") { _, _ -> handler.post {
                if (usable()) { drive?.suppress(true);mic = voice.enableCommands(); status = if (mic) "네 생각을 듣고 있어" else "지금은 화면으로 골라줘";if(!mic) drive?.suppress(false);render() }
            } }.create())
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
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(28).toFloat() }
    private fun label(text: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp(size); setTextColor(color); includeFontPadding = false; setLineSpacing(dp(5).toFloat(),1f)
        if (bold) typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = sp(24f); isAllCaps = false; setTextColor(if (primary) Color.WHITE else ink)
        background = rounded(if(primary) accent else Color.rgb(231,241,250)); minHeight = dp(72)
        setPadding(dp(16),dp(12),dp(16),dp(12)); setOnClickListener { if (usable()) action() }
    }
    private fun action(text: String, primary: Boolean = false, fn: () -> Unit) {
        card.addView(button(text,primary,fn),LinearLayout.LayoutParams(-1,dp(74)).apply { topMargin = dp(10) })
    }
    private fun gap(parent: LinearLayout,n: Int) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    private fun buildLayout() {
        val root = column().apply { setBackgroundColor(Color.rgb(242,247,251)); setPadding(dp(30),dp(24),dp(30),dp(24)) }
        val header = row(); val titles = column()
        titles.addView(label("가는 길이 이야기가 되는 시간",17f,muted)); gap(titles,6)
        titles.addView(label("길 위의 큐레이터",29f,ink,true))
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        badge = label("시연 경로",18f,accent,true).also { header.addView(it,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(22) }) }
        header.addView(button("보호자") { parentMenu() },LinearLayout.LayoutParams(-2,dp(68)))
        root.addView(header); gap(root,24)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = row()
        val left = column().apply { background = rounded(Color.rgb(223,238,249)); setPadding(dp(24),dp(24),dp(24),dp(28)); gravity=Gravity.CENTER }
        subline = label("경로에서 만나는 발견",20f,accent,true).also { left.addView(it) }
        // The drawing takes the leftover height instead of a fixed box, so the panel is full
        // on a tall screen and still has room to breathe on a short one.
        scene = CuratorMediaPanel(this).also {
            left.addView(it,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14);bottomMargin=dp(16) })
        }
        placeName = label("어떤 곳을 만나게 될까?",31f,ink,true).also { left.addView(it) }
        scene.places={ CuratorCatalog.places+album.load().ownCuratorPlaces() }
        mediaButton=button("짧은 원리 영상 다시 보기") { scene.replay() }.apply { textSize=sp(18f) }
        left.addView(mediaButton,LinearLayout.LayoutParams(-1,dp(58)).apply { topMargin=dp(12) })
        // The scene already draws the four step dots with their names, so the text version of
        // the same list was saying it twice.
        body.addView(left,LinearLayout.LayoutParams(0,-1,.46f).apply { marginEnd=dp(22) })
        // Centred, so a short turn sits in the middle of the card rather than hugging the top.
        card = column().apply {
            background = rounded(Color.WHITE); setPadding(dp(34),dp(30),dp(34),dp(30))
            gravity = Gravity.CENTER_VERTICAL
        }
        body.addView(card,LinearLayout.LayoutParams(0,-1,.54f)); scroll.addView(body,
            LinearLayout.LayoutParams(-1,-1))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun render() {
        if (!::card.isInitialized || isDestroyed) return
        badge.text = if(live) "실제 내비 / ${if(playing) "음성 여행" else "잠시 쉼"}" else if(demo) "${selectedRoute.title} / 시간 10배" else "정차 시연"
        val place = engine.place(state)
        placeName.text = place?.name ?: "길에서 만날 이야기"
        subline.text = if (state.phase==CuratorPhase.FINISHED) "오늘 지나온 발견" else state.etaSeconds?.let { if(it>0) "이 지점까지 약 ${(it+59)/60}분" else "이 지점 근처를 지났어요" } ?: "경로에서 만나는 발견"
        scene.visibility=if(restricted) View.INVISIBLE else View.VISIBLE
        scene.present(state, !restricted && foreground && dialog==null)
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
    private fun populate() {
        card.removeAllViews(); statusLabel=null
        if(!connected || restricted) {
            card.addView(label(if(!connected) "차량과 연결하고 있어요" else "지금은 쉬어가요",36f,ink,true)); gap(card,22)
            card.addView(label(if(live) "화면은 쉬고, 시작한 음성 여행은 계속돼요." else "차가 멈추면 이야기를 이어가요.",25f,muted)); return
        }
        if(live && (state.phase in setOf(CuratorPhase.READY,CuratorPhase.WAITING) || !state.routeValid)) {
            card.addView(label("가는 길의 이야기를 찾고 있어요",30f,ink,true));gap(card,20)
            card.addView(label(naviStatus,24f,muted));gap(card,16)
            card.addView(label("등록된 목적지와 경유지는 약 5분 전, 그 밖의 장소는 가까워졌을 때 소개해요.",23f,muted))
            gap(card,12);card.addView(label("현재 서울 6곳 / 위치로 찾는 장소 5곳",18f,muted))
            drive?.let { card.addView(label("예고 가능한 장소 ${it.timedCount}곳 / 가까운 장소 ${it.nearbyCount}곳",19f,accent)) }
            action(if(playing) "음성 여행 잠시 쉬기" else "음성 여행 다시 연결",true) { drive?.setPaused(!drive!!.paused) ?: startLive() }
            action("시연으로 살펴보기") { routeMenu() };return
        }
        if(state.phase==CuratorPhase.READY) {
            card.addView(label("지나칠 뻔한 곳에\n어떤 이야기가 있을까?",37f,ink,true)); gap(card,24)
            card.addView(label("약 5분 전 질문을 듣고,\n가까이 가면 직접 살펴봐요.",25f,muted)); gap(card,18)
            action("실제 내비로 음성 여행 시작",true) { startLive() }
            action("한강의 과학 여행") { startDemo(CuratorRoutes.routes[0]) }
            action("도심의 오래된 이야기") { startDemo(CuratorRoutes.routes[1]) }; return
        }
        val heading=when(state.phase) { CuratorPhase.PREDICT -> "01  먼저 상상해 보기"; CuratorPhase.WATCH -> "02  창밖에서 만나기"; CuratorPhase.EXPLAIN -> "03  장소의 이야기"; CuratorPhase.FAMILY -> "우리 가족의 기억"; CuratorPhase.PLAY -> "04  오늘의 ${engine.place(state)?.activity?.role ?: "상상 놀이"}"; CuratorPhase.COMPARE -> "04  상상 놀이"; CuratorPhase.MEMORY -> "오늘의 발견"; CuratorPhase.FINISHED -> "오늘 만난 이야기"; else -> "다음 이야기를 기다려요" }
        card.addView(label(heading,21f,accent,true)); gap(card,22)
        val text=when(state.phase) {
            CuratorPhase.PREDICT -> engine.place(state)?.question ?: state.message
            CuratorPhase.PLAY -> engine.place(state)?.activity?.prompt ?: state.message
            CuratorPhase.EXPLAIN -> when(engine.place(state)?.visual) {
                CuratorVisual.TOWER -> "몸통과 바깥 기둥이 힘을 나눠요."
                CuratorVisual.GLASS -> "뼈대와 유리벽은 역할이 달라요."
                CuratorVisual.ISLAND -> "모래섬 위에 도시가 자랐어요."
                CuratorVisual.RECIPE -> "가게의 이야기가 다음 사람에게 이어져요."
                // A guardian's place has no headline of ours to give it. Theirs is the only true one.
                CuratorVisual.FAMILY -> state.message
                else -> "문을 지나면 어떤 세상이 펼쳐질까요?"
            }
            else -> state.message
        }
        card.addView(label(text,if(state.phase==CuratorPhase.EXPLAIN) 28f else 33f,ink,true)); gap(card,18)
        when(state.phase) {
            CuratorPhase.PREDICT -> {
                engine.memory(state)?.guess?.let { card.addView(label("네 생각: $it",22f,accent)); gap(card,12) }
                engine.place(state)?.guesses?.forEach { guess -> action(guess) { apply(engine.predict(state,guess)) } }
                action("내 말로 이야기하기",true) { listen() }
            }
            CuratorPhase.WATCH -> { action("봤어!",true) { apply(engine.seen(state,true)) }; action("안 보여") { apply(engine.seen(state,false)) } }
            CuratorPhase.EXPLAIN -> {
                action("이제 내가 해 볼래!") { quiet(); apply(advanceAfterNarration()) }
                action("해설을 글로 보기") {
                    show(AlertDialog.Builder(this).setTitle(engine.place(state)?.name).setMessage(state.message).setPositiveButton("닫기",null).create())
                }
            }
            CuratorPhase.FAMILY -> {
                card.addView(label("보호자가 적어 둔 기억이에요",19f,muted)); gap(card,8)
                // Both answers go to the same place. The child is never graded on remembering.
                action("기억나!",true) { quiet(); apply(engine.afterFamilyMemory(state)) }
                action("잘 모르겠어") { quiet(); apply(engine.afterFamilyMemory(state)) }
            }
            CuratorPhase.PLAY -> {
                card.addView(label("여기부터는 우리의 상상 놀이",19f,muted)); gap(card,8)
                engine.place(state)?.activity?.choices?.forEach { choice -> action(choice.label,true) { apply(engine.perform(state,choice.id)) } }
                action("내 말로 바꿔 볼래") { listen() }
            }
            CuratorPhase.MEMORY -> { memoryCard(engine.memory(state)); action("계속 창밖 보기",true) { if(live) drive?.setPaused(false) else { playing=true;render();feedDemo() } } }
            CuratorPhase.FINISHED -> { state.visits.forEach { memoryCard(it) }; action("새 시연 여행",true) { startDemo() } }
            else -> { action(if(playing) "잠깐 쉬기" else "이어서 가기",true) { playing=!playing; render() } }
        }
        if(state.phase !in setOf(CuratorPhase.FINISHED,CuratorPhase.WAITING)) {
            gap(card,18); val small=row()
            small.addView(button("다시 듣기") { if(live) drive?.repeat() else { quiet();voice.speak(state.message) } },LinearLayout.LayoutParams(0,dp(68),1f))
            small.addView(button("넘어갈래") { quiet(); apply(engine.skip(state)) },LinearLayout.LayoutParams(0,dp(68),1f).apply { marginStart=dp(10) }); card.addView(small)
        }
        gap(card,18); statusLabel=label(status,18f,muted).also { card.addView(it) }
    }
    private fun memoryCard(memory: PlaceMemory?) {
        memory ?: return
        val p=CuratorCatalog.places.firstOrNull { it.id==memory.placeId } ?: return
        card.addView(label(p.name,25f,accent,true)); gap(card,8)
        val guess=memory.guess?.let { "처음 생각: $it\n" } ?: ""
        val observed=when(memory.seen) { SeenStatus.SEEN -> "직접 봤어"; SeenStatus.NOT_SEEN -> "보이지 않아 이야기로 만났어"; else -> "봤는지는 아직 말하지 않았어" }
        card.addView(label(guess+observed+"\n"+(memory.activityLabel ?: "다음에 해 볼 놀이로 남겼어"),21f,muted)); gap(card,18)
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
        next.setOnDismissListener { if(dialog===next) { dialog=null;drive?.suppress(false);render();armRoleIdle() } }; next.show()
    }
    private fun parentMenu() {
        val items=arrayOf(if(playing) "이야기 잠깐 멈추기" else "이야기 이어서 재생", "시점 이동해서 확인", "말 대신 입력", "가족이 함께 갔던 곳 기록", "오늘 이야기 마치기", "설명 출처", "지난 시연 기억", "실제 내비 음성 여행 시작", "다른 시연 경로 선택")
        show(AlertDialog.Builder(this).setTitle("보호자 / 정차 시연").setItems(items) { _,i -> handler.post {
            when(i) {
                0 -> { if(live) drive?.setPaused(!drive!!.paused) else if(demo) { playing=!playing;render() } }
                1 -> demoControls()
                2 -> textInput()
                3 -> familyMenu()
                4 -> { playing=false;if(live) drive?.end() else apply(engine.finish(state)) }
                5 -> sources()
                6 -> previousSummary()
                7 -> startLive()
                8 -> routeMenu()
            }
        } }.setNegativeButton("닫기",null).create())
    }
    private fun routeMenu() {
        show(AlertDialog.Builder(this).setTitle("가는 길의 이야기를 골라요")
            .setItems(CuratorRoutes.routes.map { "${it.title}\n${it.subtitle}" }.toTypedArray()) { _,i -> handler.post { startDemo(CuratorRoutes.routes[i]) } }
            .setNegativeButton("닫기",null).create())
    }
    private fun demoControls() {
        if(!demo) { routeMenu(); return }
        val entries=selectedRoute.placeIds.flatMap { id ->
            val name=CuratorCatalog.places.first { it.id==id }.name
            listOf("$name 약 5분 전", "$name 가까이", "$name 통과")
        }
        show(AlertDialog.Builder(this).setTitle("시연 위치 신호").setItems(entries.toTypedArray()) { _,i -> handler.post {
            val index=i/3;val stage=i%3
            val chosen=selectedRoute.placeIds[index]
            // A jump is a fresh, isolated preview. Earlier unfinished scenes must not block it.
            quiet();state=engine.start();sequence=0L;playing=false
            val seconds=selectedRoute.passageAt(index)
            demoTime=seconds-300
            apply(engine.tick(state,CuratorFix(selectedRoute.id,++sequence,listOf(PlaceApproach(chosen,300)))))
            if(stage>0) {demoTime=seconds+if(stage==1) -20 else 10;apply(engine.tick(state,CuratorFix(selectedRoute.id,++sequence,listOf(PlaceApproach(chosen,if(stage==1)20 else -10,stage==2)))))}
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
            saved.of(place.id)?.let { "${place.name}  ·  ${it.whenLabel}" } ?: place.name
        }).toTypedArray()
        show(AlertDialog.Builder(this).setTitle("가족이 함께 갔던 곳")
            .setItems(labels) { _,index -> handler.post {
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
        column.addView(label("지금 차가 서 있는 곳을 우리 가족의 장소로 저장해요. 위치는 차 안에만 남아요.",19f,muted))
        val name=EditText(this).apply {
            hint="이곳을 뭐라고 부를까요. 예: 할머니 집"
            textSize=21f
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NAME))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(name)
        val choices=RadioGroup(this).apply { orientation=LinearLayout.VERTICAL }
        FamilyVisit.WHEN_CHOICES.forEachIndexed { index,choice ->
            choices.addView(RadioButton(this).apply { id=index+1;text=choice;textSize=21f;isChecked=index==0 })
        }
        column.addView(choices)
        val note=EditText(this).apply {
            hint="같이 무엇을 했는지 한 줄로 적어도 좋아요"
            textSize=21f
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NOTE))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(note)
        show(AlertDialog.Builder(this).setTitle("지금 여기를 기억해 두기").setView(column)
            .setNegativeButton("닫기",null)
            .setPositiveButton("저장하기") { _,_ -> handler.post {
                val called=name.text.toString().trim()
                if(called.isEmpty()) { status="이곳을 부를 이름을 적어 주세요.";render();return@post }
                val picked=FamilyVisit.WHEN_CHOICES.getOrNull(choices.checkedRadioButtonId-1)
                    ?: FamilyVisit.WHEN_CHOICES.first()
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
        column.addView(label("언제 갔는지 골라 주세요. 아이에게 그대로 말해 줄 표현이에요.",19f,muted))
        val choices=RadioGroup(this).apply { orientation=LinearLayout.VERTICAL }
        FamilyVisit.WHEN_CHOICES.forEachIndexed { index,choice ->
            choices.addView(RadioButton(this).apply {
                id=index+1; text=choice; textSize=21f
                isChecked=existing?.whenLabel==choice || (existing==null && index==0)
            })
        }
        column.addView(choices)
        val note=EditText(this).apply {
            hint="같이 무엇을 했는지 한 줄로 적어도 좋아요"
            textSize=21f; setText(existing?.note.orEmpty())
            filters=arrayOf(android.text.InputFilter.LengthFilter(FamilyVisit.MAX_NOTE))
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(note)
        val builder=AlertDialog.Builder(this).setTitle(place.name).setView(column)
            .setNegativeButton("닫기",null)
            .setPositiveButton("기록하기") { _,_ -> handler.post {
                val picked=FamilyVisit.WHEN_CHOICES.getOrNull(choices.checkedRadioButtonId-1)
                    ?: FamilyVisit.WHEN_CHOICES.first()
                album.remember(FamilyVisit(place.id,picked,note.text.toString().trim().takeIf(String::isNotEmpty)))
                status="${place.name}을 가족의 기억으로 적어 두었어요."; render()
            } }
        if(existing!=null) builder.setNeutralButton("기록 지우기") { _,_ -> handler.post {
            album.forget(place.id); status="${place.name}의 기록을 지웠어요."; render()
        } }
        show(builder.create())
    }
    private fun textInput() {
        playing=false
        val field=EditText(this).apply { hint="아이가 말할 법한 생각을 입력해 보세요"; textSize=23f; filters=arrayOf(android.text.InputFilter.LengthFilter(160)); importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        show(AlertDialog.Builder(this).setTitle("말 대신 입력하기").setView(field).setNegativeButton("닫기",null)
            .setPositiveButton("전하기") { _,_ -> handler.post { accept(field.text.toString()) } }.create())
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
}
