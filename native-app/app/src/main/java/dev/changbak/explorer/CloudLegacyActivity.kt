package dev.changbak.explorer

import android.app.Activity
import android.app.AlertDialog
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
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import dev.changbak.explorer.audio.VoiceGuide
import dev.changbak.explorer.domain.*
import dev.changbak.explorer.network.*

/** Pleos owns the driving view and climate bar. This Activity occupies only the app area. */
class CloudLegacyActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: CloudWorldStore
    private lateinit var state: TravelState
    private lateinit var voice: VoiceGuide
    // Everyday conversation is session-only: destinations and moods never enter preferences.
    private var companion = CompanionState()
    private val chatClient = FriendChatClient()
    private val aiGate = AsyncReplyGate()
    private val aiHistory = ArrayList<FriendChatMessage>()
    private var aiEnabled = false
    private var adultDemoConsent = false
    private var aiBusy = false
    private var userListeningIntent = false
    private var replyFromAi = false
    private var sceneView: LandscapeFriend3DView? = null
    private var journeyView: JourneySceneView? = null
    private var vehicleStatus = "차량 이동 연결 전"
    private var demoOriginal: TravelState? = null
    private var lastVehicleRender = 0L
    private val vehicleJourney = VehicleJourneySource(onDistance = { meters -> runOnUiThread {
        if (!foreground || demoOriginal != null || !::state.isInitialized ||
            state.phase == CloudPhase.WELCOME || state.phase == CloudPhase.KEEPSAKE) return@runOnUiThread
        val world = state.cloudWorld ?: return@runOnUiThread
        val next = SkyJourneyEngine.advance(world.journey, meters, JourneySource.VEHICLE)
        state = state.copy(cloudWorld = world.copy(journey = next))
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastVehicleRender >= 1000L) {
            lastVehicleRender = now
            sceneView?.updateState(state, !restricted)
            journeyView?.present(next, !restricted)
            if (now % 5000L < 1100L) saveWorld()
        }
    } }, onStatus = { status -> runOnUiThread { vehicleStatus = status } })
    private var layoutWide: Boolean? = null
    private var storyCard: LinearLayout? = null
    private var skyPanel: LinearLayout? = null
    private var brandMode: TextView? = null
    private var skyHeading: TextView? = null
    private var skyName: TextView? = null
    private var skyCaption: TextView? = null
    private var finishButton: Button? = null
    private var rememberButton: Button? = null
    private var lastCardSnapshot: List<Any?>? = null
    private var cardGeneration = 0L
    private var speechState = "idle"
    private var sceneResting = false
    private val undo = ArrayDeque<TravelState>()
    private var foreground = false
    private var armed = false
    private var voiceConsent = false
    private var carReady = false
    private var restricted = true
    private var car: Car? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var reply = ""
    private var voiceStatus = "마이크는 눌렀을 때 켜져요"
    private var statusView: TextView? = null
    private var micButton: Button? = null
    private var dialog: AlertDialog? = null
    private val blue = Color.rgb(37, 105, 235)
    private val ink = Color.rgb(33, 53, 79)
    private val muted = Color.rgb(103, 124, 150)
    private val pageColor = Color.rgb(246, 249, 254)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CloudWorldStore(this)
        state = store.restore()
        voice = VoiceGuide(this, onState = { label -> runOnUiThread {
            speechState = label
            voiceStatus = if (aiBusy) "AI 답변을 기다리고 있어요" else when (label) {
                "speaking" -> "친구의 이야기를 듣는 중"
                "listening" -> "듣고 있어요"
                "starting" -> "소리를 준비하고 있어요"
                "quiet" -> "다시 말하고 싶으면 마이크를 눌러요"
                else -> if (state.phase == CloudPhase.KEEPSAKE) "궁금한 기억을 물어봐도 좋아요" else "천천히, 창밖을 살펴봐요"
            }
            if (::voice.isInitialized && !voice.enabled) {
                armed = false
                if (!aiBusy) userListeningIntent = false
            }
            statusView?.text = voiceStatus
            updateMicLabel()
        } }, onCommand = {}, onError = { message -> runOnUiThread {
            voiceStatus = message
            if (::voice.isInitialized && !voice.enabled) {
                armed = false
                if (!aiBusy) userListeningIntent = false
            }
            statusView?.text = message
            updateMicLabel()
        } }, onUtterance = { words -> runOnUiThread {
            if (armed && voiceConsent && isConversable() && dialog == null) acceptWords(words)
        } })
        render()
        connectCar()
    }

    private fun connectCar() {
        try {
            car = Car.createCar(this, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready -> runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                carReady = ready
                if (ready) try {
                    restrictions = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    restrictions?.registerListener(::updateRestrictions)
                    restrictions?.currentCarUxRestrictions?.let(::updateRestrictions)
                    if (foreground && checkSelfPermission("android.car.permission.CAR_SPEED") == android.content.pm.PackageManager.PERMISSION_GRANTED)
                        vehicleJourney.connect(connected)
                } catch (error: Exception) {
                    carReady = false; restricted = true; backgroundPause()
                    Log.w("LandscapeFriends", "Vehicle connection unavailable", error)
                } else { vehicleJourney.close(); restricted = true; backgroundPause() }
                render()
            } }
        } catch (error: Exception) {
            carReady = false; restricted = true
            Log.w("LandscapeFriends", "Vehicle API unavailable", error)
            render()
        }
    }

    private fun updateRestrictions(value: CarUxRestrictions) {
        carReady = true
        restricted = value.isRequiresDistractionOptimization
        if (restricted) { dialog?.dismiss(); backgroundPause() }
        else if (state.phase == CloudPhase.WELCOME) sceneResting = false
        render()
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if (carReady && checkSelfPermission("android.car.permission.CAR_SPEED") == android.content.pm.PackageManager.PERMISSION_GRANTED) car?.let(vehicleJourney::connect)
        render()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 81 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            car?.let(vehicleJourney::connect)
        else if (requestCode == 81) vehicleStatus = "차량 이동 연결을 허용하지 않았어요"
    }
    override fun onPause() {
        foreground = false; voiceConsent = false
        vehicleJourney.close()
        cardGeneration++; storyCard?.animate()?.cancel(); lastCardSnapshot = null
        aiHistory.clear()
        dialog?.dismiss(); backgroundPause()
        super.onPause()
    }
    override fun onDestroy() {
        cardGeneration++; storyCard?.animate()?.cancel()
        handler.removeCallbacksAndMessages(null)
        invalidateAi()
        chatClient.release()
        sceneView?.releaseScene(); sceneView = null
        vehicleJourney.close()
        voice.release()
        runCatching { restrictions?.unregisterListener(); car?.disconnect() }
        super.onDestroy()
    }
    private fun backgroundPause() {
        sceneResting = true
        sceneView?.pauseScene()
        quiet()
        companion = companion.copy(pending = TalkTopic.NONE)
        if (::state.isInitialized) {
            state = state.copy(
                phase = if (state.phase == CloudPhase.WATCHING) CloudPhase.RESTING else state.phase,
                pending = FriendChoice.NONE,
                cloudWorld = state.cloudWorld?.copy(draft = null),
            )
            if (::store.isInitialized) saveWorld()
            sceneView?.updateState(state, animate = false)
        }
    }
    private fun isConversable() = foreground && carReady && !restricted &&
        (state.phase == CloudPhase.WATCHING || state.phase == CloudPhase.KEEPSAKE)
    private fun invalidateAi(clearHistory: Boolean = false) {
        aiGate.cancel()
        chatClient.cancel()
        aiBusy = false
        if (clearHistory) aiHistory.clear()
    }
    private fun quiet() {
        userListeningIntent = false
        invalidateAi()
        armed = false
        if (::voice.isInitialized) voice.suspend()
    }
    private fun updateMicLabel() {
        micButton?.text = micLabel()
        updateSceneExpression()
    }
    private fun micLabel() = if (aiBusy) "답변 기다리기 취소" else if (armed) "마이크 끄기" else "이야기하기"
    private fun updateSceneExpression() {
        sceneView?.setSpeaking(speechState == "speaking")
        sceneView?.setListening(speechState == "listening" && armed)
        sceneView?.setMood(if (sceneResting) "sleepy" else companion.mood.name.lowercase())
    }
    private fun syncSceneLifecycle() {
        if (foreground && carReady && !restricted && dialog == null) sceneView?.resumeScene()
        else sceneView?.pauseScene()
    }
    private fun addScene(parent: LinearLayout, height: Int) {
        val scene = LandscapeFriend3DView(this, state).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        sceneView = scene
        val frame = FrameLayout(this)
        frame.addView(scene, FrameLayout.LayoutParams(-1, -1))
        journeyView = JourneySceneView(this).also { frame.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        parent.addView(frame, LinearLayout.LayoutParams(-1, dp(height)))
        updateSceneExpression()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 24) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(5).toFloat(), 1f)
    }
    private fun gap(parent: LinearLayout, amount: Int) = parent.addView(View(this), LinearLayout.LayoutParams(1, dp(amount)))
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 25f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else ink)
        background = rounded(if (primary) blue else Color.rgb(235, 242, 252), 22)
        minimumHeight = dp(76); minHeight = dp(76)
        setPadding(dp(22), dp(12), dp(22), dp(12))
        setOnClickListener { action() }
    }
    private fun fullButton(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit): Button {
        return button(value, primary, action).also { parent.addView(it, LinearLayout.LayoutParams(-1, dp(86))) }
    }
    private fun pair(parent: LinearLayout, first: Button, second: Button) {
        val actions = row()
        actions.addView(first, LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginEnd = dp(12) })
        actions.addView(second, LinearLayout.LayoutParams(0, dp(80), 1f))
        parent.addView(actions)
    }

    private fun render() {
        if (isDestroyed || !::state.isInitialized) return
        val wide = resources.configuration.screenWidthDp >= 900
        if (layoutWide != wide || storyCard == null) buildLayout(wide)
        sceneView?.updateState(state, foreground && carReady && !restricted)
        journeyView?.present(state.cloudWorld?.journey ?: SkyJourney(), foreground && !restricted)
        updateSceneExpression()
        brandMode?.text = if (demoOriginal != null) "창밖 탐험 / 이동 시연" else if (aiEnabled) "창밖 탐험 / 성인 AI 시연" else "창밖 탐험"
        skyHeading?.text = if (state.cloudWorld?.expectation != null) CloudPlayEngine.journeyTitle(state)
            else if (state.cloudWorld?.journey?.complete == true) "오늘 함께 만든 하늘섬" else "우리 차와 함께 가는 하늘길"
        skyName?.text = friendTitle(state)
        skyCaption?.text = CloudPlayEngine.favorite(state)?.let { "오늘의 기억: ${CloudPlayEngine.title(it)}를 만난 순간" }
            ?: if (hasFriend(state)) "구름에서 태어나, 너와 함께" else "발견한 모습이 여기에 나타나요"
        rememberButton?.visibility = if (!state.cloudWorld?.clouds.isNullOrEmpty() && state.cloudWorld?.draft == null &&
            (state.phase == CloudPhase.WATCHING || state.phase == CloudPhase.KEEPSAKE)) View.VISIBLE else View.GONE
        skyPanel?.visibility = if (carReady && !restricted) View.VISIBLE else View.INVISIBLE
        finishButton?.visibility = if (state.phase == CloudPhase.WATCHING || state.phase == CloudPhase.RESTING) View.VISIBLE else View.GONE
        val snapshot = listOf(state, reply, aiBusy, aiEnabled, carReady, restricted, armed, voiceStatus)
        if (snapshot != lastCardSnapshot) {
            lastCardSnapshot = snapshot
            val card = storyCard ?: return
            val token = ++cardGeneration
            card.animate().cancel()
            val animate = foreground && carReady && !restricted && card.childCount > 0 && ValueAnimator.areAnimatorsEnabled()
            if (animate) {
                card.animate().alpha(0f).setDuration(110L).withEndAction {
                    if (token != cardGeneration || isDestroyed || !foreground) return@withEndAction
                    populateCard(card)
                    card.translationY = dp(5).toFloat()
                    card.animate().alpha(1f).translationY(0f).setDuration(210L).withEndAction(null).start()
                }.start()
            } else {
                card.alpha = 1f; card.translationY = 0f
                populateCard(card)
            }
        }
        syncSceneLifecycle()
    }

    /** The GL surface stays attached across dialogue, creation and selection changes. */
    private fun buildLayout(wide: Boolean) {
        cardGeneration++; storyCard?.animate()?.cancel()
        sceneView?.releaseScene(); sceneView = null
        layoutWide = wide; lastCardSnapshot = null
        val root = column().apply {
            setBackgroundColor(pageColor)
            setPadding(dp(if (wide) 40 else 24), dp(24), dp(if (wide) 40 else 24), dp(24))
        }
        val header = row()
        val brand = column()
        brandMode = label("창밖 탐험", 17f, muted).also { brand.addView(it) }; gap(brand, 7)
        brand.addView(label("구름 친구", 29f, ink, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        finishButton = button("여행 마치기") { if (foreground && !restricted) applyTurn(CloudPlayEngine.finish(state)) }
            .also { header.addView(it, LinearLayout.LayoutParams(-2, dp(70)).apply { marginEnd = dp(12) }) }
        header.addView(button("보호자") { if (!restricted && carReady) parentMenu() }, LinearLayout.LayoutParams(-2, dp(70)))
        root.addView(header); gap(root, 28)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -1))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val sky = column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = rounded(Color.rgb(232, 243, 255), 34)
                setPadding(dp(24), dp(36), dp(24), dp(26))
            }
        skyPanel = sky
        skyHeading = label("", 23f, muted).also { sky.addView(it) }
        addScene(sky, if (wide) 500 else 260)
        gap(sky, 16)
        skyName = label("", 29f, ink, true).apply { gravity = Gravity.CENTER }.also { sky.addView(it) }
        skyCaption = label("", 21f, muted).apply { gravity = Gravity.CENTER }.also { if (wide) { gap(sky, 14); sky.addView(it) } }
        rememberButton = button("이 순간 남기기") {
            if (isConversable()) applyTurn(CloudPlayEngine.remember(state))
        }.also { sky.addView(it, LinearLayout.LayoutParams(-1, dp(74)).apply { topMargin = dp(18) }) }
        body.addView(sky, if (wide) LinearLayout.LayoutParams(0, -1, 0.54f).apply { marginEnd = dp(28) }
            else LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val card = column().apply {
            background = rounded(Color.WHITE, 34)
            setPadding(dp(if (wide) 42 else 26), dp(40), dp(if (wide) 42 else 26), dp(32))
        }
        body.addView(card, if (wide) LinearLayout.LayoutParams(0, -1, 0.46f) else LinearLayout.LayoutParams(-1, -1))
        storyCard = card
        setContentView(root)
    }

    private fun populateCard(card: LinearLayout) {
        micButton = null; statusView = null
        card.removeAllViews()
        when {
            !carReady -> { card.addView(label("차량과 연결하고 있어요", 39f, ink, true)); gap(card, 24); card.addView(label("잠시만 기다려 주세요.", 28f, muted)) }
            restricted -> { card.addView(label("지금은 쉬어가요", 43f, ink, true)); gap(card, 24); card.addView(label("차가 멈추면 이어서 만나요.", 29f, muted)) }
            state.phase == CloudPhase.WELCOME -> welcome(card)
            state.phase == CloudPhase.WATCHING -> watching(card)
            state.phase == CloudPhase.RESTING -> resting(card)
            else -> keepsake(card)
        }
    }

    private fun welcome(card: LinearLayout) {
        card.addView(label("구름은\n뭘 닮았을까?", 44f, ink, true)); gap(card, 28)
        card.addView(label("함께 타고 갈 친구를\n창밖에서 만나봐요.", 29f, muted)); gap(card, 36)
        fullButton(card, "함께 출발하기", true) { beginConversation() }
    }
    private fun watching(card: LinearLayout) {
        card.addView(label(if (aiBusy) "답변을 기다리는 중" else if (hasFriend(state)) "${friendTitle(state)}의 한마디" else "하늘을 만나는 중", 22f, blue, true)); gap(card, 24)
        card.addView(label(reply.ifBlank { if (hasFriend(state)) "오늘은 어떤 일이 있을 것 같아?" else "구름은 어떤 모습 같아?" }, 35f, ink, true)); gap(card, 28)
        friendButtons(card)
        pendingChoiceButtons(card)
        if (demoOriginal != null) {
            pair(card, button("조금 더 달리기") { demoAdvance() }, button("시연 끝내기") { endDemo() })
            gap(card, 14)
        }
        if (state.cloudWorld?.introPending == true) {
            fullButton(card, "구름부터 볼래") { acceptWords("구름부터 볼래") }; gap(card, 14)
        }
        micButton = fullButton(card, micLabel(), true) { toggleMicrophone() }
        gap(card, 14)
        fullButton(card, "잠깐 쉴래") { applyTurn(CloudPlayEngine.rest(state)) }
        gap(card, 22)
        statusView = label(voiceStatus, 21f, muted).also { card.addView(it) }
    }
    private fun resting(card: LinearLayout) {
        card.addView(label("잠깐 쉬어도 좋아", 43f, ink, true)); gap(card, 24)
        card.addView(label("이야기하고 싶어지면\n그때 다시 만나.", 29f, muted)); gap(card, 40)
        fullButton(card, "다시 만날래", true) { beginConversation() }
        gap(card, 22); card.addView(label("지금은 마이크가 꺼져 있어요", 21f, muted))
    }
    private fun hasFriend(saved: TravelState) = CloudPlayEngine.active(saved) != null
    private fun friendTitle(saved: TravelState) = CloudPlayEngine.active(saved)?.let(CloudPlayEngine::title) ?: "네 상상을 기다려요"
    private fun currentMemories(saved: TravelState): List<TravelMemory> =
        saved.memories.asReversed().distinctBy { it.trait.part }.asReversed()
    private fun memoryLine(saved: TravelState): String {
        return saved.cloudWorld?.clouds.orEmpty().joinToString(", ") {
            "${CloudPlayEngine.title(it)} ${if (it.role == CloudRole.FRIEND) "친구" else "구름"}"
        }.ifBlank { "아직 아무 구름도 만들지 않았어요." }
    }
    private fun keepsake(card: LinearLayout) {
        card.addView(label("오늘 함께 만든 하늘섬", 24f, blue, true)); gap(card, 16)
        val landmarks = state.cloudWorld?.journey?.landmarks.orEmpty()
        card.addView(label(landmarks.map { it.kind.label }.distinct().joinToString(" → ").ifBlank { CloudPlayEngine.journeyTitle(state) }, 23f, muted)); gap(card, 20)
        if (demoOriginal != null) { fullButton(card, "시연 끝내기") { endDemo() }; gap(card, 14) }
        card.addView(label(reply.ifBlank { if (hasFriend(state)) "오늘 같이 와서 좋았어. 너는 어땠어?" else "오늘의 하늘을 남겨둘게." }, 34f, ink, true)); gap(card, 30)
        if (CloudPlayEngine.favorite(state) == null && !state.cloudWorld?.clouds.isNullOrEmpty()) {
            val choices = row()
            state.cloudWorld!!.clouds.forEach { cloud -> choices.addView(button(CloudPlayEngine.title(cloud)) {
                applyTurn(CloudPlayEngine.remember(state, cloud.id), false)
            }, LinearLayout.LayoutParams(-2, dp(72)).apply { marginEnd = dp(10) }) }
            card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(choices) }); gap(card, 18)
        } else friendButtons(card)
        pendingChoiceButtons(card)
        micButton = fullButton(card, micLabel(), true) { toggleMicrophone() }
        gap(card, 14)
        pair(card, button("잠깐 쉴래") { applyTurn(CloudPlayEngine.rest(state)) }, button("새 여행") { newTrip() })
        gap(card, 20)
        statusView = label(voiceStatus, 21f, muted).also { card.addView(it) }
    }

    private fun pendingChoiceButtons(card: LinearLayout) {
        if (state.cloudWorld?.draft != null) {
            pair(card, button("친구로 만들자") { acceptWords("친구로 만들자") }, button("하늘에 띄우자") { acceptWords("하늘에 띄우자") })
            gap(card, 18)
        }
    }

    private fun friendButtons(card: LinearLayout) {
        val friends = CloudPlayEngine.friends(state)
        if (friends.size < 2) return
        val strip = row()
        friends.forEach { friend -> strip.addView(button(CloudPlayEngine.title(friend), friend.id == state.cloudWorld?.activeId) {
            quiet(); applyTurn(CloudPlayEngine.select(state, friend.id), false)
        }, LinearLayout.LayoutParams(-2, dp(72)).apply { marginEnd = dp(10) }) }
        card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) })
        gap(card, 18)
    }

    private fun beginConversation() {
        if (!foreground || restricted || !carReady) return
        quiet()
        sceneResting = false
        val start = CloudPlayEngine.start(state)
        companion = companion.copy(pending = if (hasFriend(start.state)) TalkTopic.PLAN else TalkTopic.NONE)
        applyTurn(start, false)
    }

    private fun applyConversation(turn: CompanionTurn, continueListening: Boolean = armed) {
        if (!isConversable()) return
        sceneResting = false
        companion = turn.state
        reply = turn.reply
        replyFromAi = false
        // This conversation state intentionally stays in memory, outside TravelStore.
        render()
        say(turn.reply, continueListening)
    }

    private fun requestAiReply(words: String) {
        if (!aiEnabled || !adultDemoConsent || !isConversable() || !hasFriend(state) || dialog != null || aiBusy) return
        val message = words.trim()
        if (message.isEmpty() || message.length > 500) {
            quiet(); reply = "한 번에 500자 안으로 이야기해 주세요."; replyFromAi = false; render(); return
        }
        val resumeListening = userListeningIntent && voiceConsent
        val token = aiGate.begin(resumeListening)
        aiBusy = true
        // Do not call quiet(): this pause is for one request, not a withdrawal of the user's intent.
        armed = false
        voice.suspend()
        voiceStatus = "AI 답변을 기다리고 있어요"
        reply = "잠깐, 답변을 기다리고 있어."
        replyFromAi = false
        val memories = currentMemories(state).map { FriendChatMemory(it.source.label, it.trait.part.label, it.detail) }
        val appearance = "선택된 친구: ${state.body?.let(CloudPlayEngine::describe)}. 아이가 구름에서 떠올린 모습으로 태어났음. " +
            "함께 있는 하늘: ${memoryLine(state)}. 친구는 대화하고 배경 구름에는 얼굴이 없음. " +
            "오늘 기대하는 일: ${state.cloudWorld?.expectation?.label ?: "아직 말하지 않음"}. " +
            "함께 지나온 상상 길: ${state.cloudWorld?.journey?.landmarks?.map { it.kind.label }?.joinToString()}. 실제 위치와 터널은 직접 볼 수 없으며 아이가 알려준 관찰만 알고 있음. " +
            "아이가 고른 오늘의 기억: ${CloudPlayEngine.favorite(state)?.let(CloudPlayEngine::title) ?: "아직 고르지 않음"}."
        val friend = FriendChatContext(friendTitle(state), appearance, memories.toList())
        render()
        val started = chatClient.chat(message, aiHistory.toList(), friend) { result ->
            if (!aiGate.isCurrent(token)) return@chat
            val resume = aiGate.shouldResumeListening(token) && userListeningIntent && voiceConsent
            if (!aiGate.complete(token)) return@chat
            aiBusy = false
            if (!aiEnabled || !adultDemoConsent || !isConversable() || dialog != null) {
                userListeningIntent = false
                return@chat
            }
            result.fold(onSuccess = { response ->
                aiHistory.add(FriendChatMessage("user", message))
                aiHistory.add(FriendChatMessage("assistant", response.reply))
                while (aiHistory.size > 12) aiHistory.removeAt(0)
                companion = companion.copy(mood = TalkMood.entries.firstOrNull { it.name.equals(response.mood, true) } ?: TalkMood.NEUTRAL,
                    pending = TalkTopic.NONE)
                reply = response.reply
                replyFromAi = true
                sceneResting = false
                render()
                say(response.reply, resume)
            }, onFailure = { aiConnectionFailure() })
        }
        if (!started && aiGate.isCurrent(token)) aiConnectionFailure()
    }

    private fun aiConnectionFailure() {
        quiet()
        reply = "AI 연결을 확인해 주세요."
        replyFromAi = false
        voiceStatus = "보호자 메뉴에서 AI 연결을 다시 확인할 수 있어요"
        render()
    }

    private fun aiConnectionMenu() {
        if (!foreground || restricted || !carReady) return
        quiet()
        if (aiEnabled) {
            showDialog(AlertDialog.Builder(this).setTitle("AI 자유 대화 연결")
                .setMessage("현재 성인 시연용 AI 모드예요. 실제 아동의 목소리나 개인정보를 보내지 마세요.\n\nAI 연결을 끄면 기기 안의 짧은 규칙 대화로 돌아갑니다.")
                .setNegativeButton("닫기", null)
                .setNeutralButton("연결 다시 확인") { _, _ -> handler.post { checkAiConnection() } }
                .setPositiveButton("AI 연결 끄기") { _, _ ->
                    quiet(); aiEnabled = false; adultDemoConsent = false; voiceConsent = false
                    aiHistory.clear(); replyFromAi = false; reply = "기기 안의 대화로 돌아왔어요."
                    render()
                }.create())
            return
        }
        showDialog(AlertDialog.Builder(this).setTitle("성인 시연용 AI 자유 대화")
            .setMessage("성인 또는 가상 예시로만 시험해 주세요. 실제 아동의 목소리와 개인정보는 보내지 마세요.\n\n입력 문장, 최근 12개 대화, 현재 친구의 별명과 풍경 기억이 이 PC의 중계 서버를 거쳐 OpenAI에 전달됩니다. 기록에 실제 아동 정보가 없는지 먼저 확인해 주세요.\n\n아동용 실제 사용을 위한 데이터 처리 검토는 아직 완료되지 않았습니다. 연결 후에도 마이크는 직접 켜야 해요.")
            .setNegativeButton("지금은 로컬로", null)
            .setPositiveButton("성인 / 가상 입력으로 시연") { _, _ ->
                adultDemoConsent = true
                handler.post { checkAiConnection() }
            }.create())
    }

    private fun checkAiConnection() {
        if (!adultDemoConsent || !foreground || restricted || !carReady || dialog != null) return
        quiet()
        voiceConsent = false
        val token = aiGate.begin(false)
        aiBusy = true
        voiceStatus = "AI 연결을 확인하고 있어요"
        render()
        val started = chatClient.health { result ->
            if (!aiGate.isCurrent(token) || !aiGate.complete(token)) return@health
            aiBusy = false
            if (!adultDemoConsent || !foreground || restricted || !carReady || dialog != null) return@health
            val health = result.getOrNull()
            if (health?.ready != true || health.mode != "adult-demo") {
                aiEnabled = false
                adultDemoConsent = false
                aiConnectionFailure()
                return@health
            }
            aiEnabled = true
            aiHistory.clear()
            companion = CompanionState()
            replyFromAi = false
            voiceStatus = "성인 시연용 AI가 연결됐어요"
            render()
            showDialog(AlertDialog.Builder(this).setTitle("AI 연결을 확인했어요")
                .setMessage("성인 또는 가상 예시로 대화를 시험할 수 있어요. 마이크는 아직 꺼져 있어요.")
                .setPositiveButton("확인", null).create())
        }
        if (!started && aiGate.isCurrent(token)) aiConnectionFailure()
    }

    private fun say(words: String?, listenAfter: Boolean) {
        if (words.isNullOrBlank() || !foreground || restricted || !carReady) return
        syncSceneLifecycle()
        val shouldListen = listenAfter && userListeningIntent && voiceConsent && isConversable()
        // A completed trip is conversational, but completion itself must not reopen the mic.
        if (!shouldListen && voice.enabled) quiet()
        voice.speak(words)
        if (shouldListen) {
            userListeningIntent = true
            armed = voice.enableCommands()
            if (!armed) userListeningIntent = false
            updateMicLabel()
        }
    }
    private fun rememberUndo() { undo.addLast(state); while (undo.size > 10) undo.removeFirst() }
    private fun applyTurn(turn: TravelTurn, continueListening: Boolean = armed) {
        if (!foreground || restricted || !carReady) return
        if (aiBusy) quiet()
        sceneResting = turn.outcome == TravelOutcome.REST
        if (turn.outcome == TravelOutcome.UNDO) { undoLast(continueListening); return }
        if (turn.outcome == TravelOutcome.REPEAT) { say(reply.ifBlank { "눈에 들어온 모습을 네 말로 들려줘." }, continueListening); return }
        if (turn.outcome in setOf(TravelOutcome.OBSERVED, TravelOutcome.CORRECTED) &&
            turn.state.cloudWorld?.clouds != state.cloudWorld?.clouds) rememberUndo()
        if (turn.outcome in setOf(TravelOutcome.OBSERVED, TravelOutcome.CORRECTED, TravelOutcome.UNCHANGED, TravelOutcome.REST, TravelOutcome.FINISH)) {
            companion = companion.copy(pending = TalkTopic.NONE)
        }
        if (turn.state.cloudWorld?.activeId != state.cloudWorld?.activeId) {
            invalidateAi(clearHistory = true)
            companion = CompanionState(pending = if (hasFriend(turn.state)) TalkTopic.PLAN else TalkTopic.NONE)
        }
        state = turn.state
        replyFromAi = false
        if (!turn.reply.isNullOrBlank()) reply = turn.reply!!
        val endListening = turn.outcome == TravelOutcome.FINISH || turn.outcome == TravelOutcome.REST || !isConversable()
        if (endListening) quiet()
        saveWorld()
        Log.i("LandscapeFriends", "Travel turn: ${turn.outcome}; phase=${state.phase}; memories=${state.memories.size}")
        render()
        say(turn.reply, continueListening && !endListening)
    }
    private fun acceptWords(words: String) {
        if (!isConversable()) return
        val compact = words.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        if (compact in setOf("쉬고싶어", "쉬고싶어요", "쉬고싶다", "쉬자", "잠깐쉬자", "좀쉴래", "쉴래", "잠깐쉴래", "조용히있고싶어", "조용히있자", "조용히", "그만이야기할래", "그만말할래", "말하기싫어", "말하기싫어요", "이야기하기싫어")) {
            applyTurn(CloudPlayEngine.rest(state))
            return
        }
        if (!aiEnabled && companion.pending == TalkTopic.PLAN && compact in setOf("모르겠어", "아직모르겠어", "생각안나", "그냥갈래")) {
            CompanionEngine.respond(companion, words, state)?.let { applyConversation(it); return }
        }
        val travel = CloudPlayEngine.observe(state, words)
        // Creating clouds and selecting their role is separate from daily conversation.
        val hasTripPriority = travel.outcome !in setOf(TravelOutcome.CHAT, TravelOutcome.IGNORED) ||
            travel.state != state || travel.reply != null
        if (!hasTripPriority) {
            if (hasFriend(state)) {
                if (aiEnabled) { requestAiReply(words); return }
                CompanionEngine.respond(companion, words, state)?.let { applyConversation(it); return }
                applyTurn(travel.copy(reply = "그랬구나. 오늘 기대되는 일이 있어?"))
            } else applyTurn(CloudPlayEngine.help(state))
            return
        }
        applyTurn(travel)
    }
    private fun undoLast(continueListening: Boolean = armed) {
        if (!isConversable()) return
        if (aiBusy) quiet()
        sceneResting = false
        if (undo.isEmpty()) { say("아직 되돌릴 말이 없어.", continueListening); return }
        val phase = state.phase
        val currentJourney = state.cloudWorld?.journey ?: SkyJourney()
        val original = undo.removeLast()
        val previous = original.copy(phase = phase, pending = FriendChoice.NONE, cloudWorld = original.cloudWorld?.copy(journey = currentJourney))
        state = CloudPlayEngine.project(previous, (previous.cloudWorld ?: CloudWorld()).copy(draft = null))
        invalidateAi(clearHistory = true)
        companion = companion.copy(pending = TalkTopic.NONE)
        reply = "괜찮아. 방금 전 하늘로 돌아왔어."
        replyFromAi = false
        saveWorld(); render(); say(reply, continueListening)
    }
    private fun memoryQuestions() {
        if (!isConversable()) return
        quiet()
        val remembered = state.cloudWorld?.clouds.orEmpty()
        if (remembered.isEmpty()) {
            applyTurn(CloudPlayEngine.help(state), false)
            return
        }
        showDialog(AlertDialog.Builder(this).setTitle("오늘 발견한 구름")
            .setItems(remembered.map { "${CloudPlayEngine.title(it)} ${if (it.role == CloudRole.FRIEND) "친구" else "구름"}" }.toTypedArray()) { _, index ->
                handler.post {
                    if (!isConversable()) return@post
                    val cloud = remembered[index]
                    if (cloud.role == CloudRole.FRIEND) applyTurn(CloudPlayEngine.select(state, cloud.id), false)
                    else applyTurn(TravelTurn(state, "네가 발견한 ${cloud.figure.label} 구름이 하늘에 떠 있어.", TravelOutcome.CHAT), false)
                }
            }.setNegativeButton("닫기", null).create())
    }
    private fun toggleMicrophone() {
        if (!isConversable()) return
        if (aiBusy) { quiet(); voiceStatus = "대화를 멈췄어요"; render(); return }
        if (armed) { quiet(); voiceStatus = "마이크가 꺼져 있어요"; render(); return }
        sceneResting = false
        syncSceneLifecycle()
        if (voiceConsent) {
            userListeningIntent = true
            armed = voice.enableCommands()
            if (!armed) userListeningIntent = false
            render(); return
        }
        quiet()
        val explanation = if (aiEnabled) "성인 시연용입니다. 실제 아동의 목소리나 개인정보를 사용하지 마세요.\n\n음성 인식은 차량 안에서 처리하지만, 자유 대화 문장과 최근 대화 및 친구의 이름과 풍경 기억은 PC 중계 서버를 거쳐 OpenAI에 전달됩니다. 이 앱은 녹음을 저장하지 않으며, 최근 대화는 이번 실행에서만 유지합니다."
            else "구름이 어떤 모습을 닮았는지 들려줘요. 친구가 태어나면 오늘의 이야기도 나눌 수 있어요.\n\n음성은 차량 안에서 처리해요. 구름의 모양, 역할, 별명, 오늘 기대하는 활동 종류와 고른 기억, 총 이동량과 발견한 풍경 종류를 기기에 남겨요. 정확한 행선지, 녹음과 문장 전체는 저장하지 않아요. 쉬거나 앱을 벗어나면 마이크가 꺼져요."
        showDialog(AlertDialog.Builder(this).setTitle(if (aiEnabled) "성인 시연 음성 연결" else "친구와 이야기할까요?")
            .setMessage(explanation)
            .setNegativeButton("나중에", null)
            .setPositiveButton("마이크 켜기") { _, _ -> if (isConversable()) {
                voiceConsent = true; userListeningIntent = true; armed = voice.enableCommands()
                if (!armed) userListeningIntent = false
                render()
            } }.create())
    }
    private fun showDialog(next: AlertDialog) {
        if (!foreground || restricted || !carReady || isFinishing || isDestroyed) return
        quiet()
        dialog?.dismiss(); dialog = next
        sceneView?.pauseScene()
        next.setOnDismissListener { if (dialog === next) { dialog = null; syncSceneLifecycle() } }
        next.show()
    }
    private fun saveWorld() { if (demoOriginal == null) store.save(state) }
    private fun demoAdvance() {
        if (demoOriginal == null || restricted || !foreground || state.phase == CloudPhase.KEEPSAKE) return
        val world = state.cloudWorld ?: return
        state = state.copy(cloudWorld = world.copy(journey = SkyJourneyEngine.advance(world.journey, 180f, JourneySource.DEMO)))
        render()
    }
    private fun startDemo() {
        if (demoOriginal != null) return
        quiet(); demoOriginal = state
        invalidateAi(clearHistory = true); undo.clear(); companion = CompanionState()
        state = CloudPlayEngine.start(CloudPlayEngine.create()).state
        reply = "우리 차에 함께 탈 구름 친구를 만나볼까? 이건 이동 시연이야."
        demoAdvance()
    }
    private fun endDemo() {
        val original = demoOriginal ?: return
        quiet(); state = original; demoOriginal = null
        invalidateAi(clearHistory = true); undo.clear(); companion = CompanionState()
        reply = "원래 하늘로 돌아왔어."; render()
    }
    private fun vehicleMenu() {
        showDialog(AlertDialog.Builder(this).setTitle("차량 이동과 시연")
            .setMessage("$vehicleStatus\n\n차량 이동을 연결하면 속도 정보로 이동량만 계산해 하늘길을 만들어요. 위치 좌표는 저장하지 않아요.\n\n이동 시연은 원래 여행을 보관하고 별도 하늘에서 진행해요. 터널은 아이가 알려준 말로 반응해요. 현재 화면은 정차 시연용이에요.")
            .setNegativeButton("닫기", null)
            .setNeutralButton(if (demoOriginal == null) "이동 시연 시작" else "시연 끝내기") { _, _ -> handler.post { if (demoOriginal == null) startDemo() else endDemo() } }
            .setPositiveButton("차량 이동 연결") { _, _ ->
                if (checkSelfPermission("android.car.permission.CAR_SPEED") == android.content.pm.PackageManager.PERMISSION_GRANTED) car?.let(vehicleJourney::connect)
                else requestPermissions(arrayOf("android.car.permission.CAR_SPEED"), 81)
            }.create())
    }
    private fun parentMenu() {
        quiet()
        val items = arrayOf("말 대신 입력해 보기", "오늘 발견한 구름", "방금 말 되돌리기", "지난 하늘 보기", "음성과 저장 안내", "처음부터 새 여행", "AI 자유 대화 연결", "차량 이동 / 시연")
        showDialog(AlertDialog.Builder(this).setTitle("보호자 메뉴").setItems(items) { _, index ->
            when (index) {
                0 -> handler.post { tryWords() }
                1 -> handler.post { memoryQuestions() }
                2 -> handler.post { undoLast(false) }
                3 -> handler.post { previousKeepsake() }
                4 -> handler.post { information() }
                5 -> handler.post { newTrip() }
                6 -> handler.post { aiConnectionMenu() }
                7 -> handler.post { vehicleMenu() }
            }
        }.setNegativeButton("닫기", null).create())
    }
    /** Text and example inputs use exactly the same reducer as the microphone. */
    private fun tryWords() {
        if (!foreground || restricted || !carReady) return
        quiet()
        val panel = column().apply { setPadding(dp(24), dp(16), dp(24), dp(12)) }
        val field = EditText(this).apply {
            hint = if (aiEnabled) "성인의 시연 문장이나 가상 예시" else "한마디를 입력해 주세요"; textSize = 23f; maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.LengthFilter(if (aiEnabled) 500 else 160))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        panel.addView(field, LinearLayout.LayoutParams(-1, dp(100))); gap(panel, 12)
        panel.addView(label("예시를 누르거나 자유롭게 입력해 보세요.", 19f, muted)); gap(panel, 12)
        var entryDialog: AlertDialog? = null
        var submitted = false
        fun submit(words: String) {
            if (words.isBlank() || submitted) return
            submitted = true
            entryDialog?.dismiss()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
            // Wait until OnDismiss clears the active dialog before entering the async chat route.
            handler.post {
                if (!foreground || restricted || !carReady || dialog != null || isFinishing || isDestroyed) return@post
                // Post-arrival input talks to the same friend instead of silently starting over.
                if (state.phase == CloudPhase.WELCOME || state.phase == CloudPhase.RESTING) {
                    state = CloudPlayEngine.start(state).state
                    companion = companion.copy(pending = if (hasFriend(state)) TalkTopic.PLAN else TalkTopic.NONE)
                }
                acceptWords(words)
            }
        }
        val examples = when {
            state.cloudWorld?.draft != null -> listOf("친구로 만들자", "하늘에 띄우자", "나중에")
            state.cloudWorld?.introPending == true -> listOf("할머니 만나는 게 기대돼", "공원에서 놀 거야", "구름부터 볼래", "저 구름 토끼 같아")
            !hasFriend(state) -> listOf("저 구름 토끼 같아", "구름이 고래 같아", "길쭉해")
            else -> listOf("나무가 엄청 많아", "커다란 다리를 건너고 있어", "터널에 들어왔어", "터널에서 나왔어", "저쪽 구름은 고래 같아")
        }
        examples.forEach { example ->
            panel.addView(button(example) { submit(example) }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) })
        }
        val inputScroll = ScrollView(this).apply { addView(panel); isFillViewport = true }
        entryDialog = AlertDialog.Builder(this).setTitle("말 대신 입력해 보기").setView(inputScroll)
            .setNegativeButton("닫기", null).setPositiveButton("말 전하기", null).create()
        showDialog(entryDialog!!)
        entryDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(field.text.toString()) }
    }
    private fun information() {
        val mode = if (aiEnabled) "현재 성인 시연용 AI 모드예요. 자유 대화 문장과 최근 대화 및 친구의 이름과 기억을 OpenAI로 전달해 답변을 받아요. 실제 아동의 목소리나 개인정보는 보내지 마세요. 최근 12개 대화는 실행 중에만 유지하고 앱을 벗어나거나 새 여행을 시작하면 지워요."
            else "현재 기기 안의 규칙 대화 모드예요. 외부 AI에는 보내지 않아요. 보호자 메뉴에서 성인 시연용 AI 연결을 별도로 켤 수 있어요."
        val message = "$mode\n\n구름의 모양과 역할, 별명, 오늘 기대하는 활동 종류와 고른 기억, 총 이동량과 발견한 풍경 종류를 기기에 남겨요. 정확한 행선지, 녹음과 문장 전체는 저장하지 않으며 실제 하늘을 촬영하거나 판별하지 않아요. 준비된 입체 형태로 표현하며 낯선 모양은 이름 붙인 몽실한 구름으로 나타나요.\n\n말이 없으면 듣기를 마쳐요. 다시 이야기할 때 버튼을 눌러 주세요.\n\n${voice.engineLabel}\n현재 버전은 정차 상태에서 시연할 수 있어요."
        showDialog(AlertDialog.Builder(this).setTitle("음성과 저장 안내").setMessage(message).setPositiveButton("확인", null).create())
    }
    private fun previousKeepsake() {
        val saved = store.lastKeepsake()
        if (saved == null || saved.cloudWorld?.clouds.isNullOrEmpty()) {
            showDialog(AlertDialog.Builder(this).setTitle("아직 여행 그림이 없어요").setMessage("여행을 마치면 풍경 친구 한 장이 남아요.").setPositiveButton("확인", null).create()); return
        }
        val panel = column().apply { setPadding(dp(28), dp(12), dp(28), dp(24)) }
        val savedScene = FrameLayout(this)
        savedScene.addView(LandscapeFriend3DView(this, saved), FrameLayout.LayoutParams(-1, -1))
        savedScene.addView(JourneySceneView(this).apply { present(saved.cloudWorld?.journey ?: SkyJourney(), false) }, FrameLayout.LayoutParams(-1, -1))
        panel.addView(savedScene, LinearLayout.LayoutParams(-1, dp(320)))
        panel.addView(label(memoryLine(saved), 23f))
        CloudPlayEngine.favorite(saved)?.let { gap(panel, 12); panel.addView(label("내가 남긴 기억: ${CloudPlayEngine.title(it)}", 24f, blue, true)) }
        showDialog(AlertDialog.Builder(this).setTitle(CloudPlayEngine.journeyTitle(saved)).setView(panel).setPositiveButton("닫기", null).create())
    }
    private fun newTrip() {
        if (!foreground || restricted || !carReady) return
        quiet()
        showDialog(AlertDialog.Builder(this).setTitle("새 여행을 시작할까요?")
            .setMessage("마지막 여행 그림은 보호자 메뉴에서 다시 볼 수 있어요.")
            .setNegativeButton("취소", null).setPositiveButton("새 여행") { _, _ ->
                if (foreground && !restricted && carReady) {
                    invalidateAi(clearHistory = true)
                    state = CloudPlayEngine.create(); companion = CompanionState(); sceneResting = false; undo.clear(); reply = ""; replyFromAi = false; voiceConsent = false; saveWorld(); render()
                }
            }.create())
    }
}
