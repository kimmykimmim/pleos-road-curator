package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundPromptTest {
    private val places = CuratorCatalog.places
    private val lotte = places.first { it.id == "lotte" }

    @Test
    fun `the first meeting always opens with the hook`() {
        places.forEach { place ->
            assertEquals(RoundKind.HOOK, Rounds.promptFor(place, 0).kind)
        }
    }

    @Test
    fun `the hook is never used twice`() {
        // Hearing "한국에서 가장 높은 건물이 어디인지 알아?" on the ninth school run as though it
        // were news is exactly what this is for.
        places.forEach { place ->
            (1..20).forEach { round ->
                assertNotEquals("${place.id} round $round",
                    RoundKind.HOOK, Rounds.promptFor(place, round).kind)
            }
        }
    }

    @Test
    fun `later meetings rotate through the remaining rounds in order`() {
        val later = lotte.rounds.filter { it.kind != RoundKind.HOOK }
        later.indices.forEach { index ->
            assertEquals("round ${index + 1}", later[index], Rounds.promptFor(lotte, index + 1))
        }
        // Past the end it comes back round rather than running out.
        assertEquals(later.first(), Rounds.promptFor(lotte, later.size + 1))
    }

    @Test
    fun `two meetings in a row never ask the same thing`() {
        places.forEach { place ->
            (0..12).forEach { round ->
                assertNotEquals("${place.id} round $round",
                    Rounds.promptFor(place, round), Rounds.promptFor(place, round + 1))
            }
        }
    }

    @Test
    fun `a negative or absurd count still produces a prompt`() {
        assertEquals(RoundKind.HOOK, Rounds.promptFor(lotte, -3).kind)
        assertTrue(Rounds.promptFor(lotte, 10_000).question.isNotBlank())
    }

    @Test
    fun `a place with no written rounds falls back to one question`() {
        val bare = lotte.copy(rounds = emptyList())

        assertEquals(RoundKind.HOOK, Rounds.promptFor(bare, 0).kind)
        assertTrue(Rounds.promptFor(bare, 7).question.contains(bare.name))
        assertEquals(2, Rounds.promptFor(bare, 0).choices.size)
    }

    @Test
    fun `a place with only a hook keeps using it`() {
        // A guardian's own place is like this: the app has nothing else to ask about it.
        val single = lotte.copy(rounds = lotte.rounds.take(1))

        assertEquals(lotte.rounds.first(), Rounds.promptFor(single, 0))
        assertEquals(lotte.rounds.first(), Rounds.promptFor(single, 5))
    }

    @Test
    fun `every place offers a hook, a deeper question, a quiz and a looking game`() {
        places.forEach { place ->
            val kinds = place.rounds.map { it.kind }
            assertTrue(place.id, kinds.count { it == RoundKind.HOOK } >= 1)
            assertTrue(place.id, kinds.count { it == RoundKind.DEEPER } >= 1)
            assertEquals(place.id, 1, kinds.count { it == RoundKind.QUIZ })
            assertTrue(place.id, kinds.count { it == RoundKind.HUNT } >= 1)
            // Hooks come first so a first meeting never has to search past a later round.
            assertEquals(place.id, kinds.sortedBy { if (it == RoundKind.HOOK) 0 else 1 }, kinds)
        }
    }

    @Test
    fun `every prompt is short enough to finish before the place goes past`() {
        places.flatMap { it.rounds }.forEach { prompt ->
            assertTrue(prompt.spoken, prompt.spoken.length <= RoundPrompt.MAX_SPOKEN)
            assertTrue(prompt.question.isNotBlank())
            assertEquals(2, prompt.choices.size)
            assertTrue(prompt.choices.all { it.isNotBlank() })
        }
    }

    @Test
    fun `only a quiz reveals an answer`() {
        places.flatMap { it.rounds }.forEach { prompt ->
            if (prompt.kind == RoundKind.QUIZ) assertTrue(prompt.kind.name, !prompt.reveal.isNullOrBlank())
            else assertNull(prompt.kind.name, prompt.reveal)
        }
    }

    @Test
    fun `a quiz never tells a child they were wrong`() {
        // The app may hand a child a fact they did not have. It may not grade them on it.
        val reveals = places.flatMap { it.rounds }.mapNotNull { it.reveal }

        assertTrue(reveals.isNotEmpty())
        reveals.forEach { reveal ->
            RoundPrompt.FORBIDDEN_IN_REVEAL.forEach { banned ->
                assertFalse("$reveal contains $banned", reveal.contains(banned))
            }
        }
    }

    @Test
    fun `a round that says what was asked before is only used where that is true`() {
        // With interests picking the rounds, "지난번엔 바람 이야기를 했지" is a claim about what
        // this child heard last time. It holds only when the place opens that field with a hook,
        // because a first meeting always gives the child the hook in their own field.
        places.forEach { place ->
            val hooked = place.rounds.filter { it.kind == RoundKind.HOOK }.map { it.field }.toSet()
            place.rounds.filter { it.lead.contains("지난번") }.forEach { prompt ->
                assertTrue("${place.id}: ${prompt.lead}", prompt.field in hooked)
            }
        }
        // And at least some rounds do name it, or nothing would ever feel like a second visit.
        assertTrue(places.flatMap { it.rounds }.count { it.lead.contains("지난번") } >= 4)
    }

    @Test
    fun `a hook never pretends the child has been here before`() {
        places.map { it.rounds.first() }.forEach { hook ->
            assertFalse(hook.lead, hook.lead.contains("지난번"))
            assertTrue(hook.lead.isNotBlank())
        }
    }

    @Test
    fun `the spoken line joins the lead to the question`() {
        val prompt = RoundPrompt(RoundKind.HOOK, InterestField.SCIENCE, "들어 볼래?", "왜 그럴까?", listOf("응", "아니"))

        assertEquals("들어 볼래? 왜 그럴까?", prompt.spoken)
        assertEquals("왜 그럴까?", prompt.copy(lead = "").spoken)
    }

    @Test
    fun `the first round is what the old question and guesses still mean`() {
        assertEquals(lotte.rounds.first().question, lotte.question)
        assertEquals(lotte.rounds.first().choices, lotte.guesses)
    }
}
