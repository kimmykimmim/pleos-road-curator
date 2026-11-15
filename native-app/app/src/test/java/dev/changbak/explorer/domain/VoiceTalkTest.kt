package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTalkTest {
    private val places = CuratorCatalog.places
    private val lotte = places.first { it.id == "lotte" }

    private val lotteEn = CuratorCatalog.places(ChildLanguage.ENGLISH).first { it.id == "lotte" }

    @Test
    fun `an english child who does not know is not asked again`() {
        // This went to the echo instead: the child was read their own words back and asked the
        // same question a second time, because the cues for not knowing were Korean only.
        val reply = VoiceTalk.reply(lotteEn, "I don't know", 1, ChildLanguage.ENGLISH)

        assertFalse(reply.askAgain)
        assertTrue(reply.line.startsWith(lotteEn.talk.unsure))
    }

    @Test
    fun `a capital letter does not lose the branch`() {
        val lower = VoiceTalk.reply(lotteEn, "wind", 1, ChildLanguage.ENGLISH).line
        val upper = VoiceTalk.reply(lotteEn, "Wind", 1, ChildLanguage.ENGLISH).line

        assertEquals(lower, upper)
    }

    @Test
    fun `an art answer is followed up about art, not about the wind`() {
        val art = lotte.rounds.first { it.field == InterestField.ART }
        val shape = VoiceTalk.reply(lotte, "동글동글한 모양", 1, field = art.field).line
        val unknown = VoiceTalk.reply(lotte, "레고 같은 거", 1, field = art.field).line

        // The place's general follow-up is about wind. An art question must not end there.
        assertFalse(shape.contains("바람"))
        assertFalse(unknown.contains("바람"))
        assertTrue(unknown.contains(lotte.talk.byField.getValue(InterestField.ART).follow))
        // With no field, the place's own follow-up still stands.
        assertTrue(VoiceTalk.reply(lotte, "레고 같은 거", 1).line.contains(lotte.talk.follow))
    }

    @Test
    fun `an idea that is not so is answered whatever the question was about`() {
        // Gentle corrections come before the field: a misconception needs answering even when
        // the child raised it during a question about something else.
        val art = InterestField.ART
        val reply = VoiceTalk.reply(lotte, "줄로 묶어 놨을 것 같아", 1, field = art).line

        assertTrue(reply.contains("줄 대신"))
    }

    @Test
    fun `every field with a question of its own has somewhere for the answer to go`() {
        listOf(ChildLanguage.KOREAN, ChildLanguage.ENGLISH).forEach { language ->
            CuratorCatalog.places(language).forEach { place ->
                val fields = place.rounds.filter { it.kind != RoundKind.HOOK }.map { it.field }.toSet()
                fields.forEach { field ->
                    val line = VoiceTalk.reply(place, "저기 저거", 1, language, field).line
                    assertTrue("${place.id} $field", line.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `what the child brought up is named back to them`() {
        val reply = VoiceTalk.reply(lotte, "바람이 세게 불어서", 1)

        assertTrue(reply.line, reply.line.startsWith("바람 이야기를 했구나"))
        assertTrue(reply.askAgain)
    }

    @Test
    fun `the reply ends with something else to think about`() {
        // Not an assessment of what they said. Somewhere for the conversation to go next.
        places.flatMap { it.talk.topics }.forEach { topic ->
            assertTrue(topic.reply, topic.reply.trimEnd().endsWith("?"))
        }
    }

    @Test
    fun `a child's own words come back quoted when there is no topic for them`() {
        val reply = VoiceTalk.reply(lotte, "공룡이 받치고 있어", 1)

        assertTrue(reply.line, reply.line.contains("공룡이 받치고 있어"))
        assertTrue(reply.askAgain)
    }

    @Test
    fun `a long sentence is quoted back short enough to say in a car`() {
        val rambling = "음 그러니까 내 생각에는 저 건물 안에 아주 커다란 용수철이 들어 있어서 그게 왔다갔다 하는 거야"
        val quoted = VoiceTalk.quote(rambling)

        assertTrue(quoted.length <= VoiceTalk.MAX_ECHO)
        assertTrue(rambling.startsWith(quoted))
        // Cut between words, not mid-word, so it does not sound like a mishearing.
        assertFalse(quoted.endsWith(" "))
        assertTrue(VoiceTalk.reply(lotte, rambling, 1).line.contains(quoted))
    }

    @Test
    fun `a short answer is quoted whole`() {
        assertEquals("바람", VoiceTalk.quote("  바람  "))
    }

    @Test
    fun `not knowing ends the asking instead of repeating the question`() {
        val reply = VoiceTalk.reply(lotte, "모르겠어", 1)

        assertFalse(reply.askAgain)
        assertTrue(reply.line, reply.line.contains("몰라도 괜찮아"))
        assertTrue(reply.line, reply.line.contains(lotte.talk.closing))
    }

    @Test
    fun `silence is answered too, rather than leaving the car quiet`() {
        val reply = VoiceTalk.reply(lotte, "   ", 1)

        assertTrue(reply.line, reply.line.contains(lotte.talk.silence))
        assertTrue(reply.askAgain)
    }

    @Test
    fun `the exchange is two turns and then the window takes over`() {
        assertTrue(VoiceTalk.reply(lotte, "바람", 1).askAgain)
        val last = VoiceTalk.reply(lotte, "무거워서", VoiceTalk.MAX_TURNS)

        assertFalse(last.askAgain)
        assertTrue(last.line, last.line.endsWith(lotte.talk.closing))
    }

    @Test
    fun `the first turn does not already send the child to the window`() {
        assertFalse(VoiceTalk.reply(lotte, "바람", 1).line.contains(lotte.talk.closing))
    }

    @Test
    fun `every place can hold its side of the conversation`() {
        places.forEach { place ->
            val talk = place.talk
            assertTrue(place.id, talk.topics.size >= 4)
            assertTrue(place.id, talk.echo.contains(PlaceTalk.THEIR_WORDS))
            listOf(talk.unsure, talk.silence, talk.closing).forEach {
                assertTrue(place.id, it.isNotBlank())
            }
            talk.topics.forEach { topic ->
                assertTrue(place.id, topic.cues.isNotEmpty())
                assertTrue(place.id, topic.cues.all { it.isNotBlank() })
                assertTrue(place.id, topic.reply.length <= 120)
            }
        }
    }

    @Test
    fun `a topic's cues reach it`() {
        places.forEach { place ->
            place.talk.topics.forEach { topic ->
                topic.cues.forEach { cue ->
                    val line = VoiceTalk.reply(place, cue, 1).line
                    assertTrue("${place.id} $cue", line.startsWith(topic.reply))
                }
            }
        }
    }

    @Test
    fun `no two topics of a place answer to the same word`() {
        // Overlapping cues would make which reply the child gets a matter of list order.
        places.forEach { place ->
            place.talk.topics.forEachIndexed { index, topic ->
                val earlier = place.talk.topics.take(index).flatMap { it.cues }
                topic.cues.forEach { cue ->
                    assertFalse("${place.id} $cue", earlier.any { cue.contains(it) })
                }
            }
        }
    }

    @Test
    fun `the app never claims the child was right or wrong`() {
        val everything = places.flatMap { place ->
            place.talk.topics.map { it.reply } +
                listOf(place.talk.echo, place.talk.unsure, place.talk.silence, place.talk.closing)
        }

        everything.forEach { line ->
            listOf("맞았", "틀렸", "정답", "오답", "아니야 그건").forEach { banned ->
                assertFalse("$line / $banned", line.contains(banned))
            }
        }
    }
}

class SpokenPredictTest {
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }
    private var round = 0
    private val engine = RouteCurator({ CuratorCatalog.places }, { round })
    private val asking = CuratorState(phase = CuratorPhase.PREDICT, currentId = lotte.id,
        visits = listOf(PlaceMemory(lotte.id)))

    @Test
    fun `the child's own sentence drives the exchange`() {
        val turn = engine.heard(asking, "바람 때문인 것 같아", 1)

        assertEquals(turn.state.message, turn.narration)
        assertTrue(turn.narration!!.startsWith("바람 이야기를 했구나"))
        assertEquals("바람 때문인 것 같아", turn.state.visits.first().guess)
    }

    @Test
    fun `only the first thing said is kept as their answer`() {
        val first = engine.heard(asking, "바람", 1)
        val second = engine.heard(first.state, "아니면 무거워서", 2)

        assertEquals("바람", second.state.visits.first().guess)
    }

    @Test
    fun `the microphone reopens until the exchange is done`() {
        assertTrue(engine.expectsAnswer(asking, 0))
        assertTrue(engine.expectsAnswer(asking, 1))
        assertFalse(engine.expectsAnswer(asking, VoiceTalk.MAX_TURNS))
    }

    @Test
    fun `a child who said they don't know is not asked again`() {
        // VoiceTalk already decided this: not knowing closes the exchange. The decision was
        // being dropped on the floor, so the microphone reopened and the child was left
        // waiting for a turn that was over.
        val turn = engine.heard(asking, "모르겠어", 1)

        assertTrue(turn.state.askingDone)
        assertFalse(engine.expectsAnswer(turn.state, 1))
        // The place that comes after starts its own question with a clean slate.
        assertTrue(engine.expectsAnswer(asking, 1))
    }

    @Test
    fun `a quiz is one answer and then the answer, not a conversation`() {
        round = 2
        // The meeting carries its own number: a visit that began as the third meeting stays
        // the third meeting even after the store moves on.
        val quiz = asking.copy(visits = listOf(PlaceMemory(lotte.id, round = 2)))
        val turn = engine.heard(quiz, "백 층쯤?", 1)

        assertEquals(Rounds.promptFor(lotte, 2).reveal, turn.narration)
        assertTrue(engine.expectsAnswer(quiz, 0))
        assertFalse(engine.expectsAnswer(quiz, 1))
    }

    @Test
    fun `the meeting number is fixed when the meeting starts`() {
        // The closing card writes this visit into the store, and the screen used to re-read it
        // straight away: the chip under the place's name called the visit the child was still
        // looking at the next one.
        val visiting = asking.copy(visits = listOf(PlaceMemory(lotte.id, round = 1)))
        round = 2

        assertEquals(1, engine.roundOf(visiting, lotte))
        // A place this journey has not met yet still asks the store.
        assertEquals(2, engine.roundOf(CuratorState(), lotte))
    }

    @Test
    fun `nothing is heard and the place still takes its turn`() {
        val turn = engine.heard(asking, "", 1)

        assertTrue(turn.narration!!.contains(lotte.talk.silence))
        assertEquals(null, turn.state.visits.first().guess)
    }

    @Test
    fun `talking outside the asking phase changes nothing`() {
        listOf(CuratorPhase.WATCH, CuratorPhase.EXPLAIN, CuratorPhase.MEMORY).forEach { phase ->
            val turn = engine.heard(asking.copy(phase = phase), "바람", 1)
            assertEquals(phase.name, null, turn.narration)
            assertEquals(phase.name, asking.copy(phase = phase), turn.state)
        }
    }

    @Test
    fun `the window and the play still expect an answer when spoken`() {
        assertTrue(engine.expectsAnswer(asking.copy(phase = CuratorPhase.WATCH), 0))
        assertTrue(engine.expectsAnswer(asking.copy(phase = CuratorPhase.PLAY), 0))
        assertFalse(engine.expectsAnswer(asking.copy(phase = CuratorPhase.EXPLAIN), 0))
    }
}
