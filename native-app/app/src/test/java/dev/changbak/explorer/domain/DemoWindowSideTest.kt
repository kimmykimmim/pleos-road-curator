package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which window the scripted drive points at.
 */
class DemoWindowSideTest {
    private val route = CuratorRoutes.fixed

    @Test
    fun `every place on the scripted road has a window written for it`() {
        route.placeIds.forEach { id ->
            assertTrue("$id points nowhere", route.sides[id] != null)
            assertTrue("$id points nowhere", route.sides[id] != WindowSide.UNKNOWN)
        }
    }

    @Test
    fun `the signal carries the window`() {
        val fix = route.fix(340, 1)

        assertEquals(route.sides["lotte"], fix.approaches.first { it.placeId == "lotte" }.side)
    }

    @Test
    fun `the watching step knows which way to look`() {
        val engine = RouteCurator({ CuratorCatalog.places })
        var state = engine.start()
        var seq = 0L
        (0..360 step 10).forEach { t -> state = engine.tick(state, route.fix(t, ++seq)).state }

        assertEquals(CuratorPhase.WATCH, state.phase)
        assertEquals("lotte", state.currentId)
        assertEquals(WindowSide.RIGHT, state.side)
    }

    @Test
    fun `a place that goes off the route stops pointing`() {
        val engine = RouteCurator({ CuratorCatalog.places })
        var state = engine.start()
        var seq = 0L
        (0..360 step 10).forEach { t -> state = engine.tick(state, route.fix(t, ++seq)).state }
        // The route changes and this place is no longer on it.
        val gone = engine.tick(state, CuratorFix(route.id, ++seq, emptyList())).state

        assertEquals(WindowSide.UNKNOWN, gone.side)
    }
}
