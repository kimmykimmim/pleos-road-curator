package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class CuratorPlayTest {
    private val engine=RouteCurator(CuratorCatalog.places)
    private fun explaining(id:String):CuratorState {
        val asked=engine.tick(engine.start(),CuratorFix("r",1,listOf(PlaceApproach(id,300)))).state
        return engine.tick(asked,CuratorFix("r",2,listOf(PlaceApproach(id,-1,true)))).state
    }
    @Test fun everyTopicCompletesItsRoleDuringTheJourney() {
        for(place in CuratorCatalog.places) {
            val story=explaining(place.id)
            val play=engine.afterExplanation(story)
            assertEquals(CuratorPhase.PLAY,play.state.phase)
            assertTrue(play.narration!!.contains("상상"))
            for(choice in place.activity.choices) {
                val done=engine.perform(play.state,choice.id)
                assertEquals(CuratorPhase.MEMORY,done.state.phase)
                assertEquals(choice.id,engine.memory(done.state)!!.activityId)
                assertEquals(choice.label,engine.memory(done.state)!!.activityLabel)
                assertEquals(SeenStatus.UNKNOWN,engine.memory(done.state)!!.seen)
                assertEquals(story.routeId,done.state.routeId)
            }
        }
    }
    @Test fun invalidAndRepeatedChoicesCannotChangeTheCompletedScene() {
        val play=engine.afterExplanation(explaining("lotte")).state
        assertEquals(play,engine.perform(play,"nonexistent").state)
        val done=engine.perform(play,"core").state
        assertEquals(done,engine.perform(done,"belt").state)
    }
    @Test fun ownIdeasAreAcknowledgedWithoutPersistingTheUtteranceInMemory() {
        val play=engine.afterExplanation(explaining("yeouido")).state
        val done=engine.ownIdea(play,"나는 꽃들이 있는 공원을 만들고 싶어").state
        assertEquals("own",engine.memory(done)!!.activityId)
        assertEquals("내 말로 만든 장면",engine.memory(done)!!.activityLabel)
        assertFalse(done.message.contains("꽃들이"))
        assertEquals(play,engine.ownIdea(play,"").state)
        assertEquals(play,engine.ownIdea(play,"가".repeat(161)).state)
    }
    @Test fun spokenRoleKeywordsTriggerTheCorrespondingScene() {
        val examples=mapOf("lotte" to ("바깥 기둥끼리 이어 줄래" to "belt"),
            "building63" to ("안쪽 뼈대를 보여줘" to "skeleton"),
            "yeouido" to ("나무 그늘을 만들래" to "park"),
            "cheongjinok" to ("따뜻하게 인사할래" to "kindness"))
        examples.forEach { (placeId,sample) ->
            val play=engine.afterExplanation(explaining(placeId)).state
            assertEquals(sample.second,engine.memory(engine.respondToPlay(play,sample.first).state)!!.activityId)
        }
    }
    @Test fun roleChoiceCannotStartBeforeTheFactualStory() {
        val waiting=engine.start()
        assertEquals(waiting,engine.perform(waiting,"core").state)
        val story=explaining("lotte")
        assertEquals(story,engine.perform(story,"core").state)
    }
    @Test fun upcomingLandmarkCannotInterruptTheCurrentRole() {
        val play=engine.afterExplanation(explaining("lotte")).state
        val update=engine.tick(play,CuratorFix("r",3,listOf(PlaceApproach("building63",290))))
        assertEquals("lotte",update.state.currentId)
        assertEquals(CuratorPhase.PLAY,update.state.phase)
        assertNull(update.narration)
        val done=engine.perform(update.state,"core").state
        val next=engine.tick(done,CuratorFix("r",4,listOf(PlaceApproach("building63",280))))
        assertEquals("building63",next.state.currentId)
        assertEquals(CuratorPhase.PREDICT,next.state.phase)
    }
    @Test fun listeningOnlyMovesOnWithoutInventingAPerformedRole() {
        val play=engine.afterExplanation(explaining("lotte")).state
        val quiet=engine.listenOnly(play)
        assertEquals(CuratorPhase.MEMORY,quiet.state.phase)
        assertNull(engine.memory(quiet.state)!!.activityId)
        assertNull(quiet.narration)
        assertEquals(play,engine.listenOnly(play.copy(phase=CuratorPhase.EXPLAIN)).state.copy(phase=CuratorPhase.PLAY))
    }
    @Test fun skippingTheRoleKeepsTheStoryWithoutInventingAnActivity() {
        val play=engine.afterExplanation(explaining("cheongjinok")).state
        val skip=engine.skip(play).state
        assertTrue(skip.visits.single().explained)
        assertNull(skip.visits.single().activityId)
    }
}
