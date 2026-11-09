package dev.changbak.explorer.domain

import java.text.Normalizer

enum class ScenerySource(val label: String) {
    CLOUD("구름"), TREE("나무"), LEAVES("나뭇잎"), RIVER("강"), SEA("바다"), WATER("물"),
    SUNLIGHT("햇빛"), SKY("하늘"), FLOWER("꽃"), MOUNTAIN("산"), BUILDING("건물"),
    BIRD("새"), WIND("바람"), IMAGINATION("상상"), OTHER("풍경")
}
enum class FriendPart(val label: String) { BODY("몸"), EARS("귀"), TAIL("꼬리"), WINGS("날개"), COLOR("빛깔"), STEP("걸음") }
enum class FriendTrait(val part: FriendPart, val label: String) {
    BODY(FriendPart.BODY, "구름 몸"), LEAF_EARS(FriendPart.EARS, "잎사귀 귀"),
    SWAY_EARS(FriendPart.EARS, "살랑이는 귀"), SPARKLE_TAIL(FriendPart.TAIL, "반짝이는 꼬리"),
    LONG_TAIL(FriendPart.TAIL, "긴 꼬리"), WINGS(FriendPart.WINGS, "날개"),
    GREEN(FriendPart.COLOR, "초록빛"), BLUE(FriendPart.COLOR, "파란빛"),
    WARM_LIGHT(FriendPart.COLOR, "따뜻한 빛"), SWAY_STEP(FriendPart.STEP, "살랑이는 걸음")
}
data class TravelMemory(val id: Int, val source: ScenerySource, val detail: String, val trait: FriendTrait)
enum class FriendChoice { NONE, ADD_WINGS }
data class TravelState(
    val phase: CloudPhase = CloudPhase.WELCOME,
    val body: CloudFriend? = null,
    val memories: List<TravelMemory> = emptyList(),
    val pending: FriendChoice = FriendChoice.NONE,
    val cloudWorld: CloudWorld? = null,
)
enum class TravelOutcome { OBSERVED, CORRECTED, CHAT, UNCHANGED, REST, RESUME, HELP, FINISH, REPEAT, UNDO, IGNORED }
data class TravelTurn(val state: TravelState, val reply: String?, val outcome: TravelOutcome)

/** Local play rules. It cannot see the landscape or answer general-knowledge questions. */
object TravelEngine {
    private val sourceWords = linkedMapOf(
        "나뭇잎" to ScenerySource.LEAVES, "나무잎" to ScenerySource.LEAVES, "잎사귀" to ScenerySource.LEAVES,
        "낙엽" to ScenerySource.LEAVES, "시냇물" to ScenerySource.RIVER, "강물" to ScenerySource.RIVER,
        "개울" to ScenerySource.RIVER, "냇물" to ScenerySource.RIVER, "바닷물" to ScenerySource.SEA,
        "바다" to ScenerySource.SEA, "호수" to ScenerySource.WATER, "연못" to ScenerySource.WATER,
        "햇빛" to ScenerySource.SUNLIGHT, "햇살" to ScenerySource.SUNLIGHT, "태양" to ScenerySource.SUNLIGHT,
        "구름" to ScenerySource.CLOUD, "나무" to ScenerySource.TREE, "하늘" to ScenerySource.SKY,
        "건물" to ScenerySource.BUILDING, "바람" to ScenerySource.WIND, "참새" to ScenerySource.BIRD,
        "새" to ScenerySource.BIRD, "강" to ScenerySource.RIVER, "물" to ScenerySource.WATER,
        "꽃" to ScenerySource.FLOWER, "산" to ScenerySource.MOUNTAIN, "잎" to ScenerySource.LEAVES,
    )
    private val sourceRegex = Regex(sourceWords.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) })
    private val afterSource = Regex("^(?:$|[,.!?]|이|가|은|는|도|을|를|에|처럼|같|빛|색|모양|살랑|흔들|반짝|파랗|파란|초록|푸르|푸른|노랗|노란|붉|빨|동글|둥글|길|커|작|보|없|아니|말고|날|있|야|였|었)")
    private val negativeSource = Regex("^(?:이|가|은|는|도|을|를)?(?:아니|아냐|없|안보|보이지않)")
    private val sway = Regex("살랑|흔들|하늘하늘|팔랑|산들")
    private val sparkle = Regex("반짝|빛나|윤슬|반사")
    private val green = Regex("초록|녹색|푸릇")
    private val blue = Regex("파랗|파란|파랑|푸른|푸르|푸르스름")
    private val warm = Regex("노랗|노란|노랑|빨갛|빨간|빨강|분홍|주황|따뜻|환하|밝아|밝네")
    private val uncertainty = Regex("모르겠|생각안나")
    private val correction = Regex("아니고|아니라|말고")
    private val bodyFeatures = setOf(CloudFeature.ROUND, CloudFeature.FLUFFY, CloudFeature.LONG, CloudFeature.BIG, CloudFeature.SMALL, CloudFeature.BRIGHT)
    private data class SourceMention(val source: ScenerySource, val start: Int, val end: Int)

    fun create() = TravelState()

    fun start(previous: TravelState): TravelTurn {
        val state = snapshot(previous).copy(phase = CloudPhase.WATCHING, pending = FriendChoice.NONE)
        return TravelTurn(state, "편하게 보이는 풍경에서 떠오르는 걸 알려줘. 그 모습으로 우리 친구를 만들어 가자.", TravelOutcome.RESUME)
    }

    fun rest(previous: TravelState): TravelTurn {
        val state = snapshot(previous)
        return TravelTurn(state.copy(phase = if (state.phase == CloudPhase.KEEPSAKE) CloudPhase.KEEPSAKE else CloudPhase.RESTING, pending = FriendChoice.NONE),
            "잠깐 쉬어가자. 준비되면 다시 이야기해 줘.", TravelOutcome.REST)
    }

    fun finish(previous: TravelState) = TravelTurn(snapshot(previous).copy(phase = CloudPhase.KEEPSAKE, pending = FriendChoice.NONE),
        "오늘 네가 알려준 풍경으로 만든 친구야. 궁금한 모습이 있으면 이야기해 줘.", TravelOutcome.FINISH)

    fun help(previous: TravelState) = TravelTurn(snapshot(previous),
        "동글동글해, 나뭇잎이 살랑여처럼 한마디도 좋아. 내 귀가 어디서 왔는지도 물어볼 수 있어.", TravelOutcome.HELP)

    fun observe(previous: TravelState, text: String): TravelTurn {
        var state = snapshot(previous)
        val input = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        if (input.isBlank()) return TravelTurn(state, null, TravelOutcome.IGNORED)
        if (input.length > 240) return TravelTurn(state.copy(pending = FriendChoice.NONE), "한두 마디로 나눠 말해줘도 괜찮아.", TravelOutcome.IGNORED)
        val compact = input.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        if (compact in setOf("쉬자", "잠깐쉬자", "잠깐만", "멈춰", "그만", "그만할래", "안보여", "이제안보여", "밖이안보여", "구름이안보여", "풍경이안보여", "터널이야")) return rest(state)
        if (compact in setOf("시작", "시작하자", "다시시작", "다시할래", "계속하자", "다시보여", "이어서하자")) return start(state)
        if (compact in setOf("도착했어", "도착했어요", "도착이야", "여행끝", "오늘은끝", "끝났어", "친구보여줘", "완성해줘")) return finish(state)
        if (state.phase == CloudPhase.RESTING) return TravelTurn(state, null, TravelOutcome.IGNORED)
        if (compact in setOf("잘못말했어", "잘못눌렀어", "되돌려", "되돌려줘", "되돌리기", "방금취소", "취소")) return TravelTurn(state.copy(pending = FriendChoice.NONE), null, TravelOutcome.UNDO)
        if (compact in setOf("다시말해줘", "다시들려줘", "한번더", "다시듣기")) return TravelTurn(state, describe(state), TravelOutcome.REPEAT)

        if (state.pending == FriendChoice.ADD_WINGS) {
            if (compact in setOf("응", "응좋아", "좋아", "좋아요", "그래", "네", "붙여줘", "달아줘", "응붙여줘", "날개붙여줘")) {
                state = append(state.copy(pending = FriendChoice.NONE), ScenerySource.IMAGINATION, FriendTrait.WINGS)
                return TravelTurn(state, "파닥파닥! 우리가 함께 상상한 날개를 붙였어.", TravelOutcome.OBSERVED)
            }
            if (compact in setOf("아니", "아니야", "싫어", "안할래", "안붙일래", "하지마", "괜찮아", "그냥둘래")) {
                return TravelTurn(state.copy(pending = FriendChoice.NONE), "좋아, 지금 모습 그대로 함께 가자.", TravelOutcome.CHAT)
            }
        }
        state = state.copy(pending = FriendChoice.NONE)
        chat(state, compact, input)?.let { return it }
        if (isQuestion(input, compact)) return TravelTurn(state,
            "나는 네가 알려준 풍경과 내 모습에 대해 이야기할 수 있어. 아직 그 질문의 답은 모르겠어.", TravelOutcome.CHAT)
        if (uncertainty.containsMatchIn(compact) || compact in setOf("도와줘", "힌트", "어려워")) return help(state)
        if (state.phase != CloudPhase.WATCHING && state.phase != CloudPhase.KEEPSAKE) return TravelTurn(state, null, TravelOutcome.IGNORED)
        if (compact in setOf("날개달아줘", "날개붙여줘", "날개만들어줘", "날개를달아줘", "날개를붙여줘", "날개를만들어줘", "날개가있으면좋겠어", "날개가있었으면좋겠어")) {
            val existing = current(state, FriendPart.WINGS)
            if (existing != null) return TravelTurn(state, "이미 파닥이는 날개가 있어. ${origin(existing)}", TravelOutcome.UNCHANGED)
            return TravelTurn(append(state, ScenerySource.IMAGINATION, FriendTrait.WINGS),
                "파닥파닥! 네가 원한 상상 속 날개를 붙였어.", TravelOutcome.OBSERVED)
        }
        correctSource(state, input)?.let { return it }
        if (compact in setOf("그대로야", "아직그대로야", "똑같아", "비슷해", "달라진거없어", "변한게없어")) {
            return TravelTurn(state, "그대로여도 좋아. 지금 모습으로 함께 가자.", TravelOutcome.UNCHANGED)
        }

        // Naming never turns the chosen name into an observed tree, bird, or river.
        if (Regex("(?:이름|별명)(?:은|을)?").containsMatchIn(compact)) {
            val cloud = CloudEngine.observe(CloudState(CloudPhase.WATCHING, state.body), input)
            if (cloud.state.friend?.nickname != null) {
                val next = state.copy(body = cloud.state.friend?.copy(features = cloud.state.friend.features.filter { it in bodyFeatures }))
                return TravelTurn(next, cloud.reply, if (next.body == state.body) TravelOutcome.UNCHANGED else TravelOutcome.OBSERVED)
            }
        }

        var next = state
        var recognized = false
        val added = mutableListOf<FriendTrait>()
        val clauses = input.split(Regex("(?:그리고|그다음|그런데|근데|[,，.!\\n]|하고\\s*|고\\s+|이랑|랑\\s+)")).filter(String::isNotBlank)
        for (clause in clauses) {
            val mentions = findSources(clause)
            val kinds = mentions.map { it.source }.distinct()
            val nonCloud = kinds.filter { it != ScenerySource.CLOUD }
            val cloudTurn = CloudEngine.observe(CloudState(CloudPhase.WATCHING, next.body), clause)
            val candidate = cloudTurn.state.friend
            val comparison = Regex("같아|같네|처럼|모양|같다").containsMatchIn(clause)
            val implicitShape = comparison && candidate != null && candidate.form != CloudForm.CLOUD &&
                !Regex("봤|보았|발견|있어|보여").containsMatchIn(clause)
            val bareShape = Regex("^(?:저건|이건|이제)?(?:고래|토끼|강아지|고양이|용|거북이|공룡|빵|배|하트|새)(?:야|이야|네|이네|다|$)").matches(clause.replace(Regex("\\s+"), ""))
            val bodyContext = ScenerySource.CLOUD in kinds || clause.contains("구름") || implicitShape ||
                (nonCloud.isEmpty() && (comparison || bareShape || candidate?.features?.any { it in bodyFeatures } == true))
            // The noun being compared ('새 같아') is not evidence that a real bird was seen.
            val explicitOrigin = mentions.firstOrNull {
                it.source == ScenerySource.CLOUD || !Regex("^\\s*(?:같|처럼|모양)").containsMatchIn(clause.substring(it.end))
            }?.source
            val bodyOrigin = explicitOrigin ?: current(next, FriendPart.BODY)?.source ?: ScenerySource.IMAGINATION
            if (bodyContext && candidate != null && cloudTurn.outcome in setOf(CloudOutcome.OBSERVED, CloudOutcome.UNCHANGED)) {
                val body = candidate.copy(features = candidate.features.filter { it in bodyFeatures })
                val shapeWasNamed = candidate.form != next.body?.form || candidate.label != next.body?.label ||
                    body.features != next.body?.features || ScenerySource.CLOUD in kinds || comparison
                if (shapeWasNamed) {
                    next = next.copy(body = body)
                    val before = next.memories
                    next = append(next, bodyOrigin, FriendTrait.BODY)
                    if (next.memories != before) added.add(FriendTrait.BODY)
                    recognized = true
                }
                if (CloudFeature.LONG_TAIL in candidate.features && Regex("꼬리").containsMatchIn(clause)) {
                    val before = next.memories; next = append(next, bodyOrigin, FriendTrait.LONG_TAIL)
                    if (before != next.memories) added.add(FriendTrait.LONG_TAIL)
                    recognized = true
                }
                if (CloudFeature.WINGS in candidate.features && clause.contains("날개")) {
                    val before = next.memories; next = append(next, bodyOrigin, FriendTrait.WINGS)
                    if (before != next.memories) added.add(FriendTrait.WINGS)
                    recognized = true
                }
            }
            if (implicitShape) continue
            for (mention in mentions) {
                if (mention.source == ScenerySource.CLOUD) continue
                val following = mentions.firstOrNull { it.start > mention.start }
                val local = clause.substring(mention.start, following?.start ?: clause.length)
                val context = if (mentions.size == 1) clause else local
                val compactContext = context.replace(Regex("\\s+"), "")
                val after = clause.substring(mention.end).replace(Regex("\\s+"), "")
                if (negativeSource.containsMatchIn(after) || Regex("(?:살랑|흔들|반짝|파랗|초록).{0,3}(?:않|아니)").containsMatchIn(compactContext)) continue
                val traits = mutableListOf<FriendTrait>()
                when (mention.source) {
                    ScenerySource.TREE, ScenerySource.LEAVES -> {
                        traits.add(if (sway.containsMatchIn(context)) FriendTrait.SWAY_EARS else FriendTrait.LEAF_EARS)
                        if (sway.containsMatchIn(context)) traits.add(FriendTrait.SWAY_STEP)
                    }
                    ScenerySource.RIVER, ScenerySource.SEA, ScenerySource.WATER -> {
                        val waterLight = if (ScenerySource.SUNLIGHT in kinds) clause else context
                        if (sparkle.containsMatchIn(waterLight)) traits.add(FriendTrait.SPARKLE_TAIL)
                    }
                    ScenerySource.SUNLIGHT -> traits.add(FriendTrait.WARM_LIGHT)
                    ScenerySource.BIRD -> traits.add(FriendTrait.WINGS)
                    ScenerySource.WIND -> traits.add(FriendTrait.SWAY_STEP)
                    else -> Unit
                }
                when {
                    green.containsMatchIn(context) -> traits.add(FriendTrait.GREEN)
                    blue.containsMatchIn(context) -> traits.add(FriendTrait.BLUE)
                    warm.containsMatchIn(context) -> traits.add(FriendTrait.WARM_LIGHT)
                }
                for (trait in traits.distinct()) {
                    val before = next.memories; next = append(next, mention.source, trait)
                    if (next.memories != before) added.add(trait)
                    recognized = true
                }
            }
        }
        if (next != state) return TravelTurn(next, observationReply(next, added.distinct()), TravelOutcome.OBSERVED)
        if (recognized) return TravelTurn(state, "응, 그 모습은 우리 친구에게 이미 담겨 있어.", TravelOutcome.UNCHANGED)
        return TravelTurn(state, "아직 그 말은 잘 알아듣지 못했어. 보인 풍경과 느낌을 짧게 말해줘도 좋아.", TravelOutcome.IGNORED)
    }

    private fun isQuestion(input: String, compact: String) = input.contains('?') || input.contains('？') ||
        Regex("왜|어디|누구|어떻게|무슨|무엇|뭐|뭘|언제|얼마|몇|할까|일까|수있|수없").containsMatchIn(compact)

    private fun chat(state: TravelState, compact: String, input: String): TravelTurn? {
        fun answer(value: String) = TravelTurn(state, value, TravelOutcome.CHAT)
        if (compact in setOf("안녕", "안녕하세요", "반가워", "반가워요")) return answer("안녕! 네가 알려준 풍경으로 함께 자라는 여행 친구야.")
        if (compact.contains("이름") && (isQuestion(input, compact) || compact.contains("알려줘"))) {
            return answer(state.body?.nickname?.let { "네가 붙여준 내 이름은 ${it}야." }
                ?: "아직 정한 이름은 없어. 마음에 드는 이름을 붙여줘도 좋아.")
        }
        if (compact.contains("누구") || compact in setOf("너는뭐야", "넌뭐야")) return answer("나는 네가 알려준 풍경과 상상으로 자라는 여행 친구야.")
        if (compact.contains("뭘봤") || compact.contains("무엇을봤") || compact.contains("어떤걸봤") ||
            (compact.contains("기억") && (compact.contains("우리가본") || compact.contains("내가본") || compact.contains("내가말한")))) return answer(recall(state))
        if (Regex("날수있|날아갈수있|날아다닐수있|날수없").containsMatchIn(compact)) {
            val wings = current(state, FriendPart.WINGS)
            if (wings != null) return answer("상상 속에서는 파닥파닥 날 수 있어. ${origin(wings)}")
            return TravelTurn(state.copy(pending = FriendChoice.ADD_WINGS), "상상 속 날개를 붙이면 날 수 있을 것 같아. 날개를 붙일까?", TravelOutcome.CHAT)
        }
        if (compact.contains("어떻게걸") || compact.contains("어떻게움직")) {
            val step = current(state, FriendPart.STEP)
            return answer(if (step == null) "아직 걸음에 담을 움직임은 듣지 못했어." else "나는 살랑살랑 걸어. ${origin(step)}")
        }
        if (compact.contains("어떻게생겼") || compact.contains("어떤모습") || compact.contains("모습알려")) return answer(describe(state))
        val part = when {
            compact.contains("꼬리") -> FriendPart.TAIL
            compact.contains("날개") -> FriendPart.WINGS
            compact.contains("귀") -> FriendPart.EARS
            compact.contains("몸") -> FriendPart.BODY
            compact.contains("빛깔") || compact.contains("색") || compact.contains("빛") -> FriendPart.COLOR
            compact.contains("걸음") -> FriendPart.STEP
            else -> null
        }
        if (part != null && (isQuestion(input, compact) || compact.contains("알려줘"))) {
            val memory = current(state, part)
            return answer(if (memory == null) "아직 ${part.label}에 담을 풍경은 듣지 못했어."
                else "지금 내 ${part.label}은 ${traitDescription(memory, state)}. ${origin(memory)}")
        }
        if (compact in setOf("응", "네", "좋아", "그래", "아니", "아니야")) return answer("응, 네 이야기를 듣고 있어.")
        return null
    }

    private fun correctSource(state: TravelState, input: String): TravelTurn? {
        val marker = correction.find(input) ?: return null
        val before = findSources(input.substring(0, marker.range.first)).lastOrNull()?.source ?: return null
        val after = findSources(input.substring(marker.range.last + 1)).firstOrNull()?.source ?: return null
        val requestedPart = when { input.contains("꼬리") -> FriendPart.TAIL; input.contains("귀") -> FriendPart.EARS; input.contains("날개") -> FriendPart.WINGS; else -> null }
        val index = state.memories.indexOfLast { it.source == before && (requestedPart == null || it.trait.part == requestedPart) }
        if (index < 0) return TravelTurn(state, "내 기억에는 ${before.label}에서 온 모습이 아직 없어. 기억을 새로 만들지는 않을게.", TravelOutcome.CHAT)
        val old = state.memories[index]
        val changed = old.copy(source = after, detail = detail(after, old.trait, state.body))
        if (changed == old) return TravelTurn(state, "응, 이미 ${after.label}에서 온 모습으로 알고 있어.", TravelOutcome.UNCHANGED)
        val memories = state.memories.mapIndexed { i, memory -> if (i == index) changed else memory }
        return TravelTurn(state.copy(memories = memories), "알려줘서 고마워. ${old.trait.part.label}은 ${after.label}에서 온 모습으로 고쳤어.", TravelOutcome.CORRECTED)
    }

    private fun findSources(text: String): List<SourceMention> = sourceRegex.findAll(text).mapNotNull { match ->
        val word = match.value
        val before = text.substring(0, match.range.first)
        val after = text.substring(match.range.last + 1)
        if (!afterSource.containsMatchIn(after.trimStart())) return@mapNotNull null
        if (word.length == 1 && before.isNotEmpty() && before.last().isLetterOrDigit()) {
            val compact = before.replace(Regex("\\s+"), "")
            if (listOf("그거", "저거", "이거", "저기", "여기", "거기", "밖에", "옆에", "아기", "커다란", "작은", "반짝이는", "파란", "초록", "빨간", "노란", "분홍").none(compact::endsWith)) return@mapNotNull null
        }
        SourceMention(sourceWords.getValue(word), match.range.first, match.range.last + 1)
    }.toList()

    private fun current(state: TravelState, part: FriendPart) = state.memories.lastOrNull { it.trait.part == part }

    private fun append(previous: TravelState, source: ScenerySource, trait: FriendTrait): TravelState {
        var state = previous
        val detail = detail(source, trait, state.body)
        val old = current(state, trait.part)
        if (old?.source == source && old.trait == trait && old.detail == detail) return state
        if ((state.memories.maxOfOrNull { it.id } ?: 0) >= Int.MAX_VALUE - 1) state = state.copy(memories = state.memories.mapIndexed { i, memory -> memory.copy(id = i + 1) })
        val memory = TravelMemory((state.memories.maxOfOrNull { it.id } ?: 0) + 1, source, detail, trait)
        return state.copy(memories = bounded(state.memories + memory))
    }

    private fun bounded(memories: List<TravelMemory>): List<TravelMemory> {
        if (memories.size <= 24) return memories.toList()
        val keep = FriendPart.entries.mapNotNull { part -> memories.indexOfLast { it.trait.part == part }.takeIf { it >= 0 } }.toMutableSet()
        for (index in memories.indices.reversed()) { if (keep.size >= 24) break; keep.add(index) }
        return memories.filterIndexed { index, _ -> index in keep }
    }

    private fun snapshot(state: TravelState): TravelState {
        val memories = bounded(state.memories.map { it.copy(detail = it.detail.replace(Regex("\\p{C}"), "").take(64)) })
        val unique = if (memories.any { it.id <= 0 } || memories.map { it.id }.distinct().size != memories.size)
            memories.mapIndexed { i, memory -> memory.copy(id = i + 1) } else memories
        val body = state.body?.copy(label = state.body.label.take(12), nickname = state.body.nickname?.take(12), features = state.body.features.filter { it in bodyFeatures }.distinct())
        return state.copy(body = body, memories = unique)
    }

    private fun detail(source: ScenerySource, trait: FriendTrait, body: CloudFriend?): String {
        val place = source.label
        return when (trait) {
            FriendTrait.BODY -> "$place: ${body?.features?.takeLast(2)?.joinToString(" ") { it.label }.orEmpty()} ${body?.label ?: "몸"} 모양".replace(Regex("\\s+"), " ")
            FriendTrait.LEAF_EARS -> "${place}에서 떠올린 잎사귀 모양"
            FriendTrait.SWAY_EARS, FriendTrait.SWAY_STEP -> "${place}의 살랑이는 움직임"
            FriendTrait.SPARKLE_TAIL -> "${place}에서 반짝이는 빛"
            FriendTrait.LONG_TAIL -> "${place}에서 떠올린 길쭉한 모양"
            FriendTrait.WINGS -> if (source == ScenerySource.IMAGINATION) "함께 상상해서 붙인 날개" else "${place}에서 떠올린 날개"
            FriendTrait.GREEN -> "${place}의 초록빛"
            FriendTrait.BLUE -> "${place}의 파란빛"
            FriendTrait.WARM_LIGHT -> "${place}에서 떠올린 따뜻한 빛"
        }.take(64)
    }

    private fun origin(memory: TravelMemory) = if (memory.source == ScenerySource.IMAGINATION) "우리가 함께 상상해서 붙인 모습이야."
        else "네가 알려준 ${memory.detail}에서 떠올렸어."

    private fun traitDescription(memory: TravelMemory, state: TravelState) = when (memory.trait) {
        FriendTrait.BODY -> state.body?.let { "${it.features.takeLast(1).joinToString { f -> f.label }} ${it.label} 모습이야" }?.trim() ?: "아직 정하지 않은 모습이야"
        FriendTrait.LEAF_EARS -> "잎사귀 모양이야"
        FriendTrait.SWAY_EARS -> "살랑살랑 움직여"
        FriendTrait.SPARKLE_TAIL -> "반짝반짝 빛나"
        FriendTrait.LONG_TAIL -> "길쭉해"
        FriendTrait.WINGS -> "파닥이는 상상 속 날개야"
        FriendTrait.GREEN -> "초록빛이야"
        FriendTrait.BLUE -> "파란빛이야"
        FriendTrait.WARM_LIGHT -> "따뜻한 빛이야"
        FriendTrait.SWAY_STEP -> "살랑살랑 움직여"
    }

    private fun recall(state: TravelState): String {
        val sources = state.memories.filter { it.source != ScenerySource.IMAGINATION }.map { it.source.label }.distinct().takeLast(5)
        if (sources.isEmpty()) return if (state.memories.any { it.source == ScenerySource.IMAGINATION }) "아직 본 풍경은 듣지 못했어. 우리가 함께 상상한 모습은 기억하고 있어."
            else "아직 네가 본 풍경을 들은 기억은 없어."
        return "${sources.joinToString(", ")}. 네가 알려준 풍경들이야."
    }

    private fun describe(state: TravelState): String {
        val parts = mutableListOf<String>()
        if (current(state, FriendPart.BODY) != null) state.body?.let { parts.add("${it.features.takeLast(1).joinToString { f -> f.label }} ${it.label} 모양의 몸".trim()) }
        listOf(FriendPart.EARS, FriendPart.TAIL, FriendPart.WINGS, FriendPart.COLOR, FriendPart.STEP).forEach { part -> current(state, part)?.let { parts.add(it.trait.label) } }
        if (parts.isEmpty()) return "아직 네가 알려준 풍경으로 정한 모습은 없어."
        return "지금 내 모습은 ${parts.take(4).joinToString(", ")}이야. 모두 네가 알려준 풍경과 상상에서 왔어."
    }

    private fun observationReply(state: TravelState, traits: List<FriendTrait>): String {
        val newest = traits.takeLast(3).map { when (it) {
            FriendTrait.BODY -> "${state.body?.label ?: "구름 친구"} 모양의 몸"
            else -> it.label
        } }
        return if (newest.isEmpty()) "네가 알려준 모습으로 친구가 달라졌어."
        else "짜잔, ${newest.joinToString(", ")}! 네가 알려준 풍경이 내 모습이 됐어."
    }
}
