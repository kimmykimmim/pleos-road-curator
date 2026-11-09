package dev.changbak.explorer.domain

import java.text.Normalizer

enum class CloudPhase { WELCOME, WATCHING, RESTING, KEEPSAKE }

enum class CloudForm(val label: String) {
    CLOUD("구름 친구"), WHALE("고래"), DRAGON("용"), RABBIT("토끼"), BIRD("새"),
    DOG("강아지"), CAT("고양이"), TURTLE("거북이"), DINOSAUR("공룡"),
    BREAD("빵"), SHIP("배"), HEART("하트"), OTHER("내가 본 모습")
}

enum class CloudFeature(val label: String) {
    LONG_TAIL("긴 꼬리"), WINGS("날개"), ROUND("동글동글"), FLUFFY("몽실몽실"),
    LONG("길쭉길쭉"), BIG("커다란"), SMALL("작은"), BRIGHT("반짝반짝")
}

data class CloudFriend(
    val form: CloudForm = CloudForm.CLOUD,
    val label: String = "구름 친구",
    val nickname: String? = null,
    val features: List<CloudFeature> = emptyList(),
)

data class CloudMemory(val friend: CloudFriend)
data class CloudState(
    val phase: CloudPhase = CloudPhase.WELCOME,
    val friend: CloudFriend? = null,
    val memories: List<CloudMemory> = emptyList(),
)

enum class CloudOutcome { OBSERVED, UNCHANGED, REST, RESUME, HELP, FINISH, REPEAT, UNDO, IGNORED }
data class CloudTurn(val state: CloudState, val reply: String?, val outcome: CloudOutcome)

/**
 * An offline play-state engine, not a camera or a general-language model.
 * Only a small shape/name/feature vocabulary is persisted, never the child's utterance.
 * The host owns microphone consent, waiting, lifecycle, and its stack of states for undo.
 */
object CloudEngine {
    private val aliases = linkedMapOf(
        "드래곤" to CloudForm.DRAGON, "강아지" to CloudForm.DOG, "멍멍이" to CloudForm.DOG,
        "고양이" to CloudForm.CAT, "야옹이" to CloudForm.CAT, "거북이" to CloudForm.TURTLE,
        "거북" to CloudForm.TURTLE, "공룡" to CloudForm.DINOSAUR, "토끼" to CloudForm.RABBIT,
        "고래" to CloudForm.WHALE, "참새" to CloudForm.BIRD, "독수리" to CloudForm.BIRD,
        "식빵" to CloudForm.BREAD, "우주선" to CloudForm.SHIP, "비행선" to CloudForm.SHIP,
        "하트" to CloudForm.HEART, "구름" to CloudForm.CLOUD,
        "용" to CloudForm.DRAGON, "새" to CloudForm.BIRD, "개" to CloudForm.DOG,
        "빵" to CloudForm.BREAD, "배" to CloudForm.SHIP,
    )
    private val correction = Regex("(?:아니고|아니라|말고|아니야|아니에요|아냐)[,\\s]*")
    private val introduction = Regex("^(?:저구름(?:은|이|도)?|이구름(?:은|이|도)?|구름(?:은|이|도)?|새(?:로운)?친구(?:는|가)?|이번에는|이번엔|이제는|지금은|이제|지금|저거는|이거는|저건|이건|저거|이거|그건|꼭|마치|조금|엄청)")
    private val formDecorator = Regex("^(?:날개(?:가)?(?:달린|있는|없는)|긴꼬리(?:가)?(?:달린|있는)|긴꼬리의|꼬리(?:가)?긴|길쭉한|동그란|둥근|몽실몽실한|커다란|작은|반짝이는|큰)")
    private val unknownComparison = Regex("([가-힣A-Za-z]{1,32})\\s*(?:처럼|같(?:아요|아|네|다)|모양)")
    private val badNouns = setOf("뭐", "무엇", "그거", "이거", "저거", "그것", "이것", "저것", "똑같", "그대로", "비슷", "모양", "느낌", "것", "거", "아닌것", "모르겠", "아무것", "없는것")
    private val nicknamePattern = Regex("(?:이름(?:은|을)?|별명(?:은|을)?)\\s*([가-힣A-Za-z0-9]{1,20}?)(?:이라고|라고|이야|야|이에요|예요|으로|로|할래|할게|해줘|하자|$)")
    private val negativeAfter = Regex("^(?:(?:은|는|이|가|도|을|를)\\s*)?(?:아니|아냐|아닌|말고|없|같(?:지|진|지는)\\s*않|처럼\\s*보이(?:지|진|지는)\\s*않|안\\s*(?:같|보|닮))")
    private val formTail = Regex("^(?:$|\\s|[,.!?]|같|처럼|모양|은|는|이|가|야|네|다|도|을|를|랑|아니|아냐|말고|으로|로)")
    private val tailLong = Regex("(?:긴|길쭉한)꼬리|꼬리(?:가|는|도)?(?:아주|엄청|정말|더|조금|좀)?(?:길쭉|길|긴|늘어)")
    private val tailGone = Regex("꼬리(?:가|는|도)?(?:아주|엄청|정말|더|조금|좀)?(?:없|짧|안길|안보여|보이지않|안보인다)")
    private val featurePatterns = listOf(
        CloudFeature.WINGS to Regex("날개"),
        CloudFeature.ROUND to Regex("동글|동그랗|동그라|동그란|동그레|둥글|둥그랗|둥그렇|둥그레|둥근|원형"),
        CloudFeature.FLUFFY to Regex("푹신|폭신|몽실|복슬|보송"),
        CloudFeature.LONG to Regex("길쭉|길(?=어|다|게|고|지)|긴모양|기다랗|늘어났|늘어졌"),
        CloudFeature.BIG to Regex("커다란|커다랗|큰(?=구름|모양)|크(?=다|네|고|게|지|잖)|커(?=졌|요|보|$)"),
        CloudFeature.SMALL to Regex("작(?=아|다|네|고|게|지|은|$)|조그맣|조그만|쪼그라|쪼그매"),
        CloudFeature.BRIGHT to Regex("반짝|밝(?=아|다|네|고|게|지)|눈부시|빛나"),
    )

    fun create() = CloudState()

    fun start(previous: CloudState): CloudTurn {
        val state = snapshot(previous).copy(phase = CloudPhase.WATCHING)
        return CloudTurn(state,
            if (state.friend == null) "편하게 보이는 구름에서 떠오르는 게 있으면 알려줘. 한마디여도 괜찮아."
            else "지금 보이는 구름부터 이어가자. 새롭게 눈에 들어오는 게 있으면 알려줘.",
            CloudOutcome.RESUME)
    }

    fun rest(previous: CloudState) = CloudTurn(snapshot(previous).copy(phase = CloudPhase.RESTING),
        "잠깐 쉬어가자. 다시 보고 싶을 때 시작하면 돼.", CloudOutcome.REST)

    fun finish(previous: CloudState): CloudTurn {
        val state = snapshot(previous).copy(phase = CloudPhase.KEEPSAKE)
        return CloudTurn(state,
            if (state.friend == null) "오늘은 편하게 쉬어간 여행으로 남겨둘게."
            else "오늘 네가 떠올린 구름 친구를 남겨둘게.", CloudOutcome.FINISH)
    }

    fun help(previous: CloudState) = CloudTurn(snapshot(previous),
        "이름이 안 떠오르면 길쭉해, 몽실몽실해처럼 느낌만 말해도 괜찮아.", CloudOutcome.HELP)

    fun observe(previous: CloudState, text: String): CloudTurn {
        val state = snapshot(previous)
        val input = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        if (input.isBlank()) return CloudTurn(state, null, CloudOutcome.IGNORED)
        if (input.length > 160) return CloudTurn(state, "한마디만 말해줘도 괜찮아.", CloudOutcome.IGNORED)
        val compact = input.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        if (compact in setOf("다시말해줘", "다시들려줘", "한번더", "다시듣기")) {
            val reply = state.friend?.let { "네가 떠올린 ${it.nickname ?: it.label} 모습을 기억하고 있어." }
                ?: "편하게 보이는 구름에서 떠오르는 게 있으면 알려줘."
            return CloudTurn(state, reply, CloudOutcome.REPEAT)
        }
        if (compact in setOf("잘못말했어", "잘못눌렀어", "되돌려", "되돌려줘", "되돌리기", "방금취소", "방금거취소", "취소")) {
            return CloudTurn(state, null, CloudOutcome.UNDO)
        }
        if (compact in setOf("도착했어", "도착했어요", "도착이야", "여행끝", "오늘은끝", "끝났어", "친구보여줘", "구름친구보여줘", "완성해줘")) return finish(state)
        val hiddenPart = Regex("(?:날개|꼬리)(?:가|는|도)?(?:이제|지금|조금|잘|하나도)?(?:안보여|보이지않|안보인다|없)").containsMatchIn(compact)
        val hiddenCloud = Regex("구름(?:이|은|도)?(?:이제|지금|잘|하나도)?(?:안보여|보이지않|안보인다|없|사라졌)").containsMatchIn(compact)
        val hiddenSomething = compact.contains("안보여") || compact.contains("보이지않") || compact.contains("안보인다")
        if (hiddenCloud || (hiddenSomething && !hiddenPart) ||
            compact in setOf("구름없어", "구름이없어", "가려졌어", "터널이야", "밤이야", "쉬자", "잠깐쉬자", "잠깐만", "멈춰", "그만", "그만할래")) return rest(state)
        if (compact in setOf("다시보여", "구름이다시보여", "다시할래", "계속하자", "다시시작", "시작", "시작하자")) return start(state)
        if (compact.contains("모르겠") || compact.contains("생각안나") || compact in setOf("어려워", "도와줘", "힌트", "뭐라고해야해")) return help(state)
        if (state.phase != CloudPhase.WATCHING) return CloudTurn(state, null, CloudOutcome.IGNORED)
        if (compact in setOf("그대로야", "아직그대로야", "똑같아", "비슷해", "달라진거없어", "변한게없어", "안바뀌었어")) return unchanged(state)

        // A corrected-out noun must never become a positive observation. The final clause may be empty.
        val clause = correction.split(input).last().trim().removePrefix("아니,").trim()
        val nicknameMatch = nicknamePattern.find(clause)
        val nickname = nicknameMatch?.groupValues?.get(1)?.let(::shortName)?.takeIf(String::isNotBlank)
        val description = if (nicknameMatch == null) clause else clause.removeRange(nicknameMatch.range)
        val form = findForm(description)
        val events = featureEvents(description)
        val explicitlyNew = compact.contains("새친구") || compact.contains("새로운친구")
        if (form == null && events.isEmpty() && nickname == null) {
            return CloudTurn(state, "응, 천천히 바라봐도 괜찮아.", CloudOutcome.IGNORED)
        }

        val original = if (explicitlyNew) null else state.friend
        val formChanged = form != null && (original?.form != form.first || original.label != form.second)
        var friend = if (formChanged) CloudFriend(form.first, form.second, original?.nickname)
            else original ?: CloudFriend()
        if (nickname != null) friend = friend.copy(nickname = nickname)
        var features = friend.features
        events.forEach { event ->
            features = if (event.positive) addFeature(features, event.feature) else features - event.feature
        }
        friend = safeFriend(friend.copy(features = features))
        // A purely negative observation cannot invent a default cloud friend.
        if (state.friend == null && form == null && nickname == null && events.none { it.positive }) {
            return CloudTurn(state, "응, 네가 말한 대로 기억할게.", CloudOutcome.IGNORED)
        }
        if (friend == state.friend) return unchanged(state)
        val memories = if (state.memories.lastOrNull()?.friend == friend) state.memories
            else (state.memories + CloudMemory(friend)).takeLast(12)
        val next = state.copy(friend = friend, memories = memories)
        val reply = when {
            nickname != null -> "이 친구의 이름은 ${nickname}! 앞으로 이렇게 불러줄게."
            formChanged -> formReply(friend, state.friend != null,
                events.filter { it.positive && it.feature in friend.features }.map { it.feature }.distinct())
            events.lastOrNull()?.positive == true -> featureReply(events.last().feature)
            else -> "그럼 그 특징은 쏙! 상상 속 친구도 달라졌네."
        }
        return CloudTurn(next, reply, CloudOutcome.OBSERVED)
    }

    private fun unchanged(state: CloudState) = CloudTurn(state,
        if (state.friend == null) "달라지지 않아도 괜찮아. 편하게 바라보자."
        else "같은 모습이어도 좋아. 두둥실, 함께 가자.", CloudOutcome.UNCHANGED)

    private fun shortName(value: String) = value.filter { it.isLetterOrDigit() }.take(12)

    private fun safeFriend(friend: CloudFriend): CloudFriend {
        val label = if (friend.form == CloudForm.OTHER) shortName(friend.label).ifBlank { CloudForm.OTHER.label }
            else friend.form.label
        val features = friend.features.fold(emptyList<CloudFeature>()) { result, feature -> addFeature(result, feature) }
        return friend.copy(label = label, nickname = friend.nickname?.let(::shortName)?.takeIf(String::isNotBlank), features = features)
    }

    private fun snapshot(state: CloudState): CloudState {
        val memories = state.memories.takeLast(12).map { CloudMemory(safeFriend(it.friend)) }
            .fold(emptyList<CloudMemory>()) { result, memory -> if (result.lastOrNull() == memory) result else result + memory }
        return state.copy(friend = state.friend?.let(::safeFriend), memories = memories)
    }

    private fun findForm(text: String): Pair<CloudForm, String>? {
        // One short noun next to a comparison phrase is supported, not arbitrary sentences.
        val comparisons = unknownComparison.findAll(text).mapNotNull { match ->
            val after = text.substring(match.range.last + 1)
            if (Regex("^\\s*(?:보이(?:지|진|지는)\\s*않|지\\s*않|진\\s*않|지는\\s*않|안\\s*보)").containsMatchIn(after)) return@mapNotNull null
            var noun = match.groupValues[1]
            while (introduction.containsMatchIn(noun)) noun = noun.replaceFirst(introduction, "")
            while (formDecorator.containsMatchIn(noun)) noun = noun.replaceFirst(formDecorator, "")
            if (noun.isBlank() || noun in badNouns || listOf("같", "처럼", "아니", "보여", "그리고", "근데", "않").any(noun::contains)) return@mapNotNull null
            if ((noun.endsWith("거") || noun.endsWith("것")) && featureEvents(noun).isNotEmpty()) return@mapNotNull null
            if (noun.startsWith("안") && noun.length > 2) return@mapNotNull null
            val known = aliases[noun]
            if (known != null) known to known.label else CloudForm.OTHER to shortName(noun)
        }.toList()
        if (comparisons.isNotEmpty()) return comparisons.last()

        data class Found(val index: Int, val form: CloudForm)
        val found = mutableListOf<Found>()
        aliases.forEach { (word, form) ->
            Regex(Regex.escape(word)).findAll(text).forEach matchLoop@ { match ->
                val before = text.substring(0, match.range.first)
                val after = text.substring(match.range.last + 1)
                if (!formTail.containsMatchIn(after) || negativeAfter.containsMatchIn(after.trimStart())) return@matchLoop
                if (word.length == 1) {
                    val prefix = before.replace(Regex("\\s+"), "")
                    val boundary = before.isEmpty() || !before.last().isLetterOrDigit() ||
                        listOf("구름", "구름은", "구름이", "이건", "저건", "이제", "지금", "친구는").any(prefix::endsWith)
                    if (!boundary) return@matchLoop
                    if (word == "개" && !Regex("^\\s*(?:같|처럼|모양)").containsMatchIn(after)) return@matchLoop
                    if (word == "새" && !Regex("^\\s*(?:$|야|네|같|처럼|모양|다)").containsMatchIn(after)) return@matchLoop
                }
                found.add(Found(match.range.first, form))
            }
        }
        return found.maxByOrNull { it.index }?.form?.let { it to it.label }
    }

    private data class FeatureEvent(val feature: CloudFeature, val positive: Boolean, val index: Int)

    private fun featureEvents(text: String): List<FeatureEvent> {
        val compact = text.replace(Regex("\\s+"), "")
        val events = mutableListOf<FeatureEvent>()
        val tailRanges = mutableListOf<IntRange>()
        tailLong.findAll(compact).forEach {
            events.add(FeatureEvent(CloudFeature.LONG_TAIL, !isNegative(compact, it.range), it.range.first))
            tailRanges.add(it.range)
        }
        tailGone.findAll(compact).forEach {
            events.add(FeatureEvent(CloudFeature.LONG_TAIL, false, it.range.first))
            tailRanges.add(it.range)
        }
        featurePatterns.forEach { (feature, pattern) -> pattern.findAll(compact).forEach { match ->
            if (feature != CloudFeature.LONG || tailRanges.none { match.range.first in it }) {
                events.add(FeatureEvent(feature, !isNegative(compact, match.range), match.range.first))
            }
        } }
        return events.sortedBy { it.index }
    }

    private fun isNegative(text: String, range: IntRange): Boolean {
        val before = text.substring(0, range.first)
        val after = text.substring(range.last + 1)
        return before.endsWith("안") || Regex("^(?:(?:은|는|이|가|도))?(?:(?:이제|지금|조금|잘|하나도))?(?:없|아니|아냐|안보여|보이지않|안보인다|안달)").containsMatchIn(after) ||
            Regex("^(?:이|하)?(?:지|진|지는)않").containsMatchIn(after)
    }

    private fun addFeature(features: List<CloudFeature>, feature: CloudFeature): List<CloudFeature> {
        val opposite = when (feature) {
            CloudFeature.BIG -> CloudFeature.SMALL
            CloudFeature.SMALL -> CloudFeature.BIG
            CloudFeature.LONG -> CloudFeature.ROUND
            CloudFeature.ROUND -> CloudFeature.LONG
            else -> null
        }
        val compatible = features.filter { it != opposite }.distinct()
        return if (feature in compatible) compatible else compatible + feature
    }

    private fun formReply(friend: CloudFriend, changed: Boolean, additions: List<CloudFeature>): String {
        val name = if (friend.form == CloudForm.CLOUD) friend.label else "구름 ${friend.label}"
        val details = additions.takeLast(2).joinToString(" ") { when (it) {
            CloudFeature.LONG_TAIL -> "긴 꼬리의"
            CloudFeature.WINGS -> "날개 달린"
            CloudFeature.ROUND -> "동글동글한"
            CloudFeature.FLUFFY -> "몽실몽실한"
            CloudFeature.LONG -> "길쭉한"
            CloudFeature.BIG -> "커다란"
            CloudFeature.SMALL -> "작은"
            CloudFeature.BRIGHT -> "반짝이는"
        } }.let { if (it.isEmpty()) "" else "$it " }
        if (changed) return "이번엔 ${details}${name} 모습이구나! 멋지게 변신했네."
        val sound = when (friend.form) {
            CloudForm.WHALE -> "푸우"
            CloudForm.DRAGON -> "슈우웅"
            CloudForm.RABBIT -> "깡충"
            CloudForm.BIRD -> "포르르"
            CloudForm.DOG -> "멍멍"
            CloudForm.CAT -> "야옹"
            CloudForm.TURTLE -> "느릿느릿"
            CloudForm.DINOSAUR -> "쿵쿵"
            CloudForm.HEART -> "두근두근"
            else -> "두둥실"
        }
        val last = name.lastOrNull()?.code ?: 0
        val particle = if (last in 0xAC00..0xD7A3 && (last - 0xAC00) % 28 != 0) "이" else "가"
        return "$sound, 네 상상 속 ${details}${name}${particle} 태어났어!"
    }

    private fun featureReply(feature: CloudFeature) = when (feature) {
        CloudFeature.LONG_TAIL -> "휘이잉, 긴 꼬리로 쭉 기지개!"
        CloudFeature.WINGS -> "파닥파닥! 상상 속 친구에게 날개가 생겼네."
        CloudFeature.ROUND -> "데굴데굴! 동글동글한 친구라니 재미있겠다."
        CloudFeature.FLUFFY -> "몽실몽실, 상상 속 친구를 폭 안아주고 싶다!"
        CloudFeature.LONG -> "쭈우욱! 상상 속 친구가 길게 늘어났네."
        CloudFeature.BIG -> "우와, 커다란 구름 친구! 하늘을 가득 채울 것 같아."
        CloudFeature.SMALL -> "쏘옥! 상상 속 작은 친구가 손바닥에 올라왔어."
        CloudFeature.BRIGHT -> "반짝반짝! 상상 속 친구도 빛나는구나."
    }
}
