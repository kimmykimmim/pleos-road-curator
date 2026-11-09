package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class TravelEngineTest {
    private fun ready() = TravelEngine.start(TravelEngine.create()).state
    private fun say(text: String, state: TravelState = ready()) = TravelEngine.observe(state, text)
    private fun current(state: TravelState, part: FriendPart) = state.memories.lastOrNull { it.trait.part == part }
    private fun formed(): TravelState = say("강물이 반짝여", say("나뭇잎이 살랑살랑 움직여", say("구름이 동글동글해").state).state).state

    @Test fun severalLandscapesBuildOneFriendAndRetainEarlierParts() {
        val state = formed()
        assertTrue(state.body!!.features.contains(CloudFeature.ROUND))
        assertEquals(ScenerySource.CLOUD, current(state, FriendPart.BODY)?.source)
        assertEquals(FriendTrait.SWAY_EARS, current(state, FriendPart.EARS)?.trait)
        assertEquals(ScenerySource.LEAVES, current(state, FriendPart.EARS)?.source)
        assertEquals(FriendTrait.SPARKLE_TAIL, current(state, FriendPart.TAIL)?.trait)
        assertEquals(ScenerySource.RIVER, current(state, FriendPart.TAIL)?.source)
        assertEquals(FriendTrait.SWAY_STEP, current(state, FriendPart.STEP)?.trait)
    }

    @Test fun independentObservationsCanArriveTogether() {
        val result = say("구름이 동글동글하고 나뭇잎이 살랑살랑하고 강물이 반짝여")
        assertEquals(TravelOutcome.OBSERVED, result.outcome)
        assertEquals(setOf(FriendPart.BODY, FriendPart.EARS, FriendPart.STEP, FriendPart.TAIL), result.state.memories.map { it.trait.part }.toSet())
    }

    @Test fun aWhaleBodySurvivesTreesAndWater() {
        val whale = say("고래 같아").state
        val state = say("물은 반짝여", say("나무가 보여", whale).state).state
        assertEquals(CloudForm.WHALE, state.body?.form)
        assertEquals(FriendTrait.LEAF_EARS, current(state, FriendPart.EARS)?.trait)
        assertEquals(FriendTrait.SPARKLE_TAIL, current(state, FriendPart.TAIL)?.trait)
    }

    @Test fun aBodyIsNotRequiredBeforeAnEarObservation() {
        val state = say("나뭇잎이 살랑여").state
        assertNull(state.body)
        assertNotNull(current(state, FriendPart.EARS))
    }

    @Test fun repeatingAReportDoesNotAddMemories() {
        val state = formed()
        val again = say("강물이 반짝여", state)
        assertEquals(TravelOutcome.UNCHANGED, again.outcome)
        assertEquals(state, again.state)
    }

    @Test fun sourceQuestionsReferToActualMemoriesWithoutChangingThem() {
        val state = formed()
        listOf("왜 꼬리가 반짝여?", "꼬리는 어디서 왔어?", "왜 꼬리 모습이 이렇게 됐어?").forEach {
            val turn = say(it, state)
            assertEquals(TravelOutcome.CHAT, turn.outcome)
            assertEquals(state, turn.state)
            assertTrue(turn.reply!!.contains("강"))
        }
        assertTrue(say("귀는 어디서 왔어?", state).reply!!.contains("나뭇잎"))
        assertTrue(say("어떻게 걸어?", state).reply!!.contains("나뭇잎"))
    }

    @Test fun correctingASourceKeepsItsIdTraitAndMemoryCount() {
        val before = formed()
        val old = current(before, FriendPart.TAIL)!!
        val corrected = say("그거 강이 아니라 바다였어", before)
        val tail = current(corrected.state, FriendPart.TAIL)!!
        assertEquals(TravelOutcome.CORRECTED, corrected.outcome)
        assertEquals(old.id, tail.id)
        assertEquals(old.trait, tail.trait)
        assertEquals(ScenerySource.SEA, tail.source)
        assertEquals(before.memories.size, corrected.state.memories.size)
        assertTrue(say("꼬리는 어디서 왔어?", corrected.state).reply!!.contains("바다"))
        assertFalse(tail.detail.contains("강"))
    }

    @Test fun correctingAnUnseenSourceNeverCreatesAReport() {
        val state = say("나무가 보여").state
        val turn = say("그거 강 아니고 바다였어", state)
        assertEquals(state, turn.state)
        assertEquals(TravelOutcome.CHAT, turn.outcome)
    }

    @Test fun questionsCannotManufactureFormsOrObservations() {
        val state = formed()
        listOf("고래 같아?", "바다가 반짝이면 날 수 있어?", "왜 우주는 커?", "산은 몇 살이야?", "내 친구는 공룡일까?").forEach {
            val turn = say(it, state)
            assertEquals(it, state.body, turn.state.body)
            assertEquals(it, state.memories, turn.state.memories)
            assertEquals(it, TravelOutcome.CHAT, turn.outcome)
        }
    }

    @Test fun flightNeedsConsentAndAnImaginedOrigin() {
        val state = formed()
        val offered = say("날 수 있어?", state)
        assertEquals(FriendChoice.ADD_WINGS, offered.state.pending)
        assertEquals(state.memories, offered.state.memories)
        val accepted = say("응", offered.state)
        assertEquals(ScenerySource.IMAGINATION, current(accepted.state, FriendPart.WINGS)?.source)
        assertEquals(FriendChoice.NONE, accepted.state.pending)
        assertTrue(say("날개는 어디서 왔어?", accepted.state).reply!!.contains("상상"))
        assertFalse(say("날개는 어디서 왔어?", accepted.state).reply!!.contains("새에서"))
    }

    @Test fun rejectingOrAbandoningAWingOfferDoesNotCreateWings() {
        val original = formed()
        val offer = say("날 수 있어?", original).state
        val rejected = say("아니", offer)
        assertEquals(original.memories, rejected.state.memories)
        assertEquals(FriendChoice.NONE, rejected.state.pending)
        val namedQuestion = say("너 이름이 뭐야?", offer)
        assertEquals(FriendChoice.NONE, namedQuestion.state.pending)
        assertEquals(original.memories, say("응", namedQuestion.state).state.memories)
    }

    @Test fun aDirectWingRequestIsImaginationAndDoesNotInventABirdOrCloud() {
        val requested = say("날개 달아줘")
        assertNull(requested.state.body)
        assertEquals(1, requested.state.memories.size)
        assertEquals(ScenerySource.IMAGINATION, requested.state.memories.single().source)
        assertEquals(requested.state, say("날개 붙여줘", requested.state).state)
        val bird = say("새가 날아가").state
        assertEquals(bird, say("날개 달아줘", bird).state)
        assertEquals(CloudForm.DOG, say("강아지 같아").state.body?.form)
        assertFalse(say("강아지 같아").state.memories.any { it.source == ScenerySource.RIVER })
    }

    @Test fun anObservedBirdCreatesWingsWithABirdOrigin() {
        val state = say("새가 날아가").state
        assertEquals(ScenerySource.BIRD, current(state, FriendPart.WINGS)?.source)
        val turn = say("날 수 있어?", state)
        assertEquals(FriendChoice.NONE, turn.state.pending)
        assertTrue(turn.reply!!.contains("새"))
        assertEquals(state.memories, turn.state.memories)
    }

    @Test fun aBirdShapedCloudDoesNotPretendARealBirdWasSeen() {
        val state = say("새 같아").state
        assertEquals(CloudForm.BIRD, state.body?.form)
        assertFalse(state.memories.any { it.source == ScenerySource.BIRD })
    }

    @Test fun aComparedShapeUsesItsExplicitSubjectNotAnInventedCloud() {
        val tree = say("나무가 고래 같아").state
        assertEquals(CloudForm.WHALE, tree.body?.form)
        assertEquals(ScenerySource.TREE, current(tree, FriendPart.BODY)?.source)
        assertFalse(tree.memories.any { it.source == ScenerySource.CLOUD })
        val unspecified = say("고래 같아").state
        assertEquals(ScenerySource.IMAGINATION, current(unspecified, FriendPart.BODY)?.source)
        assertFalse(say("내가 뭘 봤지?", unspecified).reply!!.contains("날개"))
        val cloud = say("구름이 고래 같아").state
        val transformed = say("토끼 같아", cloud).state
        assertEquals(CloudForm.RABBIT, transformed.body?.form)
        assertEquals(ScenerySource.CLOUD, current(transformed, FriendPart.BODY)?.source)
    }

    @Test fun latestColorWinsWithoutErasingBodyEarsOrTail() {
        val before = formed()
        val green = say("꽃 색이 초록색이야", before).state
        val blue = say("하늘이 파란색이야", green).state
        assertEquals(FriendTrait.BLUE, current(blue, FriendPart.COLOR)?.trait)
        assertEquals(ScenerySource.SKY, current(blue, FriendPart.COLOR)?.source)
        listOf(FriendPart.BODY, FriendPart.EARS, FriendPart.TAIL).forEach { assertEquals(current(before, it), current(blue, it)) }
    }

    @Test fun nicknameAndIdentityRepliesDoNotInventASceneryMemory() {
        val before = formed()
        val named = say("이름은 뭉치야", before)
        assertEquals("뭉치", named.state.body?.nickname)
        assertEquals(before.memories, named.state.memories)
        assertTrue(say("너 이름이 뭐야", named.state).reply!!.contains("뭉치"))
        assertEquals(named.state, say("넌 누구야?", named.state).state)
    }

    @Test fun recallUsesStoredSourcesIncludingACorrectedOne() {
        val state = say("그거 강 아니고 바다였어", formed()).state
        val reply = say("내가 뭘 봤지?", state).reply!!
        assertTrue(reply.contains("구름"))
        assertTrue(reply.contains("나뭇잎"))
        assertTrue(reply.contains("바다"))
        assertFalse(reply.contains("강"))
        assertEquals(state, say("우리가 본 거 기억해?", state).state)
    }

    @Test fun currentPartsSurvivePruningAndIdsStayUnique() {
        var state = formed()
        val originalBody = current(state, FriendPart.BODY)
        val originalEars = current(state, FriendPart.EARS)
        repeat(60) { state = say(if (it % 2 == 0) "강물이 반짝여" else "바다가 반짝여", state).state }
        assertEquals(24, state.memories.size)
        assertEquals(state.memories.size, state.memories.map { it.id }.distinct().size)
        assertEquals(originalBody, current(state, FriendPart.BODY))
        assertEquals(originalEars, current(state, FriendPart.EARS))
        assertTrue(state.memories.maxOf { it.id } > 24)
    }

    @Test fun keepsakeCanConverseAndGrowWithoutLeavingThePicture() {
        val keepsake = TravelEngine.finish(formed()).state
        assertEquals(CloudPhase.KEEPSAKE, say("내가 뭘 봤지?", keepsake).state.phase)
        assertEquals(CloudPhase.KEEPSAKE, say("새가 날아가", keepsake).state.phase)
        val rest = say("쉬자", keepsake)
        assertEquals(TravelOutcome.REST, rest.outcome)
        assertEquals(CloudPhase.KEEPSAKE, rest.state.phase)
    }

    @Test fun restingNeedsExplicitResumeAndClearsPendingChoice() {
        val offered = say("날 수 있어?", formed()).state
        val rest = TravelEngine.rest(offered).state
        assertEquals(FriendChoice.NONE, rest.pending)
        assertEquals(rest, say("새가 날아가", rest).state)
        assertEquals(rest, say("응", rest).state)
        assertEquals(CloudPhase.WATCHING, say("다시시작", rest).state.phase)
    }

    @Test fun blankSpeechAndUnknownFactsDoNotCreateMemories() {
        val state = formed()
        assertNull(say(" ", state).reply)
        assertEquals(state, say("지구 나이는 몇 살이야?", state).state)
        assertTrue(say("지구 나이는 몇 살이야?", state).reply!!.contains("모르겠어"))
        assertFalse(state.toString().contains("나뭇잎이 살랑살랑 움직여"))
    }

    @Test fun similarSyllablesAndNegationDoNotInventSources() {
        listOf("새 친구", "강아지가 귀여워", "배고파", "동물이 좋아", "나무가 아니야", "새가 안 보여").forEach {
            val turn = say(it)
            assertTrue(it, turn.state.memories.isEmpty())
        }
    }
}
