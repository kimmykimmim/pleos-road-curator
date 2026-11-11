package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same road, driven again. This is the ordinary case, not the exception: the school run
 * passes the same places five mornings a week.
 */
class RepeatJourneyTest {
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }
    private var round = 0
    private val engine = RouteCurator({ CuratorCatalog.places }, { round })

    private fun firstQuestion(): String {
        val fix = CuratorFix("r", 1, listOf(PlaceApproach(lotte.id, etaSeconds = 280)))
        return engine.tick(engine.start(), fix).narration!!
    }

    @Test
    fun `the first drive past opens with the hook`() {
        round = 0

        assertTrue(firstQuestion().contains("한국에서 가장 높은 건물"))
    }

    @Test
    fun `the second drive past asks something else`() {
        round = 1
        val said = firstQuestion()

        assertFalse(said.contains("한국에서 가장 높은 건물"))
        assertTrue(said.contains("지난번"))
        assertTrue(said.contains("어떻게 올라갈까"))
    }

    @Test
    fun `the third drive past is a quiz and the fourth is a looking game`() {
        round = 2
        assertTrue(firstQuestion().contains("몇 층짜리"))
        round = 3
        assertTrue(firstQuestion().contains("구름에 닿아"))
        // And a fifth one still has somewhere new to go.
        round = 4
        assertTrue(firstQuestion().contains("어떤 모양으로 할래"))
    }

    @Test
    fun `every round this place has is a different opening, then they rotate`() {
        val rounds = lotte.rounds.filter { it.kind != RoundKind.HOOK }.size
        val heard = (0..rounds).map { round = it; firstQuestion() }

        assertEquals(heard.size, heard.toSet().size)
        // Past the end it comes back round rather than running out, and still differs from the
        // one just before: a family never hears the same opening twice in a row.
        round = rounds + 1
        val next = firstQuestion()
        assertEquals(heard[1], next)
        assertNotEquals(heard[rounds], next)
    }

    @Test
    fun `a quiz tells the child the answer whichever one they picked`() {
        round = 2
        val predicting = CuratorState(phase = CuratorPhase.PREDICT, currentId = lotte.id,
            visits = listOf(PlaceMemory(lotte.id)))
        val quiz = Rounds.promptFor(lotte, 2)

        quiz.choices.forEach { choice ->
            val turn = engine.predict(predicting, choice)
            assertEquals(quiz.reveal, turn.narration)
            assertTrue(turn.narration!!.contains("123층"))
        }
        // And the child's own words get the same answer rather than a shrug.
        assertEquals(quiz.reveal, engine.predict(predicting, "백 층쯤?").narration)
    }

    @Test
    fun `a round with no answer to give does not invent one`() {
        round = 0
        val predicting = CuratorState(phase = CuratorPhase.PREDICT, currentId = lotte.id,
            visits = listOf(PlaceMemory(lotte.id)))

        assertEquals("그렇게 생각했구나. 가까이 가면 직접 살펴보자.",
            engine.predict(predicting, lotte.guesses.first()).narration)
    }

    @Test
    fun `whatever the child answers is kept, quiz or not`() {
        val predicting = CuratorState(phase = CuratorPhase.PREDICT, currentId = lotte.id,
            visits = listOf(PlaceMemory(lotte.id)))
        listOf(0, 2).forEach {
            round = it
            assertEquals("아주 무거워서",
                engine.predict(predicting, "아주 무거워서").state.visits.first().guess)
        }
    }

    @Test
    fun `a place met close up also gets today's round, not always the first`() {
        round = 1
        val fix = CuratorFix("r", 1, listOf(PlaceApproach(lotte.id, etaSeconds = 10, nearby = true)))
        val said = engine.tick(engine.start(), fix).narration!!

        assertTrue(said.contains("지난번"))
        assertNotEquals(CuratorPhase.PREDICT, engine.tick(engine.start(), fix).state.phase)
    }

    @Test
    fun `the story and the play stay the same however many times the place is met`() {
        // Only the way in changes. What the place actually teaches does not drift with a counter.
        val explaining = CuratorState(phase = CuratorPhase.EXPLAIN, currentId = lotte.id,
            visits = listOf(PlaceMemory(lotte.id, explained = true)))
        val said = (0..4).map { round = it; engine.afterExplanation(explaining).narration }

        assertEquals(1, said.toSet().size)
    }
}
