package dev.changbak.explorer.domain

/**
 * What kind of turn a place takes on a given journey.
 *
 * A family drives the same road every day. Meeting 롯데월드타워 with the identical sentence on
 * the ninth school run is how a child learns to stop listening, so a place keeps several turns
 * and spends a different one each time.
 */
enum class RoundKind {
    /** The first meeting. Opens with a fact worth interrupting a car ride for. */
    HOOK,

    /** A later meeting. Names what was asked last time, then asks about something else. */
    DEEPER,

    /** A guess-it round. Has an answer, but nothing is scored and no answer is called wrong. */
    QUIZ,

    /** A looking game. The window decides, so there is nothing to be right about. */
    HUNT,
}

/**
 * One meeting's worth of asking.
 *
 * @param lead said first, before the question. For [RoundKind.HOOK] this is the thing that makes
 *   a six year old look up; for [RoundKind.DEEPER] it is the reminder of what was asked last time.
 * @param question what the child is actually asked. Short enough to hold in a moving car.
 * @param choices the answers offered on screen, and the words a child in the back seat is likely
 *   to say out loud. Never a right-and-wrong pair outside [RoundKind.QUIZ].
 * @param reveal for a quiz only: what is said after the child answers, whichever they chose.
 *   It tells them the answer without ever telling them they were wrong.
 */
data class RoundPrompt(
    val kind: RoundKind,
    val lead: String,
    val question: String,
    val choices: List<String>,
    val reveal: String? = null,
) {
    /** What the app says when it opens this round. */
    val spoken: String get() = if (lead.isBlank()) question else "$lead $question"

    companion object {
        /** Long enough to be interesting, short enough to finish before the place goes past. */
        const val MAX_SPOKEN = 150

        /** Words a reveal must never contain. A quiz here is not a test. */
        val FORBIDDEN_IN_REVEAL = listOf("틀렸", "오답", "정답이 아니", "아쉽", "다시 생각")
    }
}

/**
 * Picks which of a place's rounds this meeting gets.
 *
 * Round 0 is the first meeting and always gets the hook. After that the remaining rounds rotate,
 * so a child who passes the same tower every weekday keeps being asked something new and the
 * opening fact is never repeated as though it were news.
 */
object Rounds {
    fun promptFor(place: CuratorPlace, round: Int): RoundPrompt {
        val all = place.rounds
        if (all.isEmpty()) return fallback(place)
        val safe = round.coerceAtLeast(0)
        if (safe == 0 || all.size == 1) return all.first()
        val later = all.drop(1)
        return later[(safe - 1) % later.size]
    }

    /** A guardian's own place has no written rounds, so its single question stands in. */
    private fun fallback(place: CuratorPlace) = RoundPrompt(
        kind = RoundKind.HOOK,
        lead = "",
        question = "${place.name}은 어떤 곳이었을까?",
        choices = listOf("기억이 날 것 같아", "잘 모르겠어"),
    )
}
