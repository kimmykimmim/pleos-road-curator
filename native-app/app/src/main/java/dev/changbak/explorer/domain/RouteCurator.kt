package dev.changbak.explorer.domain

enum class CuratorPhase { READY, WAITING, PREDICT, WATCH, EXPLAIN, FAMILY, PLAY, COMPARE, MEMORY, FINISHED }
enum class SeenStatus { UNKNOWN, SEEN, NOT_SEEN }
enum class Reflection(val label: String) { SAME("생각과 비슷했어"), DIFFERENT("생각과 달랐어"), UNSURE("아직 잘 모르겠어") }
enum class CuratorVisual { GATE, TOWER, GLASS, ISLAND, RECIPE }
data class PlayChoice(val id: String, val label: String, val response: String, val cues: List<String> = emptyList())
data class PlaceActivity(val role: String, val prompt: String, val choices: List<PlayChoice>)
data class CuratorPlace(val id: String, val name: String, val theme: String, val question: String,
    val guesses: List<String>, val observe: String, val explanation: String, val sourceTitle: String, val sourceUrl: String,
    val category: String = "역사", val visual: CuratorVisual = CuratorVisual.GATE, val mediaKey: String = "gate",
    val activity: PlaceActivity = PlaceActivity("문지기", "아침 인사를 골라볼래?", listOf(
        PlayChoice("welcome", "좋은 아침!", "네 인사와 함께 도시의 아침이 열렸어."))))
data class PlaceApproach(val placeId: String, val etaSeconds: Int?, val passed: Boolean = false, val observable: Boolean = true,
    val nearby: Boolean = false, val arrived: Boolean = false)
data class CuratorFix(val routeId: String, val sequence: Long, val approaches: List<PlaceApproach>, val fresh: Boolean = true)
data class PlaceMemory(val placeId: String, val guess: String? = null, val seen: SeenStatus = SeenStatus.UNKNOWN,
    val reflection: Reflection? = null, val explained: Boolean = false,
    val activityId: String? = null, val activityLabel: String? = null,
    /** The family record was spoken here. It does not claim the child remembered anything. */
    val familyRecalled: Boolean = false)
data class CuratorState(val phase: CuratorPhase = CuratorPhase.READY, val routeId: String? = null,
    val sequence: Long = -1, val currentId: String? = null, val etaSeconds: Int? = null,
    val visits: List<PlaceMemory> = emptyList(), val deferred: Set<String> = emptySet(), val message: String = "",
    val routeValid: Boolean = true)
data class CuratorTurn(val state: CuratorState, val narration: String? = null)

/** Provider supplies ETA to each encounter, not ETA to the final destination. No sight detection claims. */
class RouteCurator(private val catalog: List<CuratorPlace>) {
    fun place(state: CuratorState) = catalog.firstOrNull { it.id == state.currentId }
    fun memory(state: CuratorState) = state.visits.firstOrNull { it.placeId == state.currentId }
    fun start() = CuratorState(phase = CuratorPhase.WAITING, message = "가는 길에서 만날 이야기를 기다리고 있어.")
    fun tick(state: CuratorState, fix: CuratorFix): CuratorTurn {
        if (!fix.fresh || (fix.routeId == state.routeId && fix.sequence <= state.sequence) || state.phase == CuratorPhase.READY || state.phase == CuratorPhase.FINISHED) return CuratorTurn(state)
        var next = state.copy(routeId = fix.routeId, sequence = fix.sequence, routeValid = true)
        val current = fix.approaches.firstOrNull { it.placeId == state.currentId }
        if (state.currentId != null && current == null && state.phase in setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH)) {
            next = next.copy(phase = CuratorPhase.WAITING, currentId = null, etaSeconds = null,
                deferred = next.deferred + state.currentId, message = "길이 바뀌었어. 새 길에서 만날 곳을 다시 살펴볼게.")
        }
        if (current != null) {
            next = next.copy(etaSeconds = current.etaSeconds?.coerceAtLeast(0))
            if (state.phase in setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH)) {
                if (current.passed || current.arrived) return explain(next, current.arrived)
                if ((current.etaSeconds in 0..30 || current.nearby) && state.phase == CuratorPhase.PREDICT) {
                    val p = place(next) ?: return CuratorTurn(next)
                    val text = if (current.observable) "곧 ${p.name} 근처야. ${p.observe} 안 보이면 그냥 지나가도 좋아."
                        else "지금은 보기 어려운 구간이야. 지나간 뒤 이야기로 만나자."
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
        val text = if(close) "${p.name} 근처야. ${p.question} ${p.observe} 안 보이면 듣기만 해도 좋아."
            else "약 ${((requireNotNull(candidate.etaSeconds) + 59)/60).coerceAtLeast(1)}분 뒤에 ${p.name} 근처를 지나갈 거야. ${p.question}"
        return CuratorTurn(next.copy(phase = if(close) CuratorPhase.WATCH else CuratorPhase.PREDICT, currentId = p.id, etaSeconds = candidate.etaSeconds,
            visits = next.visits + PlaceMemory(p.id), message = text), text)
    }
    fun predict(state: CuratorState, guess: String): CuratorTurn {
        if (state.phase != CuratorPhase.PREDICT || guess.isBlank()) return CuratorTurn(state)
        val clean = guess.trim().take(160)
        val next = updateMemory(state) { it.copy(guess = clean) }
        val text = "그렇게 생각했구나. 가까이 가면 직접 살펴보자."
        return CuratorTurn(next.copy(message = text), text)
    }
    fun seen(state: CuratorState, seen: Boolean): CuratorTurn {
        if (state.phase != CuratorPhase.WATCH || !state.routeValid) return CuratorTurn(state)
        val next = updateMemory(state) { it.copy(seen = if (seen) SeenStatus.SEEN else SeenStatus.NOT_SEEN) }
        return if (seen) explain(next) else CuratorTurn(next.copy(message = "괜찮아. 지나가면 이야기로 들려줄게."), "괜찮아. 지나가면 이야기로 들려줄게.")
    }
    private fun explain(state: CuratorState, arrived: Boolean = false): CuratorTurn {
        val p = place(state) ?: return CuratorTurn(state)
        if (memory(state)?.explained == true) return CuratorTurn(state)
        val text = (if (memory(state)?.seen == SeenStatus.SEEN) "네가 본 ${p.name} 이야기야. " else if(arrived) "${p.name}에 도착했어. 보지 못했어도 괜찮아. " else "${p.name} 근처를 지났어. 보지 못했어도 괜찮아. ") + p.explanation
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
        val text = "이제 우리가 상상해 보자. 너는 ${p.activity.role}야! ${p.activity.prompt}"
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
        return CuratorTurn(state.copy(phase = CuratorPhase.MEMORY, message = "이번에는 이야기로 만났어. 편하게 다음 풍경을 기다려도 좋아."))
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
        val message = "네 생각으로 장면을 완성했어. 이제 편하게 다음 풍경을 만나러 가자."
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
            deferred = state.deferred + id, message = "좋아. 편하게 밖을 보고 있어도 돼."))
    }
    fun finish(state: CuratorState) = CuratorTurn(state.copy(phase = CuratorPhase.FINISHED, message = "오늘 길에서 만난 이야기를 돌아볼까?"))
    fun navigationLost(state: CuratorState) = state.copy(routeValid = false)
    private fun updateMemory(state: CuratorState, change: (PlaceMemory) -> PlaceMemory) = state.copy(
        visits = state.visits.map { if (it.placeId == state.currentId) change(it) else it })
}
