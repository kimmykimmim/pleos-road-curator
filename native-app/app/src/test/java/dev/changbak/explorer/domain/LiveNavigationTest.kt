package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class LiveNavigationTest {
    private val landmark=LivePlaces.all.first { it.id=="cheongjinok" }
    private val engine=RouteCurator(CuratorCatalog.places)
    private fun point(metersNorth:Double)=landmark.anchor.copy(latitude=landmark.anchor.latitude+metersNorth/111_195.0)
    private fun stop(t:Long=1000,eta:Int=300,key:String="waypoint:0",name:String="청진옥")=
        NaviStop(key,name,landmark.anchor,eta,1000,t)
    private fun frame(t:Long=1000,north:Double=2000.0,stops:List<NaviStop> = emptyList(),route:String="r")=
        NaviFrame(route,t,t,true,t,TimedPosition(point(north),t),stops)
    @Test fun stopEtaAsksAtFiveMinutesAndNotEarlier() {
        val navigation=LiveNavigation()
        var state=engine.start()
        state=engine.tick(state,navigation.discover(frame(stops=listOf(stop(eta=301)))).fix).state
        assertEquals(CuratorPhase.WAITING,state.phase)
        val turn=engine.tick(state,navigation.discover(frame(3000,stops=listOf(stop(3000,300)))).fix)
        assertEquals(CuratorPhase.PREDICT,turn.state.phase);assertTrue(turn.narration!!.contains("5분"))
    }
    @Test fun destinationEtaIsNeverUsedForAnotherNearbyPlace() {
        val navigation=LiveNavigation()
        val result=navigation.discover(frame(north=80.0,stops=listOf(stop(name="우리집",eta=300))))
        assertEquals(0,result.timedCount)
        val approach=result.fix.approaches.single();assertNull(approach.etaSeconds)
        val turn=engine.tick(engine.start(),result.fix)
        assertEquals(CuratorPhase.WATCH,turn.state.phase);assertFalse(turn.narration!!.contains("5분"))
    }
    @Test fun outsideNearbyRadiusDoesNotInventALandmarkOnTheRoute() {
        val result=LiveNavigation().discover(frame(north=1200.0))
        assertTrue(result.fix.approaches.isEmpty())
    }
    @Test fun passageRequiresApproachingThenTwoSamplesMovingAway() {
        val n=LiveNavigation();var state=engine.start()
        listOf(500.0,250.0,100.0,10.0,-80.0).forEachIndexed { i,d ->
            state=engine.tick(state,n.discover(frame(1000L+i*2000,d)).fix).state
            assertNotEquals(CuratorPhase.EXPLAIN,state.phase)
        }
        val turn=engine.tick(state,n.discover(frame(11000,-140.0)).fix)
        assertEquals(CuratorPhase.EXPLAIN,turn.state.phase)
        assertEquals(SeenStatus.UNKNOWN,engine.memory(turn.state)!!.seen)
        assertTrue(turn.narration!!.contains("보지 못했어도"))
    }
    @Test fun leavingZoneDoesNotDiscardQuestionBeforePassageConfirmation() {
        val n=LiveNavigation();var state=engine.start()
        listOf(450.0,250.0,150.0,-230.0,-310.0).forEachIndexed { i,d ->
            state=engine.tick(state,n.discover(frame(1000L+i*6000,d)).fix).state
        }
        assertEquals(CuratorPhase.EXPLAIN,state.phase)
    }
    @Test fun parkedGpsNoiseNeverPasses() {
        val n=LiveNavigation()
        repeat(30) { i -> assertFalse(n.discover(frame(1000L+i*2000,80.0+(i%4)*4)).fix.approaches.any { it.passed }) }
    }
    @Test fun firstLocationAlreadyLeavingDoesNotClaimPassage() {
        val n=LiveNavigation()
        listOf(-40.0,-110.0,-180.0,-250.0).forEachIndexed { i,d -> assertFalse(n.discover(frame(1000L+i*2000,d)).fix.approaches.any { it.passed }) }
    }
    @Test fun impossibleLocationJumpDoesNotPass() {
        val n=LiveNavigation()
        n.discover(frame(1000,500.0));n.discover(frame(3000,100.0))
        assertFalse(n.discover(frame(4000,-1500.0)).fix.approaches.any { it.passed })
    }
    @Test fun positionGapResetsPassageHistory() {
        val n=LiveNavigation()
        n.discover(frame(1000,400.0));n.discover(frame(5000,70.0))
        n.discover(frame(30000,-140.0))
        assertFalse(n.discover(frame(32000,-240.0)).fix.approaches.any { it.passed })
    }
    @Test fun rerouteCannotCompleteOldPassage() {
        val n=LiveNavigation()
        n.discover(frame(1000,400.0));n.discover(frame(5000,70.0))
        n.discover(frame(7000,-140.0,route="new"))
        assertFalse(n.discover(frame(9000,-240.0,route="new")).fix.approaches.any { it.passed })
    }
    @Test fun staleStopsDoNotTriggerQuestions() {
        assertEquals(0,LiveNavigation().discover(frame(20000,stops=listOf(stop()))).timedCount)
    }
    @Test fun stalePositionAndRouteAreRejected() {
        val n=LiveNavigation()
        assertFalse(n.discover(frame(20000).copy(position=TimedPosition(point(0.0),1000))).fix.fresh)
        assertFalse(n.discover(frame(20000,stops=listOf(stop(20000))).copy(guidanceAtMs=1000)).fix.fresh)
    }
    @Test fun cancelledGuidanceCannotNarrate() {
        val n=LiveNavigation()
        assertFalse(n.discover(frame(stops=listOf(stop())).copy(guiding=false)).fix.fresh)
    }
    @Test fun duplicateSequenceIsIgnored() {
        val n=LiveNavigation();val f=frame(stops=listOf(stop()))
        assertTrue(n.discover(f).fix.fresh);assertFalse(n.discover(f).fix.fresh)
    }
    @Test fun zeroEtaIsNotProofOfArrival() {
        val result=LiveNavigation().discover(frame(stops=listOf(stop(eta=0))))
        assertFalse(result.fix.approaches.single().arrived)
        assertNotEquals(CuratorPhase.EXPLAIN,engine.tick(engine.start(),result.fix).state.phase)
    }
    @Test fun sdkArrivalWithNearbyLocationExplainsOnce() {
        val n=LiveNavigation()
        var state=engine.tick(engine.start(),n.discover(frame(stops=listOf(stop()))).fix).state
        val arrived=frame(8000,0.0,listOf(stop(8000,0))).copy(arrivals=listOf(NaviArrival("waypoint:0",8000)))
        val turn=engine.tick(state,n.discover(arrived).fix)
        assertEquals(CuratorPhase.EXPLAIN,turn.state.phase);assertTrue(turn.narration!!.contains("도착했어"))
        assertNull(engine.tick(turn.state,n.discover(arrived.copy(sequence=9000,nowMs=9000)).fix).narration)
    }
    @Test fun unmatchedOrDistantArrivalDoesNotExplain() {
        val n=LiveNavigation()
        val far=frame(stops=listOf(stop())).copy(arrivals=listOf(NaviArrival("waypoint:0",1000)))
        assertFalse(n.discover(far).fix.approaches.any { it.arrived })
        assertFalse(LiveNavigation().discover(frame(north=0.0,stops=listOf(stop())).copy(arrivals=listOf(NaviArrival("wrong",1000)))).fix.approaches.any { it.arrived })
    }
    @Test fun unknownBranchesAndNamesOutsideAreaAreNotMatched() {
        assertNull(LivePlaces.match("청진옥분점",landmark.anchor))
        assertNull(LivePlaces.match("청진옥",GeoPoint(35.0,129.0)))
        assertEquals("cheongjinok",LivePlaces.match("청진옥 본점",landmark.anchor)?.id)
    }
    @Test fun broadIslandIsNeverGivenAnInventedPassagePoint() {
        val place=LivePlaces.all.first { it.id=="yeouido" }
        assertFalse(place.proximityEnabled)
        val f=frame().copy(position=TimedPosition(place.anchor,1000))
        assertTrue(LiveNavigation().discover(f).fix.approaches.none { it.placeId=="yeouido" })
    }
    @Test fun completedStoryIsNotRepeatedAfterAnotherPassage() {
        val n=LiveNavigation();var state=engine.start()
        listOf(500.0,250.0,80.0,0.0,-80.0,-150.0).forEachIndexed { i,d -> state=engine.tick(state,n.discover(frame(1000L+i*4000,d)).fix).state }
        state=engine.listenOnly(engine.afterExplanation(state).state).state
        assertEquals(CuratorPhase.MEMORY,state.phase)
        val again=engine.tick(state,CuratorFix("other",1,listOf(PlaceApproach("cheongjinok",300))))
        assertNull(again.narration);assertEquals(1,again.state.visits.size)
    }
    @Test fun noLocationOrEtaIsAnExplicitUnavailableSignal() {
        assertFalse(LiveNavigation().discover(frame().copy(position=null)).fix.fresh)
    }
    @Test fun wrongCoordinateOrderIsNotAcceptedAsLocalPlace() {
        assertNull(LivePlaces.match("청진옥",GeoPoint(landmark.anchor.longitude,landmark.anchor.latitude)))
    }
}
