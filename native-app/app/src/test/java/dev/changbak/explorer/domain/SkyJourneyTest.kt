package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class SkyJourneyTest {
    private fun watching() = CloudPlayEngine.start(CloudPlayEngine.create()).state
    @Test fun journeyDoesNotGrowByTimeOrInvalidDistance() {
        val initial = SkyJourney()
        assertEquals(initial, SkyJourneyEngine.advance(initial, Float.NaN, JourneySource.VEHICLE))
        assertEquals(initial, SkyJourneyEngine.advance(initial, -1f, JourneySource.VEHICLE))
        assertEquals(initial, SkyJourneyEngine.advance(initial, 10f, JourneySource.NONE))
    }
    @Test fun demoAndRealTripsCannotSilentlyMix() {
        val real = SkyJourneyEngine.advance(SkyJourney(), 20f, JourneySource.VEHICLE)
        assertEquals(real, SkyJourneyEngine.advance(real, 180f, JourneySource.DEMO))
    }
    @Test fun vehicleDistanceUsesFreshSamplesOnly() {
        val source = VehicleDistanceIntegrator()
        assertEquals(0f, source.sample(10f, 1_000_000_000), .001f)
        assertEquals(10f, source.sample(10f, 2_000_000_000), .001f)
        assertEquals(0f, source.sample(10f, 2_000_000_000), .001f)
        assertEquals(0f, source.sample(10f, 9_000_000_000), .001f)
        source.reset()
        assertEquals(0f, source.sample(10f, 10_000_000_000), .001f)
    }
    @Test fun invalidAndReverseSpeedAreHandled() {
        val source = VehicleDistanceIntegrator()
        source.sample(-2f, 1_000_000_000)
        assertEquals(2f, source.sample(-2f, 2_000_000_000), .001f)
        assertEquals(0f, source.sample(Float.NaN, 3_000_000_000), .001f)
        assertEquals(0f, source.sample(20f, 4_000_000_000), .001f)
    }
    @Test fun childObservationBelongsToCurrentTravelSegment() {
        val state = watching().let { it.copy(cloudWorld = it.cloudWorld!!.copy(journey = SkyJourney(360f, JourneySource.DEMO))) }
        val turn = CloudPlayEngine.observe(state, "나무가 엄청 많아")
        val journey = turn.state.cloudWorld!!.journey
        assertEquals(listOf(SkyWaypoint(WaypointKind.TREES, 360f)), journey.landmarks)
        assertTrue(turn.state.cloudWorld!!.clouds.isEmpty())
    }
    @Test fun cloudShapesAreStillCloudsAndQuestionsDoNotCreatePlaces() {
        assertNull(SkyJourneyEngine.observe(watching(), "나무 같은 구름이야"))
        assertNull(SkyJourneyEngine.observe(watching(), "나무는 어디 있어?"))
        assertNull(SkyJourneyEngine.observe(watching(), "나무가 없어"))
    }
    @Test fun tunnelBecomesLightAndDoesNotRestTheConversation() {
        val tunnel = CloudPlayEngine.observe(watching(), "터널에 들어왔어").state
        assertTrue(tunnel.cloudWorld!!.journey.tunnel)
        assertEquals(CloudPhase.WATCHING, tunnel.phase)
        val light = CloudPlayEngine.observe(tunnel, "터널에서 나왔어").state
        assertFalse(light.cloudWorld!!.journey.tunnel)
        assertEquals(listOf(WaypointKind.TUNNEL, WaypointKind.LIGHT), light.cloudWorld!!.journey.landmarks.map { it.kind })
    }
    @Test fun duplicatesAreSuppressedButLaterSegmentsRemain() {
        val state = CloudPlayEngine.observe(watching(), "나무가 보여").state
        val repeated = CloudPlayEngine.observe(state, "나무가 보여").state
        assertEquals(state, repeated)
        val later = state.copy(cloudWorld = state.cloudWorld!!.copy(journey = SkyJourneyEngine.advance(state.cloudWorld!!.journey, 100f, JourneySource.DEMO)))
        assertEquals(2, CloudPlayEngine.observe(later, "나무가 보여").state.cloudWorld!!.journey.landmarks.size)
    }
    @Test fun finishFreezesTheIslandAndDoesNotCreateFriends() {
        val ended = CloudPlayEngine.finish(watching()).state
        assertTrue(ended.cloudWorld!!.journey.complete)
        assertEquals(ended.cloudWorld!!.journey, SkyJourneyEngine.advance(ended.cloudWorld!!.journey, 100f, JourneySource.DEMO))
        assertNull(SkyJourneyEngine.observe(ended, "나무가 보여"))
        assertTrue(ended.cloudWorld!!.clouds.isEmpty())
    }
    @Test fun storedLandmarksAreBounded() {
        var state = watching()
        repeat(50) { i -> state = CloudPlayEngine.observe(state, if (i % 2 == 0) "나무가 보여" else "호수가 보여").state }
        assertEquals(32, state.cloudWorld!!.journey.landmarks.size)
    }
}
