package dev.changbak.explorer.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener
import java.util.Locale

/**
 * Foreground, explicitly enabled listening sessions. This class never asks for permissions,
 * registers a server account, or starts a microphone during construction.
 * The host must call suspend() on pause/window hiding, and release() on destruction.
 * With onUtterance, completed speech is delivered once without a command vocabulary.
 * Silence ends that listening turn quietly; it never triggers another spoken prompt.
 */
class VoiceGuide(
    context: Context,
    private val onState: (String) -> Unit,
    private val onCommand: (MissionCommand) -> Unit,
    private val onError: (String) -> Unit,
    private val onUtterance: ((String) -> Unit)? = null,
    private val initializeListening: Boolean = true,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    @Volatile private var disposed = false
    @Volatile private var commandsEnabled = false
    @Volatile private var sttReady = false
    @Volatile private var ttsReady = false
    @Volatile private var androidReady = false
    private var sttInitializing = false
    private var ttsInitializing = false
    private var androidInitializing = false
    private var initializationScheduled = true
    private var focusHeld = false
    private var focusEpoch = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var lastSpeechCompletedAt: Long? = null
    private var stopping = false
    private var epoch = 0L
    private var state = "idle"
    private var failureCount = 0
    private var commandGuideShown = false
    private var tts: PleosTts? = null
    private var stt: SpeechToText? = null
    private var androidTts: AndroidTts? = null
    private var pendingSpeech: SpeechRun? = null
    private var speech: SpeechRun? = null
    private var listening: ListeningRun? = null
    private var listenTimer: Runnable? = null
    private var ttsInitTimer: Runnable? = null
    private var sttInitTimer: Runnable? = null
    private var androidInitTimer: Runnable? = null

    val enabled: Boolean get() = commandsEnabled && !disposed
    val canListen: Boolean get() = sttReady && !disposed
    val engineLabel: String get() = when {
        disposed -> "음성 꺼짐"
        ttsReady && sttReady -> "Pleos 기기 내 음성"
        ttsReady -> "Pleos 음성 안내"
        androidReady && sttReady -> "기기 음성 안내와 Pleos 듣기"
        androidReady -> "Android 음성 안내"
        initializationScheduled || ttsInitializing || androidInitializing || sttInitializing -> "음성 준비 중"
        else -> "화면으로 진행"
    }

    private class SpeechRun(
        val epoch: Long,
        val text: String,
        val onDone: () -> Unit,
        val onAborted: () -> Unit,
    ) {
        var listener: EventListener? = null
        var timeout: Runnable? = null
        var settled = false
        val utteranceId = "mission-$epoch"
    }

    private class ListeningRun(val epoch: Long) {
        var listener: ResultListener? = null
        var timeout: Runnable? = null
        var endedGrace: Runnable? = null
    }

    init {
        // Posting avoids delivering callbacks before the host has assigned its VoiceGuide field.
        main.post {
            initializationScheduled = false
            if (!disposed) { initializeTts(); if (initializeListening) initializeStt() }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun notifyError(message: String) {
        if (!disposed) runCatching { onError(message) }
    }

    private fun publishState(value: String, force: Boolean = false) {
        if (disposed || (!force && state == value)) return
        state = value
        runCatching { onState(value) }
    }

    /**
     * Every started run reports exactly one outcome. A host that waits for narration to finish
     * before moving on would otherwise stay in that phase forever when speech fails, is replaced,
     * or loses audio focus, and that stuck phase blocks every later place.
     */
    private fun settle(run: SpeechRun, completed: Boolean) {
        if (run.settled) return
        run.settled = true
        // Completion already reports from a finished teardown, so it stays synchronous.
        if (completed) { runCatching { run.onDone() }; return }
        // Aborts report on a later main-thread pass: cancelActive() runs while speak() or
        // suspend() is still unwinding, and a host that starts the next narration from this
        // callback must not re-enter the call that cancelled it.
        main.post { if (!disposed) runCatching { run.onAborted() } }
    }

    private fun valid(token: Long) = !disposed && token == epoch
    private fun isSpeech(run: SpeechRun) = speech === run && valid(run.epoch)
    private fun isListening(run: ListeningRun) = listening === run && valid(run.epoch) && enabled
    private fun permitted(permission: String) =
        app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun initializeTts() {
        if (disposed || ttsInitializing || ttsReady) return
        if (!permitted(TTS_PERMISSION)) { initializeAndroidTts(); return }
        ttsInitializing = true
        try {
            val engine = PleosTts(app, TtsMode.ON_DEVICE)
            tts = engine
            ttsInitTimer = Runnable {
                if (tts === engine && ttsInitializing) failPleosTts(engine)
            }.also { main.postDelayed(it, INIT_TIMEOUT_MS) }
            engine.initialize(object : TtsInitializeListener {
                override fun onSuccess() = onMain {
                    if (disposed || tts !== engine || !ttsInitializing) return@onMain
                    ttsInitTimer?.let(main::removeCallbacks)
                    ttsInitTimer = null
                    try {
                        engine.setLocale(Locale.KOREA)
                        engine.setAudioAttributes(attributes)
                        ttsInitializing = false
                        ttsReady = true
                        publishState(state, force = true)
                        playPendingSpeech()
                    } catch (_: Exception) { failPleosTts(engine) }
                }
                override fun onFailure(errorType: TtsError) = onMain {
                    if (!disposed && tts === engine && ttsInitializing) failPleosTts(engine)
                }
            })
        } catch (_: Exception) {
            ttsInitTimer?.let(main::removeCallbacks)
            ttsInitTimer = null
            ttsInitializing = false
            val failedEngine = tts
            tts = null
            runCatching { failedEngine?.release() }
            initializeAndroidTts()
        }
    }

    private fun failPleosTts(engine: PleosTts) {
        if (tts !== engine) return
        ttsInitTimer?.let(main::removeCallbacks)
        ttsInitTimer = null
        ttsReady = false
        ttsInitializing = false
        tts = null
        runCatching { engine.release() }
        initializeAndroidTts()
    }

    private fun initializeAndroidTts() {
        if (disposed || androidInitializing || androidReady) return
        androidInitializing = true
        androidInitTimer = Runnable {
            if (androidInitializing && !disposed) failAndroidTts()
        }.also { main.postDelayed(it, INIT_TIMEOUT_MS) }
        try {
            // Always post: some engines can invoke initialization synchronously.
            androidTts = AndroidTts(app) { result -> main.post {
                if (disposed || !androidInitializing) return@post
                val engine = androidTts
                if (result != AndroidTts.SUCCESS || engine == null) { failAndroidTts(); return@post }
                try {
                    val koreanVoice = engine.voices?.firstOrNull {
                        it.locale.language == Locale.KOREAN.language && !it.isNetworkConnectionRequired
                    }
                    if (koreanVoice == null || engine.setVoice(koreanVoice) != AndroidTts.SUCCESS) {
                        failAndroidTts()
                        return@post
                    }
                    engine.setAudioAttributes(attributes)
                    engine.setSpeechRate(0.92f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = onMain {
                            val run = speech ?: return@onMain
                            if (isSpeech(run) && utteranceId == run.utteranceId) publishState("speaking")
                        }
                        override fun onDone(utteranceId: String?) = onMain {
                            val run = speech ?: return@onMain
                            if (isSpeech(run) && utteranceId == run.utteranceId) finishSpeech(run)
                        }
                        @Deprecated("Framework callback")
                        override fun onError(utteranceId: String?) = speechFailed(utteranceId)
                        override fun onError(utteranceId: String?, errorCode: Int) = speechFailed(utteranceId)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = onMain {
                            val run = speech ?: return@onMain
                            if (isSpeech(run) && utteranceId == run.utteranceId) suspend()
                        }
                    })
                    androidInitTimer?.let(main::removeCallbacks)
                    androidInitTimer = null
                    androidInitializing = false
                    androidReady = true
                    publishState(state, force = true)
                    playPendingSpeech()
                } catch (_: Exception) { failAndroidTts() }
            } }
        } catch (_: Exception) { failAndroidTts() }
    }

    private fun failAndroidTts() {
        androidInitTimer?.let(main::removeCallbacks)
        androidInitTimer = null
        androidInitializing = false
        androidReady = false
        val engine = androidTts
        androidTts = null
        runCatching { engine?.shutdown() }
        val hadPending = pendingSpeech != null
        if (hadPending) {
            cancelActive()
            commandsEnabled = false
            notifyError("음성 안내를 준비하지 못했어요. 화면의 버튼으로 이어갈 수 있어요.")
        }
        publishState("idle", force = true)
    }

    private fun initializeStt() {
        if (disposed || sttInitializing || sttReady) return
        if (!permitted(STT_PERMISSION)) { publishState(state, force = true); return }
        sttInitializing = true
        try {
            val engine = SpeechToText(app, SttMode.ON_DEVICE)
            stt = engine
            sttInitTimer = Runnable {
                if (stt === engine && sttInitializing) failStt(engine)
            }.also { main.postDelayed(it, INIT_TIMEOUT_MS) }
            engine.initialize(object : SttInitializeListener {
                override fun onSuccess() = onMain {
                    if (disposed || stt !== engine || !sttInitializing) return@onMain
                    sttInitTimer?.let(main::removeCallbacks)
                    sttInitTimer = null
                    try {
                        engine.setLocale(Locale.KOREA)
                        sttInitializing = false
                        sttReady = true
                        publishState(state, force = true)
                    } catch (_: Exception) { failStt(engine) }
                }
                override fun onFailure(errorType: SttError) = onMain {
                    if (!disposed && stt === engine && sttInitializing) failStt(engine)
                }
            })
        } catch (_: Exception) {
            sttInitTimer?.let(main::removeCallbacks)
            sttInitTimer = null
            sttInitializing = false
            sttReady = false
            val failedEngine = stt
            stt = null
            runCatching { failedEngine?.release() }
            publishState(state, force = true)
        }
    }

    private fun failStt(engine: SpeechToText) {
        if (stt !== engine) return
        sttInitTimer?.let(main::removeCallbacks)
        sttInitTimer = null
        sttReady = false
        sttInitializing = false
        stt = null
        commandsEnabled = false
        runCatching { engine.release() }
        publishState(state, force = true)
    }

    /**
     * onAborted precedes onDone so an existing trailing-lambda call site still supplies onDone.
     * Exactly one of the two runs for any text that reaches a SpeechRun.
     */
    fun speak(text: String, onAborted: () -> Unit = {}, onDone: () -> Unit = {}) = onMain {
        // These two refusals never reach a SpeechRun, so they report through the same later pass.
        if (disposed || stopping) { main.post { if (!disposed) runCatching { onAborted() } }; return@onMain }
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        cancelActive()
        if (cleaned.isBlank()) {
            publishState("idle")
            main.post { if (!disposed) runCatching { onAborted() } }
            return@onMain
        }
        val run = SpeechRun(epoch, cleaned.take(1000), onDone, onAborted)
        pendingSpeech = run
        publishState("starting")
        if (!valid(run.epoch)) { pendingSpeech = null; settle(run, false); return@onMain }
        if (ttsReady || androidReady) playPendingSpeech()
        else if (!initializationScheduled && !ttsInitializing && !androidInitializing) {
            pendingSpeech = null
            commandsEnabled = false
            publishState("idle")
            notifyError("지금은 음성 안내를 사용할 수 없어요. 화면을 보며 이어가요.")
            settle(run, false)
        }
    }

    private fun playPendingSpeech() {
        val run = pendingSpeech ?: return
        if (!valid(run.epoch)) { pendingSpeech = null; settle(run, false); return }
        // Still waiting on engine initialization: keep the run pending rather than ending it.
        if (!ttsReady && !androidReady) return
        pendingSpeech = null
        if (!takeFocus()) {
            stopWithError("다른 소리를 듣고 있어요. 잠시 후 다시 들려줄게요.")
            settle(run, false)
            return
        }
        if (!valid(run.epoch)) { settle(run, false); return }
        speech = run
        run.timeout = Runnable {
            if (isSpeech(run)) stopWithError("음성 안내가 멈췄어요. 버튼으로 이어갈 수 있어요.")
        }.also { main.postDelayed(it, (5000L + run.text.length * 180L).coerceIn(8000L, 35000L)) }
        publishState("speaking")
        if (!isSpeech(run)) return
        try {
            val pleos = tts
            if (ttsReady && pleos != null) {
                val listener = object : EventListener {
                    override fun onReady() = Unit
                    override fun onStart() = onMain { if (isSpeech(run)) publishState("speaking") }
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() = onMain { if (isSpeech(run)) finishSpeech(run) }
                    override fun onStop() = onMain { if (isSpeech(run)) suspend() }
                    override fun onError(errorType: TtsError) = onMain {
                        if (isSpeech(run)) stopWithError("음성 안내를 듣기 어려워요. 버튼으로 이어갈 수 있어요.")
                    }
                }
                run.listener = listener
                pleos.addEventListener(listener)
                // Pleos streams directly; its public API has no PCM/pre-roll control.
                pleos.speak(run.text, true)
            } else {
                val android = androidTts ?: throw IllegalStateException()
                if (android.playSilentUtterance(180L, AndroidTts.QUEUE_FLUSH, "silence-${run.epoch}") == AndroidTts.ERROR ||
                    android.speak(run.text, AndroidTts.QUEUE_ADD, null, run.utteranceId) == AndroidTts.ERROR
                ) stopWithError("음성 안내를 듣기 어려워요. 버튼으로 이어갈 수 있어요.")
            }
        } catch (_: Exception) { if (isSpeech(run)) stopWithError("음성 안내를 듣기 어려워요. 버튼으로 이어갈 수 있어요.") }
    }

    private fun speechFailed(utteranceId: String?) = onMain {
        val run = speech ?: return@onMain
        if (isSpeech(run) && (utteranceId == run.utteranceId || utteranceId == "silence-${run.epoch}")) {
            stopWithError("음성 안내를 듣기 어려워요. 버튼으로 이어갈 수 있어요.")
        }
    }

    private fun finishSpeech(run: SpeechRun) {
        if (!isSpeech(run)) return
        speech = null
        run.timeout?.let(main::removeCallbacks)
        run.listener?.let { listener -> runCatching { tts?.removeEventListener(listener) } }
        lastSpeechCompletedAt = SystemClock.elapsedRealtime()
        abandonFocus()
        publishState("idle")
        if (!valid(run.epoch)) { settle(run, false); return }
        settle(run, true)
        scheduleListening(run.epoch)
    }

    /** Call on the main thread only, directly from the explicit voice opt-in action. */
    fun enableCommands(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || stopping) return false
        // A fresh, explicit opt-in can recover a transient service initialization failure.
        // Async initialization alone never enables commands or opens the microphone.
        if (!sttReady && !sttInitializing && permitted(STT_PERMISSION)) initializeStt()
        if (!canListen || !permitted(STT_PERMISSION)) {
            commandsEnabled = false
            notifyError(if (sttInitializing) "말로 하기를 준비하고 있어요. 잠시 후 다시 눌러줘요." else "지금은 말로 하기를 사용할 수 없어요. 버튼으로 이어갈 수 있어요.")
            return false
        }
        commandsEnabled = true
        failureCount = 0
        commandGuideShown = false
        if (speech != null || pendingSpeech != null || listening != null) return true
        clearListenTimer()
        val elapsed = lastSpeechCompletedAt?.let { SystemClock.elapsedRealtime() - it }
        if (elapsed != null && elapsed < LISTEN_DELAY_MS) {
            scheduleListening(epoch, LISTEN_DELAY_MS - elapsed)
        } else startListening(epoch)
        return enabled
    }

    private fun scheduleListening(token: Long, delay: Long = LISTEN_DELAY_MS) {
        if (!valid(token) || !enabled || speech != null || pendingSpeech != null) return
        clearListenTimer()
        listenTimer = Runnable {
            listenTimer = null
            if (valid(token) && enabled) startListening(token)
        }.also { main.postDelayed(it, delay) }
    }

    private fun startListening(token: Long) {
        if (!valid(token) || !enabled || !canListen || listening != null || speech != null || pendingSpeech != null) return
        val engine = stt ?: return
        if (!permitted(STT_PERMISSION)) { stopWithError("마이크를 사용할 수 없어요. 보호자와 음성 권한을 확인해 주세요."); return }
        if (!takeFocus()) { stopWithError("다른 소리를 듣고 있어요. 버튼으로 이어갈 수 있어요."); return }
        if (!valid(token) || !enabled) return
        val run = ListeningRun(token)
        listening = run
        val listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onStartedRecognition() = onMain { if (isListening(run)) publishState("listening") }
            override fun onUpdated(stt: String, completed: Boolean) = onMain {
                if (isListening(run) && completed && stt.isNotBlank()) acceptText(run, stt)
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onEndedRecognition() = onMain {
                if (!isListening(run)) return@onMain
                // Some services send the final text just after the end event.
                run.endedGrace?.let(main::removeCallbacks)
                run.endedGrace = Runnable {
                    if (isListening(run)) { closeListening(run); retryListening(token, unknown = false) }
                }.also { main.postDelayed(it, 250L) }
            }
            override fun onError(errorType: SttError) = onMain {
                if (!isListening(run)) return@onMain
                closeListening(run)
                if (errorType == SttError.TIMEOUT) retryListening(token, unknown = false)
                else stopWithError(if (errorType == SttError.PERMISSION_DENIED || errorType == SttError.MIC_ERROR) {
                    "마이크를 사용할 수 없어요. 보호자와 음성 권한을 확인해 주세요."
                } else "지금은 목소리를 듣기 어려워요. 버튼으로 이어갈 수 있어요.")
            }
        }
        run.listener = listener
        run.timeout = Runnable {
            if (isListening(run)) { closeListening(run); retryListening(token, unknown = false) }
        }.also { main.postDelayed(it, LISTEN_TIMEOUT_MS) }
        publishState("starting")
        if (!isListening(run)) return
        try {
            engine.addListener(listener)
            engine.request()
        } catch (_: Exception) {
            if (isListening(run)) stopWithError("지금은 목소리를 듣기 어려워요. 버튼으로 이어갈 수 있어요.")
        }
    }

    private fun acceptText(run: ListeningRun, text: String) {
        if (!isListening(run)) return
        closeListening(run)
        if (!valid(run.epoch) || !enabled) return
        val utteranceReceiver = onUtterance
        if (utteranceReceiver != null) {
            failureCount = 0
            commandGuideShown = false
            // Keep the completed utterance local to this callback. Do not store it or log it.
            // Deliver before disarming so a synchronous host can handle rest/finish commands.
            val utterance = text.trim().replace(Regex("\\s+"), " ")
            if (utterance.isNotEmpty()) runCatching { utteranceReceiver(utterance) }
            // The host can explicitly speak a reply and enable another listening turn.
            // If it chose not to reply, leave room for observation instead of polling forever.
            if (valid(run.epoch) && speech == null && pendingSpeech == null && listening == null) {
                endQuietly()
            }
            return
        }
        val command = parseMissionCommand(text)
        if (command == null) { retryListening(run.epoch, unknown = true); return }
        failureCount = 0
        commandGuideShown = false
        // Deliver PAUSE while the host's opt-in is still active, then always stop.
        // Otherwise an idle-state callback can disarm the host before it handles PAUSE.
        runCatching { onCommand(command) }
        if (command == MissionCommand.PAUSE) suspend() else scheduleListening(run.epoch)
    }

    private fun closeListening(run: ListeningRun) {
        if (listening !== run) return
        listening = null
        run.timeout?.let(main::removeCallbacks)
        run.endedGrace?.let(main::removeCallbacks)
        run.listener?.let { listener -> runCatching { stt?.removeListener(listener) } }
        runCatching { stt?.stop() }
        abandonFocus()
        publishState("idle")
    }

    private fun retryListening(token: Long, unknown: Boolean) {
        if (!valid(token) || !enabled) return
        if (onUtterance != null) {
            // A child may simply be looking outside. No retry, error message or voice coaching.
            endQuietly()
            return
        }
        failureCount += 1
        if (failureCount > MAX_RETRIES) {
            stopWithError("천천히 둘러봐요. 다시 말하고 싶으면 마이크를 눌러줘요.")
            return
        }
        if (unknown && !commandGuideShown) {
            commandGuideShown = true
            notifyError("찾았어, 못 찾았어, 힌트, 쉬자처럼 짧게 말해줘요.")
        }
        scheduleListening(token)
    }

    private fun endQuietly() {
        if (disposed) return
        commandsEnabled = false
        stopping = true
        try {
            cancelActive()
            publishState("quiet", force = true)
        } finally { stopping = false }
    }

    private fun takeFocus(): Boolean {
        if (focusHeld) return true
        val lease = ++focusEpoch
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (lease == focusEpoch && focusHeld && !disposed &&
                    (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                ) suspend()
            }, main)
            .build()
        focusRequest = request
        focusHeld = runCatching { audio?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        if (!focusHeld) focusRequest = null
        return focusHeld
    }

    private fun abandonFocus() {
        if (!focusHeld) return
        focusHeld = false
        ++focusEpoch
        val request = focusRequest
        focusRequest = null
        if (request != null) runCatching { audio?.abandonAudioFocusRequest(request) }
    }

    private fun clearListenTimer() {
        listenTimer?.let(main::removeCallbacks)
        listenTimer = null
    }

    private fun cancelActive() {
        ++epoch
        clearListenTimer()
        val oldPending = pendingSpeech
        pendingSpeech = null
        val oldSpeech = speech
        val oldListening = listening
        speech = null
        listening = null
        oldSpeech?.timeout?.let(main::removeCallbacks)
        oldSpeech?.listener?.let { listener -> runCatching { tts?.removeEventListener(listener) } }
        oldListening?.timeout?.let(main::removeCallbacks)
        oldListening?.endedGrace?.let(main::removeCallbacks)
        oldListening?.listener?.let { listener -> runCatching { stt?.removeListener(listener) } }
        if (oldSpeech != null) { runCatching { tts?.stop() }; runCatching { androidTts?.stop() } }
        if (oldListening != null) runCatching { stt?.stop() }
        abandonFocus()
        // Report last, with the engines already quiet, so a host that starts the next narration
        // from this callback does not race the teardown above. The epoch is already bumped, so a
        // host that tracks its own generation can tell a replaced run from a failed one.
        oldPending?.let { settle(it, false) }
        oldSpeech?.let { settle(it, false) }
    }

    private fun stopWithError(message: String) {
        suspend()
        notifyError(message)
    }

    fun suspend() = onMain {
        if (disposed) return@onMain
        commandsEnabled = false
        stopping = true
        try { cancelActive(); publishState("idle", force = true) }
        finally { stopping = false }
    }

    fun release() = onMain {
        if (disposed) return@onMain
        commandsEnabled = false
        disposed = true
        cancelActive()
        ttsInitTimer?.let(main::removeCallbacks)
        sttInitTimer?.let(main::removeCallbacks)
        androidInitTimer?.let(main::removeCallbacks)
        ttsInitializing = false
        sttInitializing = false
        androidInitializing = false
        runCatching { tts?.release() }
        runCatching { stt?.release() }
        runCatching { androidTts?.shutdown() }
        tts = null
        stt = null
        androidTts = null
        ttsReady = false
        sttReady = false
        androidReady = false
    }

    private companion object {
        const val TTS_PERMISSION = "pleos.car.permission.TTS_SERVICE"
        const val STT_PERMISSION = "pleos.car.permission.STT_SERVICE"
        const val INIT_TIMEOUT_MS = 9000L
        const val LISTEN_DELAY_MS = 600L
        const val LISTEN_TIMEOUT_MS = 25000L
        const val MAX_RETRIES = 2
    }
}
