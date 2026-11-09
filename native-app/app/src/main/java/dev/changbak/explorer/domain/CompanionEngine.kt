package dev.changbak.explorer.domain

import java.text.Normalizer

enum class TalkMood { NEUTRAL, HAPPY, SAD, EXCITED, SLEEPY }
enum class TalkTopic { NONE, DESTINATION, MOOD, PLAN }
data class CompanionState(
    val destination: String? = null,
    val mood: TalkMood = TalkMood.NEUTRAL,
    val pending: TalkTopic = TalkTopic.NONE,
)
data class CompanionTurn(val state: CompanionState, val reply: String)

/** A small, session-only conversation flow. No network, transcript log or persistence. */
object CompanionEngine {
    private val places = mapOf(
        "할머니집" to "할머니 집", "할머니댁" to "할머니 댁", "할머니네" to "할머니 집",
        "할머니네집" to "할머니 집", "외할머니집" to "외할머니 집", "외갓집" to "외갓집",
        "할아버지집" to "할아버지 집", "할아버지댁" to "할아버지 댁",
        "친구집" to "친구 집", "우리집" to "우리 집", "집" to "집", "학교" to "학교",
        "유치원" to "유치원", "어린이집" to "어린이집", "놀이공원" to "놀이공원",
        "동물원" to "동물원", "수족관" to "수족관", "박물관" to "박물관", "미술관" to "미술관",
        "공원" to "공원", "바다" to "바다", "캠핑장" to "캠핑장", "도서관" to "도서관",
        "병원" to "병원", "마트" to "마트", "식당" to "식당", "수영장" to "수영장", "영화관" to "영화관",
    )
    private val observation = Regex("구름|나뭇잎|잎사귀|꼬리|날개|동글|둥글|반짝|살랑|몽실|길쭉|고래|토끼|보여|보인다|보였|봤어|봤어요|발견|흔들")
    private val question = Regex("어디|왜|무슨|무엇|뭐|뭘|누구|어떻게|언제|얼마|몇|일까|할까")
    private val restWords = setOf("쉬고싶어", "쉬고싶어요", "좀쉴래", "쉴래", "쉬자", "쉬고싶다", "잠깐쉴래", "조용히있고싶어", "조용히있자", "조용히", "잠깐쉬자")

    fun open(previous: CompanionState, friend: TravelState): CompanionTurn {
        val state = safe(previous)
        val name = friend.body?.nickname?.take(12)
        val greeting = if (name == null) "안녕, 나는 너와 함께 여행하는 친구야!" else "안녕, 나는 ${name}${copula(name)}!"
        return if (state.destination == null) CompanionTurn(state.copy(pending = TalkTopic.DESTINATION), "$greeting 오늘 어디 가?")
        else CompanionTurn(state.copy(pending = TalkTopic.MOOD), "$greeting ${state.destination} 가는 길이었지. 지금 기분은 어때?")
    }

    fun respond(previous: CompanionState, text: String, friend: TravelState): CompanionTurn? {
        val state = safe(previous)
        val input = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        if (input.isBlank() || input.length > 160) return null
        val compact = compact(input)
        if (compact in restWords) return CompanionTurn(state.copy(pending = TalkTopic.NONE),
            "좋아, 이제 조용히 함께 갈게. 준비되면 다시 말해줘.")

        if (compact.contains("어디") && (compact.contains("간다고") || compact.contains("가기로") ||
                compact in setOf("오늘어디가", "우리어디가", "나어디가", "어디가고있지", "어디가는중이지"))) {
            return CompanionTurn(state, state.destination?.let { "${it}에 간다고 말해줬어." }
                ?: "어디로 가는지는 아직 듣지 못했어.")
        }
        if ((compact.contains("내기분") || compact.contains("내가기분") || compact.contains("내가어떤기분")) &&
            Regex("어땠|였|했|기억|말했").containsMatchIn(compact)) {
            return CompanionTurn(state, when (state.mood) {
                TalkMood.HAPPY -> "기분이 좋다고 말해줬어."
                TalkMood.SAD -> "속상하거나 슬픈 기분이라고 말해줬어."
                TalkMood.EXCITED -> "신나거나 설레는 기분이라고 말해줬어."
                TalkMood.SLEEPY -> "졸리거나 피곤하다고 말해줬어."
                TalkMood.NEUTRAL -> "지금 기분이 어떻다고 했는지는 아직 뚜렷하게 기억하지 못해."
            })
        }
        val reciprocal = compact in setOf("너는", "넌", "너는어때", "넌어때", "너기분은어때", "너기분어때", "너는기분이어때", "네기분은어때", "기분어때", "기분은어때", "오늘기분어때")
        if (reciprocal) {
            if (compact in setOf("너는", "넌") && state.pending == TalkTopic.PLAN) {
                return CompanionTurn(state, "나는 네가 새로 발견한 풍경 이야기를 듣고 싶어.")
            }
            val reply = when (state.mood) {
                TalkMood.SAD -> "지금은 네 이야기를 차분히 듣고 싶어. 말하고 싶을 때 들려줘."
                TalkMood.SLEEPY -> "나는 조용히 함께 갈게. 졸리면 편하게 쉬어도 괜찮아."
                else -> friend.memories.lastOrNull()?.let { "네가 만들어 준 ${it.trait.label}과 함께 여행하니까 신나는 기분이야." }
                    ?: "나는 너와 어떤 여행을 하게 될지 궁금해."
            }
            return CompanionTurn(state, reply)
        }
        if (input.contains('?') || input.contains('？') || question.containsMatchIn(compact)) return null
        // Landscape reports belong to TravelEngine even while a destination answer is pending.
        if (observation.containsMatchIn(compact)) return null

        val mood = parseMood(compact, state.pending)
        if (mood != null) {
            if (mood == TalkMood.SLEEPY) return CompanionTurn(state.copy(mood = mood, pending = TalkTopic.NONE),
                "좀 졸리거나 피곤하구나. 이제 조용히 같이 있을게.")
            if (mood == state.mood && state.pending == TalkTopic.PLAN) {
                return CompanionTurn(state, when (mood) {
                    TalkMood.EXCITED -> "응, 설레는 마음으로 함께 가는 중이구나."
                    TalkMood.HAPPY -> "응, 지금의 좋은 기분을 함께 나누고 있어."
                    TalkMood.SAD -> "응, 속상한 마음을 듣고 있어. 서둘러 말하지 않아도 괜찮아."
                    else -> "응, 지금 네 기분을 듣고 있어."
                })
            }
            val reply = when (mood) {
                TalkMood.EXCITED -> if (compact.contains("보고싶")) "보고 싶어서 신나는구나. 도착하면 가장 먼저 뭘 하고 싶어?"
                    else "설레는 기분이구나. 도착하면 가장 먼저 뭘 하고 싶어?"
                TalkMood.HAPPY -> "기분이 좋구나. 도착하면 하고 싶은 게 있어?"
                TalkMood.SAD -> "속상한 기분이구나. 잠깐 이야기할까, 조용히 함께 있을까?"
                TalkMood.NEUTRAL -> "그냥 그런 기분이구나. 도착하면 하고 싶은 게 있어?"
                TalkMood.SLEEPY -> error("Handled above")
            }
            return CompanionTurn(state.copy(mood = mood, pending = TalkTopic.PLAN), reply)
        }

        val destination = destination(input, state.pending == TalkTopic.DESTINATION)
        if (destination != null) {
            if (destination == state.destination) return CompanionTurn(state, "응, ${destination} 가는 길이라는 걸 기억하고 있어.")
            return CompanionTurn(state.copy(destination = destination, pending = TalkTopic.MOOD),
                "${destination} 가는 길이구나. 지금 기분은 어때?")
        }
        if (Regex("안가|못가|가지않").containsMatchIn(compact) && state.destination != null && compact.contains(compact(state.destination))) {
            return CompanionTurn(state.copy(destination = null, pending = TalkTopic.DESTINATION),
                "가는 곳이 달라졌구나. 이제 어디로 가?")
        }
        if (state.pending == TalkTopic.PLAN) {
            if (compact in setOf("모르겠어", "아직모르겠어", "생각안나", "그냥갈래")) return CompanionTurn(state.copy(pending = TalkTopic.NONE),
                "아직 정하지 않아도 괜찮아. 편하게 함께 가자.")
            if (compact.contains("안아") && (compact.contains("싶") || compact.contains("줄래"))) return CompanionTurn(state.copy(pending = TalkTopic.NONE),
                "꼭 안아주고 싶은 마음이구나. 만나서 반가운 시간을 보내면 좋겠다.")
            if (Regex("놀고싶|놀래|놀거야|먹고싶|먹을래|탈래|타고싶|구경할래|보고싶|하고싶|할래|할거야").containsMatchIn(compact)) {
                return CompanionTurn(state.copy(pending = TalkTopic.NONE), "그렇게 하고 싶구나. 하고 싶은 걸 들려줘서 고마워.")
            }
            if (state.mood == TalkMood.SAD && compact in setOf("이야기할래", "말할래", "이야기", "응이야기하자")) {
                return CompanionTurn(state.copy(pending = TalkTopic.NONE), "응, 듣고 있어. 말하고 싶은 만큼만 들려줘.")
            }
        }
        return null
    }

    private fun parseMood(text: String, pending: TalkTopic): TalkMood? {
        val positive = text.replace(Regex("안(?:신나|졸려|졸리|행복해|기뻐)|(?:신나|졸리|행복하|기쁘)(?:지|진|지는)않아"), "")
        return when {
            Regex("슬퍼|슬프|속상|서운|우울|기분(?:이)?안좋|기분(?:이)?별로|기분(?:이)?좋지않|마음(?:이)?안좋").containsMatchIn(text) -> TalkMood.SAD
            Regex("졸려|졸리|잠(?:이)?와|피곤|지쳤").containsMatchIn(positive) -> TalkMood.SLEEPY
            Regex("신나|신났|설레|설렜|기대돼|기대되|두근").containsMatchIn(positive) -> TalkMood.EXCITED
            Regex("행복|즐거워|즐겁|기뻐|기쁘|기분(?:이)?좋아|기분(?:이)?좋네").containsMatchIn(positive) -> TalkMood.HAPPY
            text in setOf("그냥그래", "그저그래", "보통이야", "괜찮아", "별느낌없어") -> TalkMood.NEUTRAL
            pending == TalkTopic.MOOD && text in setOf("좋아", "좋아요", "정말좋아") -> TalkMood.HAPPY
            else -> null
        }
    }

    private fun destination(input: String, acceptBare: Boolean): String? {
        var text = input.trim().trimEnd('.', '!', '。', '！')
        text = text.split(Regex("아니고|아니라|말고")).last().trim().removePrefix("아니,").trim()
        val prefix = Regex("^(?:오늘은|오늘|나는|난|우리는|우리|지금은|지금)\\s*")
        while (prefix.containsMatchIn(text)) text = text.replaceFirst(prefix, "")
        val joined = compact(text)
        if (Regex("안가|못가|가지않|가기싫").containsMatchIn(joined)) return null
        val directed = Regex("^(.+?)(?:에|으로|로)\\s*(?:가|가요|갈래|갈거야|갈거예요|갈거에요|가는중|가고있어|가고있어요|가고있는중|가기로했어)$").find(text)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:가|가요|갈래|갈거야|가는중|가고있어)$").find(text)?.groupValues?.get(1)
        val labelled = Regex("^(?:목적지는|가는곳은|오늘가는곳은)\\s*(.+?)(?:이야|야|이에요|예요|$)$").find(text)?.groupValues?.get(1)
        var candidate = directed ?: labelled
        if (candidate == null && acceptBare) {
            val bare = text.replace(Regex("(?:이야|이에요|예요|야)$"), "").trim()
            if (compact(bare) in places || Regex("(?:공원|학교|박물관|동물원|해수욕장|집|댁|캠핑장|도서관|수영장|영화관|랜드|시|도|섬)$").containsMatchIn(bare)) candidate = bare
        }
        if (candidate == null) return null
        candidate = candidate.trim()
        if (candidate.length !in 1..24 || candidate.any(Char::isDigit) || !Regex("[\\p{L} ]+").matches(candidate)) return null
        if (observation.containsMatchIn(compact(candidate)) || Regex("주소|번지|전화|아파트|동호|이름|아니|않|없|기분").containsMatchIn(candidate)) return null
        return places[compact(candidate)] ?: candidate
    }

    private fun compact(value: String) = value.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
    private fun safe(state: CompanionState) = state.copy(destination = state.destination?.replace(Regex("\\p{C}"), "")?.take(24))
    private fun copula(name: String): String {
        val code = name.lastOrNull()?.code ?: 0
        return if (code in 0xAC00..0xD7A3 && (code - 0xAC00) % 28 != 0) "이야" else "야"
    }
}
