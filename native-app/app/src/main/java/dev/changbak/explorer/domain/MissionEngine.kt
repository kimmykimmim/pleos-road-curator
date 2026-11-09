package dev.changbak.explorer.domain

enum class Environment(val displayName: String) {
    ANY("두루 살피기"), CITY("도시"), NATURE("자연"), TUNNEL("터널"), NIGHT("밤")
}

enum class MissionKind { FIND, DETAIL, CHANGE }
enum class MissionTheme { COLOR, SHAPE, NATURE, SKY, LIGHT, CHANGE }
enum class MissionResult { START, NEXT, FOUND, SKIP, CHANGED, SAME }

data class Mission(
    val id: String,
    val label: String,
    val prompt: String,
    val hint: String,
    val kind: MissionKind,
    val theme: MissionTheme,
    val parentId: String? = null,
)

data class History(
    val id: String,
    val label: String,
    val kind: MissionKind,
    val theme: MissionTheme,
    val parentId: String?,
    val result: MissionResult,
    val round: Int,
)

data class Discovery(val label: String, val theme: MissionTheme)

data class MissionState(
    val context: Environment = Environment.ANY,
    val current: Mission? = null,
    val history: List<History> = emptyList(),
    val discoveries: List<Discovery> = emptyList(),
    val recent: List<String> = emptyList(),
    val round: Int = 0,
)

/** Offline observation prompts. A discovery records only the child's own report. */
object MissionEngine {
    private const val OPTIONAL_HINT = "보이지 않아도 괜찮아. 다른 걸 살펴봐도 돼."
    private const val DETAIL_HINT = "아직 편하게 보일 때만 살펴봐. 지나갔다면 다음 미션으로 가도 돼."
    private val allContexts = setOf(Environment.CITY, Environment.NATURE, Environment.TUNNEL, Environment.NIGHT)
    private val cityNature = setOf(Environment.CITY, Environment.NATURE)
    private val cityNight = setOf(Environment.CITY, Environment.NIGHT)
    private val cityTunnelNight = setOf(Environment.CITY, Environment.TUNNEL, Environment.NIGHT)

    private data class Definition(
        val mission: Mission,
        val contexts: Set<Environment>,
        val detail: String,
        val easy: Boolean,
    )

    private fun define(
        id: String,
        label: String,
        prompt: String,
        theme: MissionTheme,
        contexts: Set<Environment>,
        detail: String,
        easy: Boolean = false,
        kind: MissionKind = MissionKind.FIND,
    ) = Definition(Mission(id, label, prompt, OPTIONAL_HINT, kind, theme), contexts, detail, easy)

    // Keep the web prototype's wording and order so identical input histories select identical roots.
    private val definitions = listOf(
        define("notice-one", "눈에 들어온 하나", "지금 창밖에서 눈에 들어오는 것 하나를 골라볼까?", MissionTheme.SHAPE, allContexts,
            "아직 보이면, 가장 눈에 띄는 부분 하나를 살펴볼까?", easy = true),
        define("notice-color", "눈에 띄는 색", "지금 보이는 색 하나를 골라볼까? 어떤 색이어도 괜찮아.", MissionTheme.COLOR, allContexts,
            "아직 보이면, 그 색 옆에는 어떤 색이 있는지 살펴볼까?", easy = true),
        define("notice-light", "밝음과 어두움", "지금 창밖이 밝게 보이는지 어둡게 보이는지 살펴볼까? 어느 쪽이어도 괜찮아.", MissionTheme.LIGHT, allContexts,
            "지금 보이는 곳에 조금 더 밝거나 어두운 부분도 있을까?", easy = true),
        define("notice-outline", "내가 고른 모양", "지금 보이는 것 하나는 어떤 모양일까? 네가 느낀 대로 골라도 돼.", MissionTheme.SHAPE, allContexts,
            "아직 보이면, 그 모양의 끝부분도 살펴볼까?", easy = true),
        define("notice-change", "비슷할까 달라질까", "지금 창밖 모습이 비슷하게 이어지는지 달라지는지 살펴볼까? 그대로여도 괜찮아.", MissionTheme.CHANGE, allContexts,
            "지금 보이는 모습에서 눈에 들어오는 부분 하나를 골라볼까?", easy = true, kind = MissionKind.CHANGE),
        define("repeated-shapes", "이어지는 같은 모양", "비슷한 모양이 나란히 이어지는 곳이 있는지 찾아볼까?", MissionTheme.SHAPE, allContexts,
            "아직 보이면, 모양 사이에 빈틈이 있는지도 살펴볼까?"),
        define("round-shape", "둥근 모양", "동그랗거나 둥글게 휜 모양이 있는지 찾아볼까?", MissionTheme.SHAPE, allContexts,
            "아직 보이면, 둥근 모양 옆에는 어떤 모양이 있는지 살펴볼까?"),
        define("long-line", "길게 이어진 선", "길게 이어진 선이나 모양이 있는지 찾아볼까?", MissionTheme.SHAPE, allContexts,
            "아직 보이면, 곧게 이어지는지 구불구불한지 살펴볼까?"),
        define("neighbor-colors", "나란히 있는 색", "서로 다른 색이 나란히 보이는 곳이 있을까?", MissionTheme.COLOR, allContexts,
            "아직 보이면, 두 색이 만나는 부분도 살펴볼까?"),
        define("tree", "나무 한 그루", "창밖에 나무가 보이면 한 그루를 골라볼까?", MissionTheme.NATURE, cityNature,
            "아직 보이면, 가지 사이로 하늘이 보이는지 살펴볼까?"),
        define("plants-together", "모여 있는 초록", "풀이나 나무가 모여 있는 곳이 보이는지 찾아볼까?", MissionTheme.NATURE, cityNature,
            "아직 보이면, 초록빛이 모두 비슷한지 살펴볼까?"),
        define("cloud", "구름 모양", "하늘에 구름이 있는지 살펴볼까?", MissionTheme.SKY, cityNature,
            "아직 보이면, 구름 가장자리가 어떤 모양인지 살펴볼까?"),
        define("sky-gap", "틈 사이의 하늘", "건물이나 나무 사이로 하늘이 보이는 곳이 있을까?", MissionTheme.SKY, cityNature,
            "아직 보이면, 하늘이 보이는 틈은 어떤 모양인지 살펴볼까?"),
        define("hill", "높낮이가 있는 땅", "높아지거나 낮아지는 땅의 모양이 있는지 찾아볼까?", MissionTheme.NATURE, setOf(Environment.NATURE),
            "아직 보이면, 땅과 하늘이 만나는 선도 살펴볼까?"),
        define("water", "물이 보이는 곳", "강이나 호수처럼 물이 보이는 곳이 있는지 찾아볼까?", MissionTheme.NATURE, setOf(Environment.NATURE),
            "아직 보이면, 물 옆에는 무엇이 있는지 살펴볼까?"),
        define("building", "건물의 모양", "건물이 보이면 눈에 들어오는 하나를 골라볼까?", MissionTheme.SHAPE, cityNight,
            "아직 보이면, 건물 위쪽은 어떤 모양인지 살펴볼까?"),
        define("windows", "나란히 있는 창문", "창문이 나란히 있는 건물이 보이는지 찾아볼까?", MissionTheme.SHAPE, cityNight,
            "아직 보이면, 창문 사이의 모양도 살펴볼까?"),
        define("lit-window", "불이 켜진 창문", "불이 켜진 창문이 보이는지 찾아볼까?", MissionTheme.LIGHT, cityNight,
            "아직 보이면, 밝은 창문 옆에 어두운 창문도 있을까?"),
        define("lights-in-row", "이어지는 불빛", "불빛이 나란히 이어지는 곳이 있는지 찾아볼까?", MissionTheme.LIGHT, cityTunnelNight,
            "아직 보이면, 불빛 사이에 어두운 틈도 있는지 살펴볼까?"),
        define("wall-pattern", "벽에 있는 모양", "벽에 비슷한 모양이 이어지는 곳이 있는지 찾아볼까?", MissionTheme.SHAPE, setOf(Environment.TUNNEL),
            "아직 보이면, 모양 사이에 틈이 있는지도 살펴볼까?"),
        define("light-change", "밝기가 바뀌는 풍경", "지금 보이는 풍경의 밝기가 달라지는지 살펴볼까? 비슷해도 괜찮아.", MissionTheme.CHANGE, cityTunnelNight,
            "지금 보이는 곳에서 밝은 부분이나 어두운 부분 하나를 골라볼까?", kind = MissionKind.CHANGE),
        define("color-patch", "넓게 보이는 색", "한 가지 색이 넓게 보이는 곳이 있는지 찾아볼까?", MissionTheme.COLOR, cityNature,
            "아직 보이면, 그 색이 있는 곳의 가장자리도 살펴볼까?"),
    )
    private val byId = definitions.associateBy { it.mission.id }

    fun create(context: Environment = Environment.ANY) = MissionState(context = context)

    private fun detail(root: Definition) = Mission(
        id = "${root.mission.id}-detail",
        label = "조금 더 살펴보기",
        prompt = root.detail,
        hint = DETAIL_HINT,
        kind = MissionKind.DETAIL,
        theme = root.mission.theme,
        parentId = root.mission.id,
    )

    private fun knownMission(id: String?, parentId: String?): Mission? {
        byId[id]?.let { return it.mission.copy() }
        val parent = byId[parentId] ?: return null
        return if (id == "${parent.mission.id}-detail") detail(parent) else null
    }

    private fun boundedRound(value: Int) = value.coerceIn(0, Int.MAX_VALUE - 1)
    private fun shortText(value: String): String =
        if (value.codePointCount(0, value.length) <= 80) value else value.substring(0, value.offsetByCodePoints(0, 80))

    private fun snapshot(previous: MissionState): MissionState = MissionState(
        context = previous.context,
        current = knownMission(previous.current?.id, previous.current?.parentId),
        history = previous.history.takeLast(40).mapNotNull { entry ->
            knownMission(entry.id, entry.parentId)?.let { mission ->
                History(mission.id, mission.label, mission.kind, mission.theme, mission.parentId, entry.result, boundedRound(entry.round))
            }
        },
        discoveries = previous.discoveries.takeLast(30).filter { it.label.isNotEmpty() }
            .map { Discovery(shortText(it.label), it.theme) },
        recent = previous.recent.filter { byId.containsKey(it) }.takeLast(4),
        round = boundedRound(previous.round),
    )

    private fun chooseRoot(state: MissionState, result: MissionResult): Definition {
        var candidates = definitions.filter { state.context == Environment.ANY || state.context in it.contexts }
        val currentRoot = state.current?.parentId ?: state.current?.id
        val fresh = candidates.filter { it.mission.id != currentRoot && it.mission.id !in state.recent }
        if (fresh.isNotEmpty()) candidates = fresh
        if (state.history.asReversed().takeWhile { it.result == MissionResult.SKIP }.size >= 2) {
            val easy = candidates.filter { it.easy }
            if (easy.isNotEmpty()) candidates = easy
        }
        val anotherTheme = candidates.filter { it.mission.theme != state.current?.theme }
        if (anotherTheme.isNotEmpty()) candidates = anotherTheme
        // Bound the arithmetic independently of a restored session's round counter.
        val seed = state.round % 100003 * 7 + state.history.size * 3 + state.discoveries.size * 11 +
            state.context.ordinal * 5 + result.ordinal * 13
        return candidates[seed % candidates.size]
    }

    /** SAME is a valid comparison result. Other non-success actions never add discoveries. */
    fun advance(previous: MissionState, result: MissionResult = MissionResult.START): MissionState {
        var state = snapshot(previous)
        val current = state.current
        if (result == MissionResult.SAME && current?.kind != MissionKind.CHANGE) return state
        if (current != null) {
            val entry = History(current.id, current.label, current.kind, current.theme, current.parentId, result, state.round)
            state = state.copy(history = (state.history + entry).takeLast(40))
        }
        state = state.copy(round = state.round + 1)
        if ((result == MissionResult.FOUND || result == MissionResult.SAME) && current != null && current.kind != MissionKind.DETAIL) {
            return state.copy(
                discoveries = (state.discoveries + Discovery(current.label, current.theme)).takeLast(30),
                current = detail(byId.getValue(current.id)),
            )
        }
        val root = chooseRoot(state, result).mission
        return state.copy(current = root.copy(), recent = (state.recent + root.id).takeLast(4))
    }

    /** A different environment replaces the prompt without manufacturing a discovery. */
    fun setContext(previous: MissionState, context: Environment): MissionState =
        advance(snapshot(previous).copy(context = context), MissionResult.CHANGED)
}
