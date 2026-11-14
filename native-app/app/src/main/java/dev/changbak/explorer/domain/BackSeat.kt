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

    /**
     * Said by a child who wants the current place to end, in the language they are spoken to in.
     *
     * These used to be matched anywhere in the sentence, which is wrong twice over. "다음에 또
     * 보고 싶어" is a child saying they liked it, and it was read as "skip". "다시 지으면
     * 좋겠어" is a child imagining, and it was read as "say that again". A word is not a
     * command because it appears; it is a command because the sentence is about it.
     */
    private val skipWords = mapOf(
        ChildLanguage.KOREAN to Cues(
            // The verb is part of the command. "다음" on its own is "next"; "다음에" with its
            // particle is "next time", and "다음에 또 보고 싶어" is a child saying they liked it.
            phrases = listOf("넘어가", "건너뛰", "그만볼래", "다음거", "다음으로"),
            alone = listOf("다음", "패스"),
        ),
        ChildLanguage.ENGLISH to Cues(
            alone = listOf("next", "next one", "skip", "skip it", "move on", "go on"),
        ),
    )
    private val repeatWords = mapOf(
        ChildLanguage.KOREAN to Cues(
            // "다시 들려줘" asks for the line again. "다시 지으면 좋겠어" is the child building
            // something in their head, and the word they share says nothing about which it is.
            phrases = listOf("다시들려", "다시말해", "다시알려", "다시해", "한번더", "또들려", "뭐라고", "못들었"),
            alone = listOf("다시"),
        ),
        ChildLanguage.ENGLISH to Cues(
            alone = listOf("again", "say it again", "say that again", "what", "pardon", "repeat"),
        ),
    )
    private val restWords = mapOf(
        ChildLanguage.KOREAN to Cues(
            phrases = listOf("그만듣", "그만할", "그만하자", "그만하고", "쉬자", "잠깐쉴", "조용히"),
            alone = listOf("그만"),
        ),
        ChildLanguage.ENGLISH to Cues(
            alone = listOf("stop", "stop it", "enough", "quiet", "be quiet", "no more", "break"),
        ),
    )

    /**
     * The two shapes a command comes in.
     *
     * [phrases] carry their own verb, so they are recognised at the end of a sentence: Korean
     * puts the verb last, and "이제 그만 듣고 싶어" is a request to stop. [alone] are the bare
     * words, which only count when the child said nothing else — on their own they are far too
     * common inside ordinary sentences to be read as instructions.
     */
    private data class Cues(val phrases: List<String> = emptyList(), val alone: List<String>)

    /**
     * What may sit in front of a command without changing it.
     *
     * "이제 그만" and "please stop" are the same instruction as "그만" and "stop". Nothing else
     * is allowed in front, because a command with a sentence in front of it is a sentence.
     */
    private val leadIns = mapOf(
        ChildLanguage.KOREAN to listOf("이제", "그럼", "우리", "저기"),
        ChildLanguage.ENGLISH to listOf("please", "can you", "could you", "lets", "let us", "ok", "okay", "i want to"),
    )

    /** How much may trail a Korean command word and still be its ending rather than a sentence. */
    private const val TAIL = 3

    /** A reply's words, stripped of everything speech recognition adds or drops. */
    fun compact(text: String): String = text.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")

    /** English as said rather than as typed: lower case, no punctuation, single spaces. */
    fun plain(text: String): String =
        text.lowercase().replace(Regex("[\\p{P}]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * @param choices the answers currently on screen, in the order they are drawn.
     * @param language the language the child is being spoken to in. A command only counts in
     *   the language the app is using, so an English answer is never read as a Korean command.
     */
    fun of(
        text: String,
        choices: List<String>,
        language: ChildLanguage = ChildLanguage.KOREAN,
    ): SpokenIntent {
        if (text.isBlank() || text.length > MAX) return SpokenIntent.Unclear
        val said = compact(text)
        if (said.isEmpty()) return SpokenIntent.Unclear
        // Control words win over answers: a child saying "그만" is not choosing anything.
        if (commands(text, language, restWords)) return SpokenIntent.Rest
        if (commands(text, language, skipWords)) return SpokenIntent.Skip
        if (commands(text, language, repeatWords)) return SpokenIntent.Repeat
        match(text, choices)?.let { return SpokenIntent.Choose(it) }
        return SpokenIntent.OwnWords(text.trim())
    }

    /**
     * Whether the sentence is one of these commands rather than a sentence containing one.
     *
     * Korean puts the verb last, so a command ends on its command word, give or take an ending
     * like 줘 or 할래. English says the whole short phrase and nothing else. Both let a polite
     * word in front. Anything longer is a child talking, and a child talking is never skipped.
     */
    private fun commands(
        text: String,
        language: ChildLanguage,
        table: Map<ChildLanguage, Cues>,
    ): Boolean {
        val cues = table[language] ?: return false
        val said = when (language) {
            ChildLanguage.KOREAN -> stripLead(compact(text), language) { compact(it) }
            ChildLanguage.ENGLISH -> stripLead(plain(text), language) { plain(it) }
        }
        if (cues.alone.any { said == it }) return true
        return cues.phrases.any { cue ->
            val at = said.lastIndexOf(cue)
            at >= 0 && said.length - (at + cue.length) <= TAIL
        }
    }

    private fun stripLead(said: String, language: ChildLanguage, shape: (String) -> String): String {
        var left = said
        leadIns[language].orEmpty().map(shape).sortedByDescending { it.length }.forEach { lead ->
            if (left.startsWith(lead)) left = left.removePrefix(lead).trim()
        }
        return left
    }

    /**
     * Whether the child says they can see the place, cannot see it, or said neither.
     *
     * This lived in the screen as a Korean regular expression, so an English child who read
     * "Say “I see it”" off the screen and said exactly that was told, in Korean, to say "봤어".
     *
     * Not being able to see it is checked first on purpose: "I can't see it" contains "see it",
     * and a child who cannot find the building must never be recorded as having seen it.
     */
    fun seen(text: String, language: ChildLanguage): Boolean? = when (language) {
        ChildLanguage.KOREAN -> {
            val said = compact(text)
            when {
                listOf("안보", "못봤", "못보", "안봤", "안찾").any(said::contains) -> false
                listOf("봤어", "보여", "보인다", "찾았", "있어", "보임").any(said::contains) -> true
                else -> null
            }
        }
        ChildLanguage.ENGLISH -> {
            val said = " ${plain(text)} "
            when {
                listOf("cant see", "can t see", "cannot see", "dont see", "don t see",
                    "not see", "no ", "nope").any(said::contains) -> false
                listOf("see it", "i see", "found it", "there it is", "got it",
                    "yes", "spotted").any(said::contains) -> true
                else -> null
            }
        }
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
        // A stem match needs a real word behind it. "the" starts "there", and in English that
        // alone would make a child naming one answer look like they named both.
        val spoken = words(text).filter { it.length >= STEM_MIN }
        val hits = choices.indices.filter { index ->
            distinctive(choices, index).any { key ->
                said.contains(key) ||
                    spoken.any { word -> key.length >= STEM_MIN && (key.startsWith(word) || word.startsWith(key)) }
            }
        }
        // Two choices matched means the sentence named neither of them clearly enough, and
        // guessing between them would put words in the child's mouth.
        return hits.singleOrNull()
    }

    /** The words of one choice that no other choice shares. */
    private fun distinctive(all: List<String>, index: Int): List<String> {
        val others = all.filterIndexed { other, _ -> other != index }.map(::compact)
        val plain = words(all[index]).filter { word -> others.none { it.contains(word) } }
        // Filler words identify nothing on their own, but a one-word answer made entirely of
        // them still has to be reachable: dropping them all would leave that answer unsayable.
        val own = plain.filterNot { it.lowercase() in FILLER }.ifEmpty { plain }
        // "첫 번째" against "열 번째" comes down to one syllable each, and a child saying "첫"
        // has answered as clearly as anyone can. Single syllables are a last resort, not a
        // default, because on their own they match far too much.
        return own.filter { it.length >= 2 }.ifEmpty { own }
    }

    /** Words that carry no answer in them, so they never stand for one. */
    private val FILLER = setOf(
        "is","are","the","a","an","it","its","do","does","doing","did","you","your","i","my",
        "to","of","in","on","at","and","or","so","that","this","there","here","would","will",
        "can","be","been","was","were","have","has","just","like","really","very",
    )

    /** Shortest word that may be matched as the start of a longer one. */
    private const val STEM_MIN = 4

    /** A sentence or a choice, cut into the words a child would actually say. */
    private fun words(text: String): List<String> = text
        .split(Regex("[\\s\\p{Z}\\p{P}]"))
        // Korean particles ride on the end of a word. A child says the word without them.
        .map { it.trimEnd('은', '는', '이', '가', '을', '를', '도', '야', '어', '해') }
        .filter { it.isNotEmpty() }
}
