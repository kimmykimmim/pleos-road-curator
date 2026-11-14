package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyAlbumTest {
    private val lotte = CuratorCatalog.places.first { it.id == "lotte" }
    private val gate = CuratorCatalog.places.first { it.id == "gwanghwamun" }
    private val visit = FamilyVisit("lotte", FamilyVisit.WHEN_CHOICES.first(), "전망대에서 한강을 봤어.")

    private fun explaining(placeId: String, familyRecalled: Boolean = false) = CuratorState(
        phase = CuratorPhase.EXPLAIN,
        currentId = placeId,
        visits = listOf(PlaceMemory(placeId, explained = true, familyRecalled = familyRecalled)),
    )

    @Test
    fun `an empty album changes nothing about the journey`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val turn = engine.afterExplanation(explaining("lotte"), FamilyAlbum())

        assertEquals(CuratorPhase.PLAY, turn.state.phase)
        assertFalse(turn.state.visits.single().familyRecalled)
    }

    @Test
    fun `a recorded place gets its own beat before the pretend play`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val turn = engine.afterExplanation(explaining("lotte"), FamilyAlbum(listOf(visit)))

        assertEquals(CuratorPhase.FAMILY, turn.state.phase)
        val said = turn.narration!!
        // The guardian wrote something, so the guardian's sentence is what is said. The app's
        // own "함께 왔던 곳" line would be talking over it, and is often not even true of what
        // a parent writes.
        assertTrue(said.contains("전망대에서 한강을 봤어."))
        assertFalse(said.contains("우리 가족이 함께 왔던 곳"))
        // The child is told it is fine not to remember, so there is nothing to get wrong.
        assertTrue(said.contains("기억나지 않아도 괜찮아"))
    }

    @Test
    fun `the family beat leads to the same pretend play as before`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val family = engine.afterExplanation(explaining("lotte"), FamilyAlbum(listOf(visit))).state
        val play = engine.afterFamilyMemory(family)

        assertEquals(CuratorPhase.PLAY, play.state.phase)
        assertEquals("lotte", play.state.currentId)
        assertTrue(play.narration!!.contains(lotte.activity.role))
    }

    @Test
    fun `a record is spoken once, not again on a repeated explanation`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val album = FamilyAlbum(listOf(visit))
        val again = engine.afterExplanation(explaining("lotte", familyRecalled = true), album)

        assertEquals(CuratorPhase.PLAY, again.state.phase)
    }

    @Test
    fun `only the recorded place gets the family beat`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val album = FamilyAlbum(listOf(visit))

        assertEquals(CuratorPhase.FAMILY, engine.afterExplanation(explaining("lotte"), album).state.phase)
        assertEquals(CuratorPhase.PLAY, engine.afterExplanation(explaining("gwanghwamun"), album).state.phase)
    }

    @Test
    fun `recording the beat never claims the child saw or remembered anything`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val before = explaining("lotte")
        val after = engine.afterExplanation(before, FamilyAlbum(listOf(visit))).state.visits.single()

        assertTrue(after.familyRecalled)
        assertEquals(SeenStatus.UNKNOWN, after.seen)
        assertNull(after.reflection)
        assertNull(after.guess)
    }

    @Test
    fun `a record with no note still reads as a whole sentence`() {
        val album = FamilyAlbum(listOf(FamilyVisit("gwanghwamun", "작년에")))
        val said = album.recall(gate)!!

        assertTrue(said.contains("작년에 우리 가족이 함께 왔던 곳이야."))
        assertFalse(said.contains("  "))
    }

    @Test
    fun `a place with no record has nothing to say`() {
        assertNull(FamilyAlbum().recall(lotte))
        assertNull(FamilyAlbum(listOf(visit)).recall(gate))
    }

    @Test
    fun `editing a place replaces its record instead of adding a second one`() {
        val album = FamilyAlbum(listOf(visit)).with(FamilyVisit("lotte", "작년에", "놀이공원에 갔어."))

        assertEquals(1, album.toList().size)
        assertEquals("작년에", album.of("lotte")!!.whenLabel)
    }

    @Test
    fun `a guardian can remove a record`() {
        val album = FamilyAlbum(listOf(visit)).without("lotte")

        assertTrue(album.isEmpty)
        assertNull(album.recall(lotte))
    }

    @Test
    fun `records survive being written and read back`() {
        val visits = listOf(visit, FamilyVisit("gwanghwamun", "작년에"))
        val restored = FamilyAlbum.decode(FamilyAlbum.encode(visits))

        assertEquals(visits.sortedBy { it.placeId }, restored.sortedBy { it.placeId })
    }

    @Test
    fun `an unreadable or older record is dropped rather than guessed at`() {
        assertTrue(FamilyAlbum.decode(null).isEmpty())
        assertTrue(FamilyAlbum.decode("").isEmpty())
        assertTrue(FamilyAlbum.decode("not json at all").isEmpty())
        assertTrue(FamilyAlbum.decode("""{"version":99,"visits":[{"placeId":"lotte"}]}""").isEmpty())
    }

    @Test
    fun `a damaged entry is skipped without losing the rest`() {
        val text = """{"version":1,"visits":[{"note":"no place"},{"placeId":"lotte","whenLabel":"작년에"}]}"""
        val restored = FamilyAlbum.decode(text)

        assertEquals(listOf(FamilyVisit("lotte", "작년에")), restored)
    }

    @Test
    fun `a long note is cut to a length the curator can read aloud`() {
        val long = "가".repeat(400)
        val said = FamilyAlbum(listOf(FamilyVisit("lotte", "작년에", long))).recall(lotte)!!

        assertTrue(said.contains("가".repeat(FamilyVisit.MAX_NOTE)))
        assertFalse(said.contains("가".repeat(FamilyVisit.MAX_NOTE + 1)))
    }

    @Test
    fun `the app's frame around a family record follows the child's language`() {
        // The album had a language all along and neither caller passed it, so an English
        // journey recalled a Korean sentence. The guardian's own words stay as written.
        val visit = FamilyVisit("lotte", "Last year", "we had ice cream")
        val english = FamilyAlbum(listOf(visit), ChildLanguage.ENGLISH).recall(
            CuratorCatalog.places(ChildLanguage.ENGLISH).first { it.id == "lotte" })

        assertNotNull(english)
        // The frame is the app's and follows the child; the story inside it is the guardian's
        // and never changes.
        assertTrue(english!!.contains("Someone at home left a story"))
        assertTrue(english.contains("we had ice cream"))
    }

    @Test
    fun `a story the guardian wrote is what the child hears`() {
        // The app used to wrap every record in "우리 가족이 함께 왔던 곳이야", which is not
        // true of half of what a parent would write: the child was not at the place their
        // parents first met. When there is a story, the story is the whole of it.
        val visit = FamilyVisit("sungnyemun", "작년에", "엄마 아빠가 처음 만난 곳이야.")
        val told = FamilyAlbum(listOf(visit)).recall(
            CuratorCatalog.places.first { it.id == "sungnyemun" })

        assertNotNull(told)
        assertTrue(told!!.contains("엄마 아빠가 처음 만난 곳이야."))
        assertFalse(told.contains("함께 왔던 곳"))
        // And the time is not read out on top of it: the sentence carries its own.
        assertFalse(told.contains("작년에"))
    }

    @Test
    fun `with nothing written the app says only what it knows`() {
        val visit = FamilyVisit("sungnyemun", "작년에")
        val told = FamilyAlbum(listOf(visit)).recall(
            CuratorCatalog.places.first { it.id == "sungnyemun" })

        assertNotNull(told)
        assertTrue(told!!.contains("작년에"))
        assertTrue(told.contains("함께 왔던 곳"))
    }

    @Test
    fun `a parent's sentence is long enough to be a sentence`() {
        val real = "네가 다섯 살 때 여행 가기 전에 들렀던 가게야. 어찌나 울었는지 엄마가 혼났어."
        assertTrue(real.length <= FamilyVisit.MAX_NOTE)

        val told = FamilyAlbum(listOf(FamilyVisit("sungnyemun", "작년에", real))).recall(
            CuratorCatalog.places.first { it.id == "sungnyemun" })
        assertTrue(told!!.contains(real))
    }
}
