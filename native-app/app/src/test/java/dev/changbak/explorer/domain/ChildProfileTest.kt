package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildProfileTest {
    @Test
    fun `an empty profile changes nothing`() {
        val bare = ChildProfile()

        assertFalse(bare.hasInterests)
        assertEquals(ChildLanguage.KOREAN, bare.language)
        CuratorCatalog.places.forEach { place ->
            (0..5).forEach { round ->
                assertEquals(place.id, Rounds.promptFor(place, round), Rounds.promptFor(place, round, bare))
            }
        }
    }

    @Test
    fun `what the guardian set survives being written down`() {
        val saved = ChildProfile(
            fields = setOf(InterestField.HISTORY, InterestField.ART),
            favourites = setOf(FavouriteTopic.DINOSAUR, FavouriteTopic.SPACE),
            language = ChildLanguage.ENGLISH,
        )

        assertEquals(saved, ChildProfile.decode(ChildProfile.encode(saved)))
    }

    @Test
    fun `nonsense and emptiness decode to a profile that does nothing`() {
        assertEquals(ChildProfile(), ChildProfile.decode(null))
        assertEquals(ChildProfile(), ChildProfile.decode(""))
        assertEquals(ChildProfile(), ChildProfile.decode("dragons|unicorns|klingon"))
    }

    @Test
    fun `a guardian listing everything gets a child, not a curriculum`() {
        val greedy = ChildProfile.decode("|" + FavouriteTopic.entries.joinToString(",") { it.id } + "|ko")

        assertEquals(ChildProfile.MAX_FAVOURITES, greedy.favourites.size)
    }
}

class InterestRoundsTest {
    private val sungnyemun = CuratorCatalog.places.first { it.id == "sungnyemun" }
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }

    private fun loves(vararg fields: InterestField) = ChildProfile(fields = fields.toSet())

    /** How many meetings a place can fill from one field before it runs out of them. */
    private fun ownRounds(place: CuratorPlace, field: InterestField) =
        1 + place.rounds.count { it.kind != RoundKind.HOOK && it.field == field }

    @Test
    fun `a child who likes history is asked the history question`() {
        // The user's own example: this is the question that should reach that child.
        val history = loves(InterestField.HISTORY)
        val asked = (0 until ownRounds(sungnyemun, InterestField.HISTORY))
            .map { Rounds.promptFor(sungnyemun, it, history) }

        assertTrue(asked.all { it.field == InterestField.HISTORY })
        assertTrue(asked.any { it.question.contains("시간을 어떻게 알았을까") })
    }

    @Test
    fun `a child who likes science is asked the science question at the same place`() {
        val science = loves(InterestField.SCIENCE)
        val asked = (0 until ownRounds(sungnyemun, InterestField.SCIENCE))
            .map { Rounds.promptFor(sungnyemun, it, science) }

        assertTrue(asked.all { it.field == InterestField.SCIENCE })
        assertTrue(asked.any { it.question.contains("무너지지 않을까") })
        // And not the history one first, which is the point of asking at all.
        assertFalse(asked.any { it.question.contains("시간을 어떻게 알았을까") })
    }

    @Test
    fun `a field runs out before the place starts repeating itself`() {
        // 숭례문 has fewer science questions than history ones. When a science child has had
        // them all, the place moves on to its others rather than asking the same one forever.
        val science = loves(InterestField.SCIENCE)
        val many = (0..6).map { Rounds.promptFor(sungnyemun, it, science) }

        (0 until many.lastIndex).forEach { assertNotEquals("round $it", many[it], many[it + 1]) }
    }

    @Test
    fun `the first meeting still opens with a hook, in the child's field`() {
        assertEquals(RoundKind.HOOK, Rounds.promptFor(sungnyemun, 0, loves(InterestField.SCIENCE)).kind)
        assertEquals(InterestField.SCIENCE, Rounds.promptFor(sungnyemun, 0, loves(InterestField.SCIENCE)).field)
        assertEquals(InterestField.HISTORY, Rounds.promptFor(sungnyemun, 0, loves(InterestField.HISTORY)).field)
    }

    @Test
    fun `a place with nothing in the child's field asks its own question anyway`() {
        // 롯데월드타워 has no sourced history to ask about, and does not invent one.
        assertFalse(lotte.rounds.any { it.field == InterestField.HISTORY })
        val asked = (0..4).map { Rounds.promptFor(lotte, it, loves(InterestField.HISTORY)) }

        assertTrue(asked.all { it in lotte.rounds })
        assertEquals(RoundKind.HOOK, asked.first().kind)
    }

    @Test
    fun `two children on the same road are asked different things`() {
        val science = (0..1).map { Rounds.promptFor(sungnyemun, it, loves(InterestField.SCIENCE)).question }
        val history = (0..1).map { Rounds.promptFor(sungnyemun, it, loves(InterestField.HISTORY)).question }

        assertTrue(science.intersect(history.toSet()).isEmpty())
    }

    @Test
    fun `liking several fields puts all of them ahead of the rest`() {
        val both = loves(InterestField.SCIENCE, InterestField.HISTORY)
        val own = sungnyemun.rounds.count {
            it.kind != RoundKind.HOOK && it.field in setOf(InterestField.SCIENCE, InterestField.HISTORY)
        }
        val asked = (1..own).map { Rounds.promptFor(sungnyemun, it, both).field }

        assertTrue(asked.toSet() == setOf(InterestField.SCIENCE, InterestField.HISTORY))
        assertFalse(InterestField.ART in asked)
    }

    @Test
    fun `a place spends the child's field before anything else`() {
        CuratorCatalog.places.forEach { place ->
            place.rounds.map { it.field }.toSet().forEach { field ->
                val profile = ChildProfile(fields = setOf(field))
                val own = place.rounds.count { it.kind != RoundKind.HOOK && it.field == field }
                (1..own).forEach { round ->
                    assertEquals("${place.id} $field round $round",
                        field, Rounds.promptFor(place, round, profile).field)
                }
            }
        }
    }

    @Test
    fun `a hook in the child's field opens the first meeting when the place has one`() {
        CuratorCatalog.places.forEach { place ->
            place.rounds.filter { it.kind == RoundKind.HOOK }.forEach { hook ->
                val opening = Rounds.promptFor(place, 0, ChildProfile(fields = setOf(hook.field)))
                assertEquals("${place.id} ${hook.field}", hook.field, opening.field)
                assertEquals("${place.id} ${hook.field}", RoundKind.HOOK, opening.kind)
            }
        }
    }
}

class FavouriteBridgeTest {
    private val engine = RouteCurator({ CuratorCatalog.places }, { 0 }, { profile })
    private var profile = ChildProfile()
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }
    private val cheongjinok = CuratorCatalog.places.first { it.id == "cheongjinok" }

    @Test
    fun `a favourite is mentioned only where a true link exists`() {
        profile = ChildProfile(favourites = setOf(FavouriteTopic.DINOSAUR))

        assertTrue(engine.openingFor(lotte).startsWith("공룡 좋아한다고 했지"))
        // Nothing honest connects dinosaurs to a soup restaurant, so nothing is said.
        assertEquals(engine.promptFor(cheongjinok).spoken, engine.openingFor(cheongjinok))
    }

    @Test
    fun `with no favourites the opening is the question alone`() {
        profile = ChildProfile()

        assertEquals(engine.promptFor(lotte).spoken, engine.openingFor(lotte))
    }

    @Test
    fun `the bridge comes before the question, not instead of it`() {
        profile = ChildProfile(favourites = setOf(FavouriteTopic.SPACE))
        val opening = engine.openingFor(lotte)

        assertTrue(opening.contains(engine.promptFor(lotte).question))
        assertTrue(opening.indexOf("우주") < opening.indexOf(engine.promptFor(lotte).question))
    }

    @Test
    fun `every written bridge belongs to a place that can honestly say it`() {
        // A bridge is a claim about the place. Each one is short and names the favourite.
        CuratorCatalog.places.forEach { place ->
            place.bridges.forEach { (topic, line) ->
                assertTrue("${place.id} $topic", line.length <= 60)
                assertTrue("${place.id} $topic", line.isNotBlank())
            }
        }
        assertTrue(CuratorCatalog.places.any { it.bridges.isNotEmpty() })
    }
}

class GentleCorrectionTest {
    private val building63 = CuratorCatalog.places.first { it.id == "building63" }
    private val gwanghwamun = CuratorCatalog.places.first { it.id == "gwanghwamun" }

    @Test
    fun `an answer that is not so is met with the fact, never with no`() {
        val reply = VoiceTalk.reply(building63, "유리가 떠받치고 있어", 1)

        assertTrue(reply.line, reply.line.contains("커튼"))
        PlaceTalk.NEVER_SAY.forEach { assertFalse(reply.line, reply.line.contains(it)) }
    }

    @Test
    fun `it starts from what the child got right`() {
        val reply = VoiceTalk.reply(building63, "저거 진짜 금이야", 1)

        assertTrue(reply.line, reply.line.startsWith("금처럼 반짝이지"))
        assertTrue(reply.line, reply.line.contains("햇빛"))
    }

    @Test
    fun `a wrong idea is answered before a right-sounding subject word`() {
        // "시장" is also one of this place's printed answers, so the sentence names the subject
        // while carrying the idea that needs the fact.
        val reply = VoiceTalk.reply(gwanghwamun, "시장이 있을 것 같아", 1)

        assertTrue(reply.line, reply.line.contains("궁궐"))
    }

    @Test
    fun `nothing the app says back to a child ever negates them`() {
        listOf(KoreanPlaces.places, EnglishPlaces.places).flatten().forEach { place ->
            val everything = place.talk.topics.map { it.reply } +
                place.talk.gentle.map { it.reply } +
                listOf(place.talk.echo, place.talk.unsure, place.talk.silence, place.talk.closing) +
                place.rounds.mapNotNull { it.reveal }
            everything.forEach { line ->
                PlaceTalk.NEVER_SAY.forEach { banned ->
                    assertFalse("${place.id}: $line / $banned", line.lowercase().contains(banned))
                }
            }
        }
    }

    @Test
    fun `every place can catch at least two ideas that are not so`() {
        CuratorCatalog.places.filter { it.id != "cheongjinok" || true }.forEach { place ->
            assertTrue(place.id, place.talk.gentle.size >= 2)
            place.talk.gentle.forEach { branch ->
                assertTrue(place.id, branch.cues.isNotEmpty())
                assertTrue("${place.id}: ${branch.reply}", branch.reply.length in 20..200)
            }
        }
    }
}

class LanguagePackTest {
    @Test
    fun `both packs describe the same places`() {
        assertEquals(KoreanPlaces.places.map { it.id }, EnglishPlaces.places.map { it.id })
    }

    @Test
    fun `a family keeps its places and artwork when the language changes`() {
        KoreanPlaces.places.zip(EnglishPlaces.places).forEach { (ko, en) ->
            assertEquals(ko.id, en.id)
            assertEquals(ko.id, ko.visual, en.visual)
            assertEquals(ko.id, ko.mediaKey, en.mediaKey)
            assertEquals(ko.id, ko.sourceUrl, en.sourceUrl)
            // The name is the one thing that should differ: it is read out loud.
            assertNotEquals(ko.id, ko.name, en.name)
        }
    }

    @Test
    fun `a place asks the same kinds of question in either language`() {
        KoreanPlaces.places.zip(EnglishPlaces.places).forEach { (ko, en) ->
            assertEquals(ko.id, ko.rounds.map { it.kind to it.field }, en.rounds.map { it.kind to it.field })
        }
    }

    @Test
    fun `the English pack is complete, not half translated`() {
        EnglishPlaces.places.forEach { place ->
            val everything = place.rounds.flatMap { listOf(it.lead, it.question) + it.choices } +
                place.talk.topics.map { it.reply } + place.talk.gentle.map { it.reply } +
                listOf(place.observe, place.explanation, place.talk.echo, place.talk.closing)
            everything.forEach { line ->
                assertTrue("${place.id}: $line", line.isNotBlank())
                assertFalse("${place.id}: $line", line.any { it.code in 0xAC00..0xD7A3 })
            }
        }
    }

    @Test
    fun `the catalog hands back the pack the child's language asks for`() {
        assertEquals(KoreanPlaces.places, CuratorCatalog.places(ChildLanguage.KOREAN))
        assertEquals(EnglishPlaces.places, CuratorCatalog.places(ChildLanguage.ENGLISH))
    }

    @Test
    fun `the screen's own words exist in every language`() {
        ChildLanguage.entries.forEach { language ->
            val say = Phrases.of(language)
            listOf(say.stepWatch, say.listening, say.useTheScreen, say.skip, say.youSaid)
                .forEach { assertTrue(language.name, it.isNotBlank()) }
            assertTrue(language.name, say.meeting(2).isNotBlank())
        }
        assertNotEquals(Phrases.of(ChildLanguage.KOREAN).skip, Phrases.of(ChildLanguage.ENGLISH).skip)
    }

    @Test
    fun `nothing the car says to an English child is in Korean`() {
        // A Korean sentence read aloud by an English voice is not a cosmetic problem: it is the
        // car talking gibberish at a six year old. This covers the journey's own words, which
        // sit between the places and are easy to forget.
        val english = Narration.of(ChildLanguage.ENGLISH)
        val spoken = listOf(
            english.waiting, english.routeChanged, english.notVisible, english.thoughtNoted,
            english.notSeenYet, english.listenedOnly, english.ownIdea, english.skipped,
            english.finished, english.sideLeft, english.sideRight, english.sideAhead,
            english.comingUp("Gate", " left", "Look up."),
            english.closeBy("Gate", " left", "Why?", "Look up."),
            english.minutesAhead(5, "Gate", "Why?"),
            english.sawIt("Gate"), english.arrived("Gate"), english.wentPast("Gate"),
            english.pretend("gatekeeper", "What would you say?"),
        )
        spoken.forEach { line ->
            assertTrue(line, line.isNotBlank())
            assertFalse(line, line.any { it.code in 0xAC00..0xD7A3 })
        }
    }

    @Test
    fun `the engine speaks the child's language all the way through a place`() {
        val profile = ChildProfile(language = ChildLanguage.ENGLISH)
        val engine = RouteCurator({ CuratorCatalog.places(ChildLanguage.ENGLISH) }, { 0 }, { profile })
        val gate = CuratorCatalog.places(ChildLanguage.ENGLISH).first { it.id == "sungnyemun" }

        val asked = engine.tick(engine.start(),
            CuratorFix("r", 1, listOf(PlaceApproach(gate.id, etaSeconds = 280))))
        val explained = engine.afterExplanation(
            asked.state.copy(phase = CuratorPhase.EXPLAIN, visits = listOf(PlaceMemory(gate.id))))

        listOf(engine.start().message, asked.narration!!, explained.narration!!,
            engine.skip(asked.state).state.message, engine.finish(asked.state).state.message)
            .forEach { line -> assertFalse(line, line.any { it.code in 0xAC00..0xD7A3 }) }
    }

    @Test
    fun `an English child can answer out loud too`() {
        EnglishPlaces.places.forEach { place ->
            place.rounds.forEach { prompt ->
                prompt.choices.forEachIndexed { index, choice ->
                    assertEquals("${place.id} ${prompt.kind} $choice",
                        SpokenIntent.Choose(index), SpokenReply.of(choice, prompt.choices))
                }
            }
        }
    }
}
