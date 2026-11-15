package dev.changbak.explorer.domain

enum class CuratorPhase { READY, WAITING, PREDICT, WATCH, EXPLAIN, FAMILY, PLAY, COMPARE, MEMORY, FINISHED }
enum class SeenStatus { UNKNOWN, SEEN, NOT_SEEN }
enum class Reflection(val label: String) { SAME("생각과 비슷했어"), DIFFERENT("생각과 달랐어"), UNSURE("아직 잘 모르겠어") }
enum class CuratorVisual {
    GATE, TOWER, GLASS, ISLAND, RECIPE,
    /**
     * A guardian's own place. The app has no drawing of it and must not invent one, so this is a
     * keepsake mark rather than a picture of the place itself.
     */
    FAMILY,
}
data class PlayChoice(val id: String, val label: String, val response: String, val cues: List<String> = emptyList())
data class PlaceActivity(val role: String, val prompt: String, val choices: List<PlayChoice>)
data class CuratorPlace(val id: String, val name: String, val theme: String,
    /**
     * One entry per kind of meeting. The first is the hook used on a first encounter; the rest
     * rotate on later journeys. See [Rounds.promptFor].
     */
    val rounds: List<RoundPrompt>,
    val observe: String, val explanation: String, val sourceTitle: String, val sourceUrl: String,
    val category: String = "역사", val visual: CuratorVisual = CuratorVisual.GATE, val mediaKey: String = "gate",
    val activity: PlaceActivity = PlaceActivity("문지기", "아침 인사를 골라볼래?", listOf(
        PlayChoice("welcome", "좋은 아침!", "네 인사와 함께 도시의 아침이 열렸어."))),
    /**
     * A line connecting this place to something the child already loves, said before the
     * question. Only where the connection is true: a place with nothing honest to say about
     * dinosaurs says nothing about dinosaurs.
     */
    val bridges: Map<FavouriteTopic, String> = emptyMap(),
    /** This place's side of a spoken exchange, for when the child answers out loud. */
    val talk: PlaceTalk = PlaceTalk(
        topics = emptyList(),
        echo = "“${PlaceTalk.THEIR_WORDS}”라고 했구나. 그렇게 생각할 수도 있겠다.",
        follow = "창밖에는 또 뭐가 보여?",
        unsure = "몰라도 괜찮아.",
        silence = "괜찮아. 생각만 해도 좋아.",
        closing = "이제 창밖을 봐.",
    ),
) {
    /** The first meeting's question. Later meetings go through [Rounds.promptFor]. */
    val question: String get() = Rounds.promptFor(this, 0).question
    val guesses: List<String> get() = Rounds.promptFor(this, 0).choices
}
data class PlaceApproach(val placeId: String, val etaSeconds: Int?, val passed: Boolean = false, val observable: Boolean = true,
    val nearby: Boolean = false, val arrived: Boolean = false,
    /** Which window the place is at right now, or UNKNOWN when the fixes cannot say. */
    val side: WindowSide = WindowSide.UNKNOWN)
data class CuratorFix(val routeId: String, val sequence: Long, val approaches: List<PlaceApproach>, val fresh: Boolean = true)
data class PlaceMemory(val placeId: String, val guess: String? = null, val seen: SeenStatus = SeenStatus.UNKNOWN,
    val reflection: Reflection? = null, val explained: Boolean = false,
    val activityId: String? = null, val activityLabel: String? = null,
    /** The family record was spoken here. It does not claim the child remembered anything. */
    val familyRecalled: Boolean = false,
    /**
     * How many times this place had been finished before this meeting began.
     *
     * Frozen when the place is first met, and never read again from the store. The store is
     * written the moment this meeting reaches its closing card, so anything that re-read it
     * mid-visit started describing the *next* meeting: the chip under the name jumped from
     * "2번째 만남" to "3번째 만남" while the child was still looking at the same place.
     */
    val round: Int = 0)
data class CuratorState(val phase: CuratorPhase = CuratorPhase.READY, val routeId: String? = null,
    val sequence: Long = -1, val currentId: String? = null, val etaSeconds: Int? = null,
    val visits: List<PlaceMemory> = emptyList(), val deferred: Set<String> = emptySet(), val message: String = "",
    val routeValid: Boolean = true,
    /**
     * Whether the spoken exchange at this place has already been closed off.
     *
     * [VoiceTalk] decides this — a child who said they do not know has been answered and must
     * not be asked again — but the decision was being thrown away with the rest of the reply,
     * so the microphone reopened and the child was left waiting for a turn that was over.
     */
    val askingDone: Boolean = false)
data class CuratorTurn(val state: CuratorState, val narration: String? = null)

/**
 * Provider supplies ETA to each encounter, not ETA to the final destination. No sight detection
 * claims.
 *
 * @param roundSource how many times this family has already finished this place. A family drives
 *   the same road daily, so the count decides which of the place's rounds it gets today.
 */
class RouteCurator(
    private val catalogSource: () -> List<CuratorPlace>,
    private val roundSource: (String) -> Int = { 0 },
    private val profileSource: () -> ChildProfile = { ChildProfile() },
) {
    /** A fixed catalog. The journey's own catalog grows when a guardian adds a place. */
    constructor(catalog: List<CuratorPlace>) : this({ catalog })

    private val catalog: List<CuratorPlace> get() = catalogSource()

    fun place(state: CuratorState) = catalog.firstOrNull { it.id == state.currentId }

    /** What the guardian told the app about this child. */
    private val profile: ChildProfile get() = profileSource()

    /** The journey's own sentences, in whatever language the child is spoken to in. */
    private val say: Narration get() = Narration.of(profile.language)

    /**
     * Today's round for a place, chosen by how many times the family has met it before and by
     * what this child is into.
     */
    fun promptFor(place: CuratorPlace): RoundPrompt =
        Rounds.promptFor(place, roundSource(place.id), profile)

    /**
     * The same, for a place this journey is already in the middle of.
     *
     * Uses the number frozen when the place was met rather than the store, so the question a
     * child is being asked cannot change under them part way through the visit.
     */
    fun promptFor(state: CuratorState, place: CuratorPlace): RoundPrompt =
        Rounds.promptFor(place, roundOf(state, place), profile)

    /** Which meeting this is: the frozen one if this place is in today's visits. */
    fun roundOf(state: CuratorState, place: CuratorPlace): Int =
        state.visits.lastOrNull { it.placeId == place.id }?.round ?: roundSource(place.id)

    /**
     * The whole opening, with the line connecting this place to something the child loves when
     * there is a true one to say. A favourite with nothing honest to say here says nothing.
     */
    fun openingFor(place: CuratorPlace): String {
        val bridge = bridgeFor(place)
        val ask = promptFor(place).spoken
        return if (bridge == null) ask else "$bridge $ask"
    }

    /** The line about something the child loves, or null when this place has none for them. */
    fun bridgeFor(place: CuratorPlace): String? = favouriteFor(place)?.let { place.bridges[it] }

    /**
     * Which of the child's favourite things this place is about to mention, if any.
     *
     * The screen draws a small picture of it while the line is said. That is the only reason
     * this is separate from [bridgeFor]: a picture needs to know which thing, not the sentence.
     */
    fun favouriteFor(place: CuratorPlace): FavouriteTopic? =
        profile.favourites.firstOrNull { place.bridges.containsKey(it) }

    /** Today's round for the place being met now, for the screen to draw the same one. */
    fun prompt(state: CuratorState): RoundPrompt? = place(state)?.let { promptFor(state, it) }
    fun memory(state: CuratorState) = state.visits.firstOrNull { it.placeId == state.currentId }
    fun start() = CuratorState(phase = CuratorPhase.WAITING, message = say.waiting)
    fun tick(state: CuratorState, fix: CuratorFix): CuratorTurn {
        if (!fix.fresh || (fix.routeId == state.routeId && fix.sequence <= state.sequence) || state.phase == CuratorPhase.READY || state.phase == CuratorPhase.FINISHED) return CuratorTurn(state)
        var next = state.copy(routeId = fix.routeId, sequence = fix.sequence, routeValid = true)
        val current = fix.approaches.firstOrNull { it.placeId == state.currentId }
        if (state.currentId != null && current == null && state.phase in setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH)) {
            next = next.copy(phase = CuratorPhase.WAITING, currentId = null, etaSeconds = null,
                deferred = next.deferred + state.currentId, message = say.routeChanged)
        }
        if (current != null) {
            next = next.copy(etaSeconds = current.etaSeconds?.coerceAtLeast(0))
            if (state.phase in setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH)) {
                if (current.passed || current.arrived) return explain(next, current.arrived)
                if ((current.etaSeconds in 0..30 || current.nearby) && state.phase == CuratorPhase.PREDICT) {
                    val p = place(next) ?: return CuratorTurn(next)
                    val text = if (current.observable) say.comingUp(p.name, point(current), watchLine(next, p))
                        else say.notVisible
                    return CuratorTurn(next.copy(phase = CuratorPhase.WATCH, message = text), text)
                }
            }
        }
        if (next.phase !in setOf(CuratorPhase.WAITING, CuratorPhase.MEMORY)) return CuratorTurn(next)
        val candidate = fix.approaches.filter { approach -> approach.observable && (approach.etaSeconds in 0..300 || approach.nearby) && !approach.passed && !approach.arrived &&
            approach.placeId !in next.deferred && next.visits.none { it.placeId == approach.placeId } && catalog.any { it.id == approach.placeId } }
            .minByOrNull { it.etaSeconds ?: 0 } ?: return CuratorTurn(next)
        val p = catalog.first { it.id == candidate.placeId }
        val close = candidate.nearby || candidate.etaSeconds in 0..30
        val ask = openingFor(p)
        val text = if(close) say.closeBy(p.name, point(candidate), ask, watchLine(next, p))
            else say.minutesAhead(((requireNotNull(candidate.etaSeconds) + 59)/60).coerceAtLeast(1), p.name, ask)
        return CuratorTurn(next.copy(phase = if(close) CuratorPhase.WATCH else CuratorPhase.PREDICT, currentId = p.id, etaSeconds = candidate.etaSeconds,
            // A new place is a new question, so whatever closed the last one does not carry over.
            // The meeting number is fixed here, where the meeting begins.
            visits = next.visits + PlaceMemory(p.id, round = roundSource(p.id)),
            message = text, askingDone = false), text)
    }
    /**
     * What to look for while this place is the one being asked about.
     *
     * The round owns this when it asks about something in particular, because the child has
     * just been asked where the ends of the roof go and must not then be sent to find the
     * passage underneath it. Otherwise the place's standing observation stands.
     */
    private fun watchLine(state: CuratorState, place: CuratorPlace): String =
        promptFor(state, place).watch.ifBlank { place.observe }

    /** Names the window only when the fixes are confident enough to name one. */
    private fun point(approach: PlaceApproach) = say.side(approach.side)

    /**
     * The child said something out loud while the place is still ahead.
     *
     * This is the spoken counterpart of [predict], and the difference is the point: [predict]
     * takes one of two answers that were printed on screen, while this takes whatever a child
     * in the back seat actually said and says something back to it. The exchange runs for
     * [VoiceTalk.MAX_TURNS] turns and then the window takes over, because the place is moving
     * past while this is happening.
     *
     * @param turn which turn of this place's conversation this is, counting from 1.
     */
    fun heard(state: CuratorState, text: String, turn: Int): CuratorTurn {
        if (state.phase != CuratorPhase.PREDICT) return CuratorTurn(state)
        val p = place(state) ?: return CuratorTurn(state)
        val clean = text.trim().take(160)
        // A quiz owes the answer to the first thing the child says, whatever that was. There is
        // nothing to draw out of them afterwards: they asked a question and it has an answer.
        // The round the child is answering decides where the exchange goes next, so an art
        // question is followed up about art rather than about whatever the place usually says.
        val round = promptFor(state, p)
        val spoken = if (round.reveal != null) null
            else VoiceTalk.reply(p, clean, turn, profile.language, round.field)
        val line = round.reveal ?: spoken!!.line
        val next = updateMemory(state) { it.copy(guess = it.guess ?: clean.ifBlank { null }) }
        // A reveal is its own ending, and so is a reply that stopped asking.
        val done = spoken?.askAgain == false
        return CuratorTurn(next.copy(message = line, askingDone = next.askingDone || done), line)
    }

    /** Whether the child is still being asked for something here, so the microphone reopens. */
    fun expectsAnswer(state: CuratorState, turnsTaken: Int): Boolean {
        if (state.phase != CuratorPhase.PREDICT) return state.phase in setOf(CuratorPhase.WATCH, CuratorPhase.PLAY)
        if (state.askingDone) return false
        if (place(state)?.let { promptFor(state, it).reveal } != null) return turnsTaken < 1
        return turnsTaken < VoiceTalk.MAX_TURNS
    }

    fun predict(state: CuratorState, guess: String): CuratorTurn {
        if (state.phase != CuratorPhase.PREDICT || guess.isBlank()) return CuratorTurn(state)
        val clean = guess.trim().take(160)
        val next = updateMemory(state) { it.copy(guess = clean) }
        // A quiz round owes the child the answer, whichever they picked. It is told the same way
        // either way: this is a guessing game, not a test, and nothing here is scored.
        val reveal = place(state)?.let(::promptFor)?.reveal
        val text = reveal ?: say.thoughtNoted
        return CuratorTurn(next.copy(message = text), text)
    }
    fun seen(state: CuratorState, seen: Boolean): CuratorTurn {
        if (state.phase != CuratorPhase.WATCH || !state.routeValid) return CuratorTurn(state)
        val next = updateMemory(state) { it.copy(seen = if (seen) SeenStatus.SEEN else SeenStatus.NOT_SEEN) }
        return if (seen) explain(next) else CuratorTurn(next.copy(message = say.notSeenYet), say.notSeenYet)
    }
    private fun explain(state: CuratorState, arrived: Boolean = false): CuratorTurn {
        val p = place(state) ?: return CuratorTurn(state)
        if (memory(state)?.explained == true) return CuratorTurn(state)
        val opening = if (memory(state)?.seen == SeenStatus.SEEN) say.sawIt(p.name)
            else if (arrived) say.arrived(p.name) else say.wentPast(p.name)
        val text = opening + p.explanation
        return CuratorTurn(updateMemory(state) { it.copy(explained = true) }.copy(phase = CuratorPhase.EXPLAIN, message = text), text)
    }
    /**
     * The one way out of EXPLAIN, shared by the child's button, finished narration and recovery.
     * A guardian's family record earns its own beat here, between the place's own story and the
     * pretend play, so a real shared memory is never mixed into either.
     */
    fun afterExplanation(state: CuratorState, album: FamilyAlbum = FamilyAlbum()): CuratorTurn {
        if (state.phase != CuratorPhase.EXPLAIN) return CuratorTurn(state)
        val p = place(state) ?: return CuratorTurn(state)
        val recall = if (memory(state)?.familyRecalled == true) null else album.recall(p)
        if (recall != null) {
            return CuratorTurn(updateMemory(state) { it.copy(familyRecalled = true) }
                .copy(phase = CuratorPhase.FAMILY, message = recall), recall)
        }
        return play(state, p)
    }

    /** Leaves the family beat the same way whatever the child said, or said nothing at all. */
    fun afterFamilyMemory(state: CuratorState): CuratorTurn {
        if (state.phase != CuratorPhase.FAMILY) return CuratorTurn(state)
        val p = place(state) ?: return CuratorTurn(state)
        return play(state, p)
    }

    private fun play(state: CuratorState, p: CuratorPlace): CuratorTurn {
        val text = say.pretend(p.activity.role, p.activity.prompt)
        return CuratorTurn(state.copy(phase = CuratorPhase.PLAY, message = text), text)
    }
    fun perform(state: CuratorState, choiceId: String): CuratorTurn {
        if (state.phase != CuratorPhase.PLAY) return CuratorTurn(state)
        val p = place(state) ?: return CuratorTurn(state)
        val choice = p.activity.choices.firstOrNull { it.id == choiceId } ?: return CuratorTurn(state)
        return CuratorTurn(updateMemory(state) { it.copy(activityId = choice.id, activityLabel = choice.label) }
            .copy(phase = CuratorPhase.MEMORY, message = choice.response), choice.response)
    }
    fun listenOnly(state: CuratorState): CuratorTurn {
        if (state.phase != CuratorPhase.PLAY) return CuratorTurn(state)
        return CuratorTurn(state.copy(phase = CuratorPhase.MEMORY, message = say.listenedOnly))
    }
    fun respondToPlay(state: CuratorState, text: String): CuratorTurn {
        if (state.phase != CuratorPhase.PLAY || text.isBlank() || text.length > 160) return CuratorTurn(state)
        val compact=text.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        val choice=place(state)?.activity?.choices?.firstOrNull { candidate ->
            (candidate.cues + candidate.label).any { cue -> compact.contains(cue.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")) }
        }
        return if(choice!=null) perform(state,choice.id) else ownIdea(state,text)
    }
    fun ownIdea(state: CuratorState, text: String): CuratorTurn {
        if (state.phase != CuratorPhase.PLAY || text.isBlank() || text.length > 160) return CuratorTurn(state)
        val message = say.ownIdea
        return CuratorTurn(updateMemory(state) { it.copy(activityId = "own", activityLabel = "내 말로 만든 장면") }
            .copy(phase = CuratorPhase.MEMORY, message = message), message)
    }

    fun reflect(state: CuratorState, reflection: Reflection): CuratorTurn {
        if (state.phase != CuratorPhase.COMPARE) return CuratorTurn(state)
        val text = when (reflection) {
            Reflection.SAME -> "네 생각과 이어지는 점이 있었구나. 오늘 만난 이야기로 남겨둘게."
            Reflection.DIFFERENT -> "새로운 모습을 발견했구나. 생각이 달라진 것도 좋은 여행의 기억이야."
            Reflection.UNSURE -> "지금 정하지 않아도 괜찮아. 궁금했던 곳으로 남겨둘게."
        }
        return CuratorTurn(updateMemory(state) { it.copy(reflection = reflection) }.copy(phase = CuratorPhase.MEMORY, message = text), text)
    }
    fun skip(state: CuratorState): CuratorTurn {
        val id = state.currentId ?: return CuratorTurn(state)
        return CuratorTurn(state.copy(phase = CuratorPhase.WAITING, currentId = null, etaSeconds = null,
            deferred = state.deferred + id, message = say.skipped))
    }
    fun finish(state: CuratorState) = CuratorTurn(state.copy(phase = CuratorPhase.FINISHED, message = say.finished))
    fun navigationLost(state: CuratorState) = state.copy(routeValid = false)
    private fun updateMemory(state: CuratorState, change: (PlaceMemory) -> PlaceMemory) = state.copy(
        visits = state.visits.map { if (it.placeId == state.currentId) change(it) else it })
}
