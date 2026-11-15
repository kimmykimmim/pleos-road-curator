package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackSeatTest {
    private fun listens(
        phase: CuratorPhase,
        enabled: Boolean = true,
        foreground: Boolean = true,
        restricted: Boolean = false,
        dialogOpen: Boolean = false,
        alreadyListening: Boolean = false,
    ) = BackSeat.shouldListen(phase, enabled, foreground, restricted, dialogOpen, alreadyListening)

    @Test
    fun `the microphone opens only where something was asked`() {
        assertTrue(listens(CuratorPhase.PREDICT))
        assertTrue(listens(CuratorPhase.WATCH))
        assertTrue(listens(CuratorPhase.PLAY))
    }

    @Test
    fun `it stays shut where nothing is being asked`() {
        listOf(CuratorPhase.READY, CuratorPhase.WAITING, CuratorPhase.EXPLAIN,
            CuratorPhase.FAMILY, CuratorPhase.COMPARE, CuratorPhase.MEMORY, CuratorPhase.FINISHED)
            .forEach { assertFalse(it.name, listens(it)) }
    }

    @Test
    fun `it stays shut until a guardian turns it on`() {
        // The default is off. Nothing in the app may flip it.
        assertFalse(listens(CuratorPhase.PREDICT, enabled = false))
    }

    @Test
    fun `it stays shut when the app is not the one in front of the family`() {
        assertFalse(listens(CuratorPhase.PREDICT, foreground = false))
        assertFalse(listens(CuratorPhase.PREDICT, restricted = true))
        assertFalse(listens(CuratorPhase.PREDICT, dialogOpen = true))
    }

    @Test
    fun `it does not open a microphone that is already open`() {
        assertFalse(listens(CuratorPhase.PREDICT, alreadyListening = true))
    }
}

class SpokenReplyTest {
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }
    private val firstRound = Rounds.promptFor(lotte, 0).choices

    @Test
    fun `a child naming one answer picks that button`() {
        // "안에서 함께 붙잡아 줄까?" / "아주 무거워서일까?"
        assertEquals(SpokenIntent.Choose(0), SpokenReply.of("안에서 붙잡아!", firstRound))
        assertEquals(SpokenIntent.Choose(1), SpokenReply.of("무거워서 그런 거 아니야?", firstRound))
    }

    @Test
    fun `a child reading the whole button out loud still picks it`() {
        firstRound.forEachIndexed { index, choice ->
            assertEquals(choice, SpokenIntent.Choose(index), SpokenReply.of(choice, firstRound))
        }
    }

    @Test
    fun `every written round can be answered out loud`() {
        // A child in the back seat has no other way in, so each choice must be reachable by
        // saying it. A round whose two answers cannot be told apart would strand them.
        CuratorCatalog.places.forEach { place ->
            place.rounds.forEach { prompt ->
                prompt.choices.forEachIndexed { index, choice ->
                    assertEquals("${place.id} ${prompt.kind} $choice",
                        SpokenIntent.Choose(index), SpokenReply.of(choice, prompt.choices))
                }
            }
        }
    }

    @Test
    fun `something that is not one of the answers is kept as the child said it`() {
        val said = SpokenReply.of("바람이 세게 불면 흔들릴 것 같아", firstRound)

        assertTrue(said is SpokenIntent.OwnWords)
        assertEquals("바람이 세게 불면 흔들릴 것 같아", (said as SpokenIntent.OwnWords).text)
    }

    @Test
    fun `a sentence naming both answers chooses neither`() {
        // Better to keep the child's words than to pick one of two for them.
        assertTrue(SpokenReply.of("안에서 붙잡는 것도 있고 무거워서도 그래", firstRound)
            is SpokenIntent.OwnWords)
    }

    @Test
    fun `moving on, hearing it again and stopping are understood anywhere`() {
        assertEquals(SpokenIntent.Skip, SpokenReply.of("다음!", firstRound))
        assertEquals(SpokenIntent.Skip, SpokenReply.of("넘어가자", emptyList()))
        assertEquals(SpokenIntent.Repeat, SpokenReply.of("다시 들려줘", firstRound))
        assertEquals(SpokenIntent.Repeat, SpokenReply.of("뭐라고?", emptyList()))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("그만할래", firstRound))
    }

    @Test
    fun `stopping wins over an answer that happens to contain the word`() {
        assertEquals(SpokenIntent.Rest, SpokenReply.of("이제 그만 듣고 싶어", firstRound))
    }

    @Test
    fun `silence and a wall of text are both nothing to act on`() {
        assertEquals(SpokenIntent.Unclear, SpokenReply.of("", firstRound))
        assertEquals(SpokenIntent.Unclear, SpokenReply.of("   ", firstRound))
        assertEquals(SpokenIntent.Unclear, SpokenReply.of("가".repeat(200), firstRound))
        assertEquals(SpokenIntent.Unclear, SpokenReply.of("...", firstRound))
    }

    @Test
    fun `punctuation and spacing from the recogniser do not change the answer`() {
        assertEquals(SpokenIntent.Choose(0), SpokenReply.of("안 에 서, 붙잡아!!", firstRound))
    }

    @Test
    fun `with no answers on screen nothing is ever chosen`() {
        assertNull(SpokenReply.match("무거워서", emptyList()))
        assertTrue(SpokenReply.of("무거워서", emptyList()) is SpokenIntent.OwnWords)
    }

    @Test
    fun `a sentence that merely contains a command word is still an answer`() {
        // Both of these were read as commands: the first skipped the place the child had just
        // said they liked, the second replayed the narration at a child who was imagining.
        // Found by running the app against a child's likely sentences rather than button text.
        assertTrue(SpokenReply.of("다음에 또 보고 싶어", firstRound) is SpokenIntent.OwnWords)
        assertTrue(SpokenReply.of("다시 지으면 좋겠어", firstRound) is SpokenIntent.OwnWords)
        assertTrue(SpokenReply.of("그만큼 크다고?", firstRound) is SpokenIntent.OwnWords)
    }

    @Test
    fun `short commands still work, with or without a polite word in front`() {
        assertEquals(SpokenIntent.Skip, SpokenReply.of("다음", firstRound))
        assertEquals(SpokenIntent.Skip, SpokenReply.of("다음으로 넘어가줘", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("그만", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("이제 그만할래", firstRound))
        assertEquals(SpokenIntent.Repeat, SpokenReply.of("다시 들려줘", firstRound))
        assertEquals(SpokenIntent.Repeat, SpokenReply.of("뭐라고?", firstRound))
    }

    @Test
    fun `the ways a child actually asks to stop`() {
        // Every one of these was answered as if it were a thought about the building, and the
        // conversation carried on over a child who had asked it to stop.
        assertEquals(SpokenIntent.Rest, SpokenReply.of("그만해", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("쉬고 싶어", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("잠깐만", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("이제 쉴래", firstRound))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("I want a break", emptyList(), ChildLanguage.ENGLISH))
        // And the sentence that must never be mistaken for one of them.
        assertTrue(SpokenReply.of("다음에 또 보고 싶어", firstRound) is SpokenIntent.OwnWords)
        assertTrue(SpokenReply.of("잠깐만 기다려 봐 저기 뭐가 보여", firstRound) is SpokenIntent.OwnWords)
    }

    @Test
    fun `an english child can stop, repeat and move on`() {
        val english = ChildLanguage.ENGLISH
        assertEquals(SpokenIntent.Rest, SpokenReply.of("stop", emptyList(), english))
        assertEquals(SpokenIntent.Rest, SpokenReply.of("Please stop.", emptyList(), english))
        assertEquals(SpokenIntent.Repeat, SpokenReply.of("Again!", emptyList(), english))
        assertEquals(SpokenIntent.Skip, SpokenReply.of("next", emptyList(), english))
        // And a sentence that happens to end in one of those words is not a command.
        assertTrue(SpokenReply.of("I like the bus stop", emptyList(), english) is SpokenIntent.OwnWords)
    }

    @Test
    fun `a command in the other language is not a command`() {
        // The app speaks one language at a time. A Korean word inside an English journey is a
        // child playing with words, not an instruction.
        assertTrue(SpokenReply.of("다음", emptyList(), ChildLanguage.ENGLISH) is SpokenIntent.OwnWords)
    }

    @Test
    fun `the words the screen asks for are the words that work`() {
        // The English screen says: Say "I see it". That sentence used to do nothing at all,
        // and the app answered in Korean.
        assertEquals(true, SpokenReply.seen("I see it!", ChildLanguage.ENGLISH))
        assertEquals(true, SpokenReply.seen("I found it", ChildLanguage.ENGLISH))
        assertEquals(false, SpokenReply.seen("I can't see it", ChildLanguage.ENGLISH))
        assertEquals(false, SpokenReply.seen("no", ChildLanguage.ENGLISH))
        assertNull(SpokenReply.seen("it is very tall", ChildLanguage.ENGLISH))

        assertEquals(true, SpokenReply.seen("봤어!", ChildLanguage.KOREAN))
        assertEquals(false, SpokenReply.seen("안 보여", ChildLanguage.KOREAN))
        assertNull(SpokenReply.seen("엄청 크다", ChildLanguage.KOREAN))
    }

    @Test
    fun `a quiz is answered out loud the same way as any other round`() {
        val quiz = lotte.rounds.first { it.kind == RoundKind.QUIZ }

        assertEquals(SpokenIntent.Choose(0), SpokenReply.of("123층!", quiz.choices))
        assertEquals(SpokenIntent.Choose(1), SpokenReply.of("50층인 것 같아", quiz.choices))
    }
}
