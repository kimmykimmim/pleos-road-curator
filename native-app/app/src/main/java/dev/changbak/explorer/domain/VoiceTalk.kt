package dev.changbak.explorer.domain

/**
 * Talking with the child instead of offering them two buttons.
 *
 * Two answers on screen turn a question into multiple choice: the child reads, points, and
 * learns nothing they did not already see. A question asked out loud with nothing written down
 * has to be answered out of their own head, which is the only version of this worth doing.
 *
 * Nothing here is generated. An AI inventing a reply would eventually invent a fact, and a six
 * year old has no way to tell the two apart, so every line is written for its place and checked
 * against the same source as that place's explanation. What the app can do without inventing
 * anything is what a good adult does in a car: notice what the child said, say it back, and ask
 * one more thing.
 */
data class TalkBranch(
    /** Words that mean the child is talking about this. Matched loosely, not as answers. */
    val cues: List<String>,
    /** Said back to them: names what they brought up, then asks one more thing about it. */
    val reply: String,
)

/**
 * One place's side of the conversation.
 *
 * @param topics the things a child actually says about this place, each with somewhere to go.
 * @param echo used when they said something with no topic of ours. `%s` is their own words,
 *   quoted back. Repeating a child is never a claim about the world, so this is the one reply
 *   that is safe to build from input the app does not understand.
 * @param unsure for "모르겠어". Never a re-ask: not knowing is a fine place to stop.
 * @param silence for a turn where nothing was heard at all.
 * @param closing the last thing said before the child is sent to the window.
 */
/**
 * The part of the exchange that belongs to one kind of question.
 *
 * A child who was asked an art question and answers about shape used to be taken back to the
 * wind, because every place had exactly one set of follow-ups and they were written for the
 * question that place asks by default. The first question was chosen for this child and
 * everything after it was the same for everyone, which is the opposite of what the setting
 * promises.
 *
 * @param follow what to ask next when nothing the child said is recognised. It replaces the
 *   place's general follow-up, so the second turn stays in the field the first turn opened.
 */
data class FieldTalk(val branches: List<TalkBranch> = emptyList(), val follow: String)

data class PlaceTalk(
    val topics: List<TalkBranch>,
    val echo: String,
    /** The next question when nothing matched and the round's field has nothing of its own. */
    val follow: String,
    val unsure: String,
    val silence: String,
    val closing: String,
    /**
     * Answers that are not so, and what to say instead of "no".
     *
     * A child who says the glass holds the building up has noticed something real: the glass is
     * the biggest thing they can see. Told they are wrong, they learn that answering out loud
     * costs something, and in a car they cannot leave they simply stop. So each of these starts
     * from what the child got right, hands them a clue, and lets the true thing arrive as the
     * interesting part rather than as a correction.
     *
     * Checked before [topics]: a sentence can mention the right subject and still have the
     * wrong idea in it, and the idea is what needs answering.
     */
    val gentle: List<TalkBranch> = emptyList(),
    /** Follow-ups that belong to one kind of question rather than to the place as a whole. */
    val byField: Map<InterestField, FieldTalk> = emptyMap(),
) {
    companion object {
        /** Where the child's own words go in [echo]. */
        const val THEIR_WORDS = "%s"

        /** Nothing the app says back to a child may contain any of these. */
        val NEVER_SAY = listOf("틀렸", "오답", "아니야", "잘못", "정답이 아니", "wrong", "incorrect", "no, ")
    }
}

/** Whether any of this branch's cues is in what the child said. */
private fun TalkBranch.hit(compact: String) =
    cues.any { compact.contains(SpokenReply.compact(it).lowercase()) }

/** What the app says back, and whether it is still the child's turn afterwards. */
data class TalkReply(val line: String, val askAgain: Boolean)

/**
 * Runs the exchange.
 *
 * Two turns, then the place arrives and the window takes over. A third turn would be the app
 * holding a conversation for its own sake while the thing it is about goes past the window.
 */
object VoiceTalk {
    /** How many times the child is asked for something before the story moves on. */
    const val MAX_TURNS = 2

    /** Longest fragment of a child's own words the app will say back to them. */
    const val MAX_ECHO = 24

    /**
     * "I don't know", in both languages.
     *
     * The English ones were missing, so an English child who said they did not know was read
     * back their own words and asked the question again. Not knowing has to end the asking in
     * whichever language it is said in.
     */
    private val unsureCues = mapOf(
        ChildLanguage.KOREAN to listOf("모르겠", "몰라", "글쎄", "생각안", "모르겟"),
        ChildLanguage.ENGLISH to listOf("dont know", "don t know", "do not know", "no idea",
            "not sure", "dunno", "cant think", "can t think"),
    )

    /**
     * @param turn which turn this is, counting from 1.
     * @param said what the child just said, or blank if the microphone heard nothing.
     */
    /**
     * @param field the kind of question the child is answering, so the exchange can stay in it.
     */
    fun reply(
        place: CuratorPlace,
        said: String,
        turn: Int,
        language: ChildLanguage = ChildLanguage.KOREAN,
        field: InterestField? = null,
    ): TalkReply {
        val talk = place.talk
        val last = turn >= MAX_TURNS
        val heard = said.trim()
        if (heard.isEmpty()) return TalkReply(join(talk.silence, talk.closing, last), !last)
        // Lower case throughout: a recogniser that returns "Wind" with a capital must not fall
        // past the branch that "wind" matches.
        val compact = SpokenReply.compact(heard).lowercase()
        val spoken = SpokenReply.plain(heard)
        if (unsureCues[language].orEmpty().any { spoken.contains(it) } ||
            unsureCues[ChildLanguage.KOREAN]!!.any { compact.contains(it) }) {
            // Not knowing ends the asking. Pressing a child who just said they don't know is
            // how the back seat learns to stop answering at all.
            return TalkReply(join(talk.unsure, talk.closing, true), false)
        }
        // An idea that is not so is answered first, because a sentence can name the right
        // subject while carrying the wrong idea, and the idea is the part that needs an answer.
        val own = field?.let { talk.byField[it] }
        // An idea that is not so is answered whatever the question was about. After that the
        // field's own branches come first: an answer about shape belongs to the art question
        // that asked for it, not to the wind the place usually talks about.
        val branch = talk.gentle.firstOrNull { branch -> branch.hit(compact) }
            ?: own?.branches?.firstOrNull { topic -> topic.hit(compact) }
            ?: talk.topics.firstOrNull { topic -> topic.hit(compact) }
        val body = branch?.reply
            ?: "${talk.echo.replace(PlaceTalk.THEIR_WORDS, quote(heard))} ${own?.follow ?: talk.follow}"
        return TalkReply(join(body, talk.closing, last), !last)
    }

    /** The closing is added only on the last turn, so the child is not cut off early. */
    private fun join(body: String, closing: String, last: Boolean) =
        if (last) "$body $closing" else body

    /**
     * The child's words, trimmed to something that can be said back in a car.
     *
     * Cut at a word boundary where there is one: a sentence chopped mid-syllable and read aloud
     * sounds like the app misheard, which is worse than saying less of it.
     */
    fun quote(said: String): String {
        val clean = said.trim().replace(Regex("\\s+"), " ")
        if (clean.length <= MAX_ECHO) return clean
        val cut = clean.take(MAX_ECHO)
        val boundary = cut.lastIndexOf(' ')
        return if (boundary >= MAX_ECHO / 2) cut.take(boundary) else cut
    }
}
