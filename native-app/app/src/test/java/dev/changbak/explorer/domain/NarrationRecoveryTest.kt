package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationRecoveryTest {
    private fun decide(
        phase: CuratorPhase,
        token: Long = 7,
        current: Long = 7,
        paused: Boolean = false,
        suppressed: Boolean = false,
    ) = NarrationRecovery.shouldAdvance(phase, token, current, paused, suppressed)

    @Test
    fun `a failed explanation releases the phase that waits for it`() {
        assertTrue(decide(CuratorPhase.EXPLAIN))
    }

    @Test
    fun `only the phases that wait for narration are advanced`() {
        val waiting = CuratorPhase.entries.filter { decide(it) }
        assertEquals(listOf(CuratorPhase.EXPLAIN, CuratorPhase.FAMILY), waiting)
    }

    @Test
    fun `a failed family memory releases its phase too`() {
        assertTrue(decide(CuratorPhase.FAMILY))
    }

    @Test
    fun `a replaced narration cannot steer the journey`() {
        assertFalse(decide(CuratorPhase.EXPLAIN, token = 7, current = 8))
    }

    @Test
    fun `a stale narration cannot act even long after its place is gone`() {
        assertFalse(decide(CuratorPhase.EXPLAIN, token = 1, current = 94))
    }

    @Test
    fun `a paused journey stays paused`() {
        assertFalse(decide(CuratorPhase.EXPLAIN, paused = true))
    }

    @Test
    fun `a dialog or spoken answer keeps the explanation where it is`() {
        assertFalse(decide(CuratorPhase.EXPLAIN, suppressed = true))
    }

    @Test
    fun `recovery advances on the same path as the child's own button`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val place = CuratorCatalog.places.first()
        val explaining = CuratorState(
            phase = CuratorPhase.EXPLAIN,
            currentId = place.id,
            visits = listOf(PlaceMemory(place.id, explained = true)),
            message = place.explanation,
        )

        assertTrue(decide(explaining.phase))
        val recovered = engine.afterExplanation(explaining)

        assertEquals(CuratorPhase.PLAY, recovered.state.phase)
        // Interrupted speech must never be recorded as an explanation the child heard through.
        assertEquals(explaining.visits, recovered.state.visits)
        assertEquals(SeenStatus.UNKNOWN, recovered.state.visits.single().seen)
    }

    @Test
    fun `recovery does not skip the place or lose the journey's route`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val place = CuratorCatalog.places.first()
        val explaining = CuratorState(
            phase = CuratorPhase.EXPLAIN,
            routeId = "route-3",
            sequence = 12,
            currentId = place.id,
            visits = listOf(PlaceMemory(place.id, explained = true)),
        )

        val recovered = engine.afterExplanation(explaining).state

        assertEquals(place.id, recovered.currentId)
        assertEquals("route-3", recovered.routeId)
        assertEquals(12L, recovered.sequence)
        assertTrue(recovered.deferred.isEmpty())
    }
}
