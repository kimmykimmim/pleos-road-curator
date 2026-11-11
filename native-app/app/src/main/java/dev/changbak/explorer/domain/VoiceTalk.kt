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
data class PlaceTalk(
    val topics: List<TalkBranch>,
    val echo: String,
    val unsure: String,
    val silence: String,
    val closing: String,
) {
    companion object {
        /** Where the child's own words go in [echo]. */
        const val THEIR_WORDS = "%s"
    }
}

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

    private val unsureCues = listOf("모르겠", "몰라", "글쎄", "음…", "생각안", "모르겟")

    /**
     * @param turn which turn this is, counting from 1.
     * @param said what the child just said, or blank if the microphone heard nothing.
     */
    fun reply(place: CuratorPlace, said: String, turn: Int): TalkReply {
        val talk = place.talk
        val last = turn >= MAX_TURNS
        val heard = said.trim()
        if (heard.isEmpty()) return TalkReply(join(talk.silence, talk.closing, last), !last)
        val compact = SpokenReply.compact(heard)
        if (unsureCues.any { compact.contains(it) }) {
            // Not knowing ends the asking. Pressing a child who just said they don't know is
            // how the back seat learns to stop answering at all.
            return TalkReply(join(talk.unsure, talk.closing, true), false)
        }
        val topic = talk.topics.firstOrNull { topic ->
            topic.cues.any { compact.contains(SpokenReply.compact(it)) }
        }
        val body = topic?.reply ?: talk.echo.replace(PlaceTalk.THEIR_WORDS, quote(heard))
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
