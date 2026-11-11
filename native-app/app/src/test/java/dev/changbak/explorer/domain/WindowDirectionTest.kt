package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowDirectionTest {
    // Seoul, where a degree of longitude is about 88 km and a degree of latitude about 111 km.
    private val start = GeoPoint(37.5000, 127.0000)
    /** Far enough north to beat MIN_TRAVEL_METERS, so the pair reads as travel, not noise. */
    private val northOfStart = GeoPoint(37.5010, 127.0000)

    private fun at(point: GeoPoint, ms: Long) = TimedPosition(point, ms)
    private fun side(target: GeoPoint, previous: GeoPoint = start, current: GeoPoint = northOfStart,
                     previousMs: Long = 1_000, currentMs: Long = 6_000) =
        WindowDirection.of(at(previous, previousMs), at(current, currentMs), target)

    @Test
    fun `driving north, a place to the west is at the left window`() {
        assertEquals(WindowSide.LEFT, side(GeoPoint(37.5010, 126.9950)))
    }

    @Test
    fun `driving north, a place to the east is at the right window`() {
        assertEquals(WindowSide.RIGHT, side(GeoPoint(37.5010, 127.0050)))
    }

    @Test
    fun `a place straight down the road is ahead`() {
        assertEquals(WindowSide.AHEAD, side(GeoPoint(37.5060, 127.0000)))
    }

    @Test
    fun `the sides swap when the car drives the other way`() {
        val west = GeoPoint(37.5010, 126.9950)
        assertEquals(WindowSide.LEFT, side(west))
        // Same place, same road, travelling south instead of north.
        assertEquals(WindowSide.RIGHT, side(west, previous = northOfStart, current = start))
    }

    @Test
    fun `a place already behind the car is not pointed at`() {
        assertEquals(WindowSide.UNKNOWN, side(GeoPoint(37.4930, 127.0000)))
    }

    @Test
    fun `a car that has barely moved cannot say which way it points`() {
        assertEquals(WindowSide.UNKNOWN, side(GeoPoint(37.5010, 126.9950), current = GeoPoint(37.50005, 127.0000)))
    }

    @Test
    fun `stale fixes no longer describe where the car points now`() {
        assertEquals(WindowSide.UNKNOWN,
            side(GeoPoint(37.5010, 126.9950), previousMs = 0, currentMs = WindowDirection.MAX_GAP_MS + 1))
    }

    @Test
    fun `fixes out of order are refused rather than reversed`() {
        assertEquals(WindowSide.UNKNOWN,
            side(GeoPoint(37.5010, 126.9950), previousMs = 9_000, currentMs = 1_000))
    }

    @Test
    fun `right on top of a place every side is arguable, so none is named`() {
        assertEquals(WindowSide.UNKNOWN, side(northOfStart))
    }

    @Test
    fun `a missing or invalid fix never points anywhere`() {
        val target = GeoPoint(37.5010, 126.9950)
        assertEquals(WindowSide.UNKNOWN, WindowDirection.of(null, at(northOfStart, 6_000), target))
        assertEquals(WindowSide.UNKNOWN, WindowDirection.of(at(start, 1_000), null, target))
        assertEquals(WindowSide.UNKNOWN, side(GeoPoint(0.0, 0.0)))
        assertEquals(WindowSide.UNKNOWN, side(target, previous = GeoPoint(0.0, 0.0)))
    }

    @Test
    fun `only a named side carries a hint for the journey to speak`() {
        assertNull(WindowSide.UNKNOWN.hint)
        listOf(WindowSide.LEFT, WindowSide.RIGHT, WindowSide.AHEAD).forEach {
            assertTrue(it.hint!!.contains("창밖을 봐"))
        }
    }

    @Test
    fun `turning past north does not flip the side`() {
        assertEquals(-10.0, WindowDirection.signedDifference(350.0, 0.0), 0.0001)
        assertEquals(10.0, WindowDirection.signedDifference(0.0, 350.0), 0.0001)
        assertEquals(180.0, WindowDirection.signedDifference(180.0, 0.0), 0.0001)
        assertEquals(180.0, WindowDirection.signedDifference(0.0, 180.0), 0.0001)
    }

    @Test
    fun `bearing runs clockwise from north`() {
        assertEquals(0.0, start.bearingTo(GeoPoint(37.5100, 127.0000)), 0.5)
        assertEquals(90.0, start.bearingTo(GeoPoint(37.5000, 127.0100)), 0.5)
        assertEquals(180.0, start.bearingTo(GeoPoint(37.4900, 127.0000)), 0.5)
        assertEquals(270.0, start.bearingTo(GeoPoint(37.5000, 126.9900)), 0.5)
    }

    @Test
    fun `the journey speaks the side only when one is named`() {
        val engine = RouteCurator(CuratorCatalog.places)
        val place = CuratorCatalog.places.first()
        val waiting = CuratorState(phase = CuratorPhase.WAITING)
        fun announce(side: WindowSide) = engine.tick(waiting, CuratorFix("r", 1,
            listOf(PlaceApproach(place.id, etaSeconds = 10, nearby = true, side = side)))).narration!!

        assertTrue(announce(WindowSide.LEFT).contains("왼쪽 창밖을 봐."))
        assertTrue(announce(WindowSide.RIGHT).contains("오른쪽 창밖을 봐."))
        // With no side to name, the place's own opening line follows straight on.
        assertTrue(announce(WindowSide.UNKNOWN)
            .contains("${place.name} 근처야. ${Rounds.promptFor(place, 0).spoken}"))
        assertTrue(!announce(WindowSide.UNKNOWN).contains("창밖을 봐."))
    }
}
