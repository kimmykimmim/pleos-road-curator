package dev.changbak.explorer.domain

/**
 * The child is in the back seat and cannot reach the screen.
 *
 * Every button in this app sits on the centre display, within reach of the driver and nobody
 * else. A six year old strapped into a booster seat behind it can hear the question perfectly
 * and has no way to answer it. Either the driver answers for the child, which defeats the point,
 * or the driver reaches across while moving, which is worse.
 *
 * So the back seat gets the microphone. This file holds the two decisions that need to be right:
 * when it may open, and what a child's sentence means once it does.
 */
object BackSeat {
    /**
     * The phases where a question has just been asked and an answer would mean something.
     *
     * EXPLAIN and MEMORY are deliberately absent. Nothing is being asked there, and an open
     * microphone that is not waiting for anything is a microphone with no reason to be open.
     */
    private val answering = setOf(CuratorPhase.PREDICT, CuratorPhase.WATCH, CuratorPhase.PLAY)

    /**
     * Whether to open the microphone by itself now that the narration has finished.
     *
     * Every condition here is a reason not to. The guardian has to have turned this on, the car
     * has to be in a state where the app is running normally, and the app has to have just
     * finished asking something. One turn only: this is called again after the next question.
     *
     * @param enabled the guardian's setting. Off by default, and the only way this returns true.
     */
    fun shouldListen(
        phase: CuratorPhase,
        enabled: Boolean,
        foreground: Boolean,
        restricted: Boolean,
        dialogOpen: Boolean,
        alreadyListening: Boolean,
    ): Boolean {
        if (!enabled || !foreground || restricted || dialogOpen || alreadyListening) return false
        return phase in answering
    }
}

/** What a child in the back seat just asked the app to do. */
sealed interface SpokenIntent {
    /** One of the answers on screen, named out loud. */
    data class Choose(val index: Int) : SpokenIntent

    /** Not one of the offered answers, but still an answer. Kept as the child said it. */
    data class OwnWords(val text: String) : SpokenIntent

    /** Move on without answering. */
    data object Skip : SpokenIntent

    /** Say it again. */
    data object Repeat : SpokenIntent

    /** Stop for now. */
    data object Rest : SpokenIntent

    /** Heard, but nothing in it to act on. */
    data object Unclear : SpokenIntent
}

/**
 * Turns what a child said into what the app should do.
 *
 * A child does not read the buttons back verbatim. Asked "엘리베이터가 아주 빠를까?" they say
 * "엘리베이터!", so matching is on the distinctive words of each choice rather than the whole
 * sentence, with punctuation and spacing removed because speech recognition punctuates freely.
 *
 * Nothing here decides whether an answer is right. It only decides which answer was given.
 */
object SpokenReply {
    private const val MAX = 160

    /** Said by a child who wants the current place to end. */
    private val skipWords = listOf("다음", "넘어가", "건너뛸", "그만볼래", "패스")
    private val repeatWords = listOf("다시", "한번더", "또들려", "뭐라고", "못들었")
    private val restWords = listOf("쉬자", "그만", "그만할래", "잠깐쉴래", "조용히")

    /** A reply's words, stripped of everything speech recognition adds or drops. */
    fun compact(text: String): String = text.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")

    /**
     * @param choices the answers currently on screen, in the order they are drawn.
     */
    fun of(text: String, choices: List<String>): SpokenIntent {
        if (text.isBlank() || text.length > MAX) return SpokenIntent.Unclear
        val said = compact(text)
        if (said.isEmpty()) return SpokenIntent.Unclear
        // Control words win over answers: a child saying "그만" is not choosing anything.
        if (restWords.any { said.contains(it) }) return SpokenIntent.Rest
        if (skipWords.any { said.contains(it) }) return SpokenIntent.Skip
        if (repeatWords.any { said.contains(it) }) return SpokenIntent.Repeat
        match(text, choices)?.let { return SpokenIntent.Choose(it) }
        return SpokenIntent.OwnWords(text.trim())
    }

    /**
     * Which choice the child named, or null.
     *
     * A choice is matched on its own distinctive words — the ones the other choices do not also
     * contain — so "엘리베이터가 아주 빠를까?" is found by "엘리베이터" while the shared "일까"
     * and "거야" of a pair cannot match either of them.
     *
     * A child does not finish the sentence on the button. Asked to pick between "아주
     * 무거워서일까?" and "안에서 함께 붙잡아 줄까?" they say "무거워서", so a word that is the
     * start of a distinctive word counts as having named it.
     */
    fun match(text: String, choices: List<String>): Int? {
        if (choices.isEmpty()) return null
        val said = compact(text)
        // Whole words come from the sentence as spoken: compacting first would weld it into one
        // long string and leave nothing to compare a choice's words against.
        val spoken = words(text).filter { it.length >= 2 }
        val hits = choices.indices.filter { index ->
            distinctive(choices, index).any { key ->
                said.contains(key) || spoken.any { word -> key.startsWith(word) || word.startsWith(key) }
            }
        }
        // Two choices matched means the sentence named neither of them clearly enough, and
        // guessing between them would put words in the child's mouth.
        return hits.singleOrNull()
    }

    /** The words of one choice that no other choice shares. */
    private fun distinctive(all: List<String>, index: Int): List<String> {
        val others = all.filterIndexed { other, _ -> other != index }.map(::compact)
        val own = words(all[index]).filter { word -> others.none { it.contains(word) } }
        // "첫 번째" against "열 번째" comes down to one syllable each, and a child saying "첫"
        // has answered as clearly as anyone can. Single syllables are a last resort, not a
        // default, because on their own they match far too much.
        return own.filter { it.length >= 2 }.ifEmpty { own }
    }

    /** A sentence or a choice, cut into the words a child would actually say. */
    private fun words(text: String): List<String> = text
        .split(Regex("[\\s\\p{Z}\\p{P}]"))
        // Korean particles ride on the end of a word. A child says the word without them.
        .map { it.trimEnd('은', '는', '이', '가', '을', '를', '도', '야', '어', '해') }
        .filter { it.isNotEmpty() }
}
