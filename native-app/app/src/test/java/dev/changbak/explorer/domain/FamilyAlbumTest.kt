package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(said.contains("우리 가족이 함께 왔던 곳"))
        assertTrue(said.contains(FamilyVisit.WHEN_CHOICES.first()))
        assertTrue(said.contains("전망대에서 한강을 봤어."))
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
}
