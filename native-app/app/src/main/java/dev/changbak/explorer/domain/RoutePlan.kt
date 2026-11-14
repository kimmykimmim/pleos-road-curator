package dev.changbak.explorer.domain

/**
 * What a drive has time to say, and what it therefore says.
 *
 * The app used to pick a question per place and hope the road lasted. On a fixed route that is
 * the wrong way round: the road is known, so the time is known, and the only honest question is
 * how much of it there is room for. A drive that starts five stories and finishes two has not
 * told two stories, it has interrupted three.
 *
 * So a segment is all or nothing. Once a place's story is started it runs to its end, and the
 * plan only contains segments that fit whole.
 */
object RoutePlan {
    /**
     * Seconds per character, read aloud to a child.
     *
     * Measured against the app's own narration safety timer, which has been standing in for
     * this all along at the same rate. Korean and English differ, because a character carries
     * about two and a half times as much in Korean as a letter does in English.
     */
    const val KOREAN_PER_CHAR = 0.18
    const val ENGLISH_PER_CHAR = 0.07

    /** The breath between sentences, and the moment a child is given to answer. */
    const val TURN_PAUSE = 14

    /** What one place's story costs, beyond the words themselves: looking out of the window. */
    const val WATCHING = 20

    /** One category's story at one place. */
    data class Segment(
        val placeId: String,
        val placeName: String,
        val field: InterestField,
        /** How long this takes from its first word to its last, in seconds. */
        val seconds: Int,
    )

    /** How long this text takes to say out loud. */
    fun spokenSeconds(text: String, language: ChildLanguage = ChildLanguage.KOREAN): Int {
        if (text.isBlank()) return 0
        val rate = if (language == ChildLanguage.ENGLISH) ENGLISH_PER_CHAR else KOREAN_PER_CHAR
        return kotlin.math.ceil(text.length * rate).toInt()
    }

    /**
     * Every story this route could tell, one per place and category that has something true
     * to say there.
     *
     * A place with no question in a category produces no segment for it. That is the whole
     * point: the categories a drive offers are the ones its road can actually answer, not a
     * list of five that gets padded out with invented facts.
     */
    fun segments(
        places: List<CuratorPlace>,
        order: List<String>,
        language: ChildLanguage = ChildLanguage.KOREAN,
    ): List<Segment> = order.mapNotNull { id -> places.firstOrNull { it.id == id } }
        .flatMap { place ->
            place.rounds.map { it.field }.distinct().map { field ->
                val round = place.rounds.first { it.field == field }
                val words = listOf(round.lead, round.question, place.explanation,
                    place.activity.prompt).joinToString(" ")
                Segment(
                    placeId = place.id,
                    placeName = place.name,
                    field = field,
                    seconds = spokenSeconds(words, language) + TURN_PAUSE + WATCHING,
                )
            }
        }

    /**
     * The stories this drive has room for, in the order the road meets them.
     *
     * @param available how long the drive lasts, in seconds.
     * @param wanted the categories the guardian chose. Those come first; the rest fill what is
     *   left rather than being thrown away, because a road with time to spare should use it.
     *
     * One story per place: a child passing 숭례문 hears about 숭례문 once. The categories
     * decide which story that is, not how many.
     */
    fun fit(
        segments: List<Segment>,
        available: Int,
        wanted: Set<InterestField> = emptySet(),
    ): List<Segment> {
        val chosen = mutableListOf<Segment>()
        var left = available
        segments.map { it.placeId }.distinct().forEach { placeId ->
            val here = segments.filter { it.placeId == placeId }
            // The child's own category at this place, or the place's first if it has none.
            val pick = here.firstOrNull { it.field in wanted } ?: here.firstOrNull()
            if (pick != null && pick.seconds <= left) {
                chosen.add(pick)
                left -= pick.seconds
            }
        }
        return chosen
    }

    /** What the plan costs altogether, so a guardian can see it against the drive. */
    fun totalSeconds(plan: List<Segment>): Int = plan.sumOf { it.seconds }
}
