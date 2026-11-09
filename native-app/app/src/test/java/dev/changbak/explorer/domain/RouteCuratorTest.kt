package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class RouteCuratorTest {
    private val engine=RouteCurator(CuratorCatalog.places)
    private fun fix(seconds:Int, seq:Long=1, passed:Boolean=false, id:String="r", observable:Boolean=true)=
        CuratorFix(id,seq,listOf(PlaceApproach("sungnyemun",seconds,passed,observable)))
    private fun asked()=engine.tick(engine.start(),fix(300)).state
    private fun nearby()=engine.tick(asked(),fix(20,2)).state
    @Test fun doesNotAskBeforeFiveMinutes() {
        assertEquals(CuratorPhase.WAITING,engine.tick(engine.start(),fix(301)).state.phase)
    }
    @Test fun asksAtFiveMinutesOnceAndWaitsForTheEncounter() {
        val question=engine.tick(engine.start(),fix(300))
        assertEquals(CuratorPhase.PREDICT,question.state.phase)
        assertTrue(question.narration!!.contains("5분"))
        val repeated=engine.tick(question.state,fix(290,2))
        assertNull(repeated.narration)
        assertEquals(1,repeated.state.visits.size)
    }
    @Test fun timeIsForEachPlaceRatherThanDestination() {
        val t=engine.tick(engine.start(),CuratorFix("r",1,listOf(PlaceApproach("gwanghwamun",600),PlaceApproach("sungnyemun",300))))
        assertEquals("sungnyemun",t.state.currentId)
    }
    @Test fun absentResponseDoesNotBlockAutomaticExplanation() {
        val turn=engine.tick(asked(),fix(-2,2,true))
        assertEquals(CuratorPhase.EXPLAIN,turn.state.phase)
        assertNull(engine.memory(turn.state)!!.guess)
        assertEquals(SeenStatus.UNKNOWN,engine.memory(turn.state)!!.seen)
    }
    @Test fun seeingCannotBeClaimedWhileFarAway() {
        assertEquals(asked(),engine.seen(asked(),true).state)
    }
    @Test fun nearPointInvitesObservationButDoesNotExplainYet() {
        assertEquals(CuratorPhase.WATCH,nearby().phase)
        assertFalse(engine.memory(nearby())!!.explained)
    }
    @Test fun reportedSightStartsExplanationOnce() {
        val t=engine.seen(nearby(),true)
        assertEquals(CuratorPhase.EXPLAIN,t.state.phase)
        assertEquals(SeenStatus.SEEN,engine.memory(t.state)!!.seen)
        assertNull(engine.tick(t.state,fix(-1,3,true)).narration)
    }
    @Test fun notVisibleIsNotMarkedAsSeenButPassageStillExplains() {
        val unseen=engine.seen(nearby(),false).state
        assertEquals(CuratorPhase.WATCH,unseen.phase)
        val passed=engine.tick(unseen,fix(-1,3,true))
        assertEquals(SeenStatus.NOT_SEEN,engine.memory(passed.state)!!.seen)
        assertTrue(passed.narration!!.contains("보지 못했어도"))
    }
    @Test fun originalPredictionAndReflectionRemainTogether() {
        val guess=engine.predict(asked(),"사람들이 드나들려고").state
        val explain=engine.tick(guess,fix(-1,2,true)).state
        val compare=engine.afterExplanation(explain).state
        val remembered=engine.perform(compare,"welcome").state
        assertEquals("사람들이 드나들려고",engine.memory(remembered)!!.guess)
        assertEquals("welcome",engine.memory(remembered)!!.activityId)
        assertEquals(CuratorPhase.MEMORY,remembered.phase)
    }
    @Test fun unknownAnswerIsAcceptedWithoutScoring() {
        val explain=engine.tick(asked(),fix(-1,2,true)).state
        val play=engine.afterExplanation(explain).state
        val t=engine.skip(play)
        assertEquals(CuratorPhase.WAITING,t.state.phase)
        assertNull(engine.memory(play)!!.activityId)
    }
    @Test fun rerouteRemovesUpcomingQuestionWithoutCallingItVisited() {
        val t=engine.tick(asked(),CuratorFix("new-route",1,emptyList()))
        assertEquals(CuratorPhase.WAITING,t.state.phase)
        assertNull(t.state.currentId)
        assertFalse(t.state.visits.single().explained)
        assertEquals(SeenStatus.UNKNOWN,t.state.visits.single().seen)
    }
    @Test fun staleOrOutOfOrderEventsNeverTriggerAudio() {
        val state=asked()
        assertEquals(state,engine.tick(state,fix(-1,0,true)).state)
        assertEquals(state,engine.tick(state,fix(-1,2,true).copy(fresh=false)).state)
    }
    @Test fun pauseOrConnectionLossCannotConfirmSight() {
        val lost=engine.navigationLost(nearby())
        assertEquals(lost,engine.seen(lost,true).state)
    }
    @Test fun skippedPlaceDoesNotAskAgainWhenEtaOscillates() {
        val skipped=engine.skip(asked()).state
        assertNull(engine.tick(skipped,fix(299,2)).narration)
    }
    @Test fun invisibleCandidateIsNotSelected() {
        assertEquals(CuratorPhase.WAITING,engine.tick(engine.start(),fix(300,observable=false)).state.phase)
    }
    @Test fun unknownPlaceIsNeverGivenInventedContent() {
        val t=engine.tick(engine.start(),CuratorFix("r",1,listOf(PlaceApproach("unknown-place",280))))
        assertEquals(CuratorPhase.WAITING,t.state.phase)
        assertNull(t.narration)
    }
    @Test fun anotherPointCannotInterruptAnExplanation() {
        val state=engine.tick(asked(),fix(-1,2,true)).state
        val t=engine.tick(state,CuratorFix("r",3,listOf(PlaceApproach("gwanghwamun",290))))
        assertEquals("sungnyemun",t.state.currentId)
        assertEquals(CuratorPhase.EXPLAIN,t.state.phase)
        assertNull(t.narration)
    }
    @Test fun completedTripDoesNotRestartOnARepeatedLocation() {
        val ended=engine.finish(asked()).state
        assertEquals(ended,engine.tick(ended,fix(280,2)).state)
    }
}
