package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class CloudPlayEngineTest {
    private fun empty() = CloudPlayEngine.start(CloudPlayEngine.create()).state
    private fun say(state: TravelState, text: String) = CloudPlayEngine.observe(state, text).state
    private fun rabbit() = say(empty(), "저 구름 토끼 같아")

    @Test fun expectationIsOptionalAndNeverCreatesACharacter() {
        val initial = empty()
        assertTrue(initial.cloudWorld!!.introPending)
        val answer = say(initial, "할머니 만나는 게 기대돼")
        assertEquals(JourneyExpectation.FAMILY, answer.cloudWorld!!.expectation)
        assertFalse(answer.cloudWorld.introPending)
        assertNull(answer.body)
        assertEquals("가족을 만나러 가던 날", CloudPlayEngine.journeyTitle(answer))
    }
    @Test fun skippingTheQuestionOrImmediatelyDescribingACloudBothWork() {
        val skipped = say(empty(), "구름부터 볼래")
        assertFalse(skipped.cloudWorld!!.introPending)
        assertNull(skipped.cloudWorld.expectation)
        assertEquals(CloudForm.RABBIT, rabbit().body!!.form)
        assertFalse(rabbit().cloudWorld!!.introPending)
    }
    @Test fun aMemoryIsChosenByTheChildRatherThanPreselected() {
        val state = rabbit()
        assertNull(CloudPlayEngine.favorite(state))
        val remembered = say(state, "이거 기억할래")
        assertEquals(CloudForm.RABBIT, CloudPlayEngine.favorite(remembered)!!.figure.form)
        assertEquals(state.cloudWorld!!.clouds, remembered.cloudWorld!!.clouds)
        assertNotNull(CloudPlayEngine.favorite(CloudPlayEngine.finish(remembered).state))
    }
    @Test fun theChildCanRememberABackgroundCloudWithoutChangingTheSpeaker() {
        val state = say(say(rabbit(), "고래 같아"), "하늘에 띄우자")
        val remembered = say(state, "고래 구름 기억해줘")
        assertEquals(CloudRole.BACKGROUND, CloudPlayEngine.favorite(remembered)!!.role)
        assertEquals(CloudForm.RABBIT, remembered.body!!.form)
        assertNull(CloudPlayEngine.favorite(say(remembered, "기억에서 빼줘")))
    }
    @Test fun finishingDoesNotSilentlyPickAFavorite() {
        val turn = CloudPlayEngine.finish(rabbit())
        assertNull(CloudPlayEngine.favorite(turn.state))
        assertTrue(turn.reply!!.contains("가장 기억나"))
    }

    @Test fun beginningHasNeitherCharacterNorScenery() {
        val state = empty()
        assertNull(state.body); assertTrue(state.memories.isEmpty())
        assertTrue(state.cloudWorld!!.clouds.isEmpty()); assertNull(state.cloudWorld.draft)
    }
    @Test fun helloOrAPlanCannotInventTheFirstFriend() {
        for (text in listOf("안녕", "오늘 할머니 집에 가", "오늘 신나", "구름이 있어", "이름은 뭉치야")) {
            assertNull(text, say(empty(), text).body)
            assertTrue(text, say(empty(), text).cloudWorld!!.clouds.isEmpty())
        }
    }
    @Test fun firstObservedShapeBecomesACoPassengerAndAsksAboutTheWindow() {
        val turn = CloudPlayEngine.observe(empty(), "저 구름 토끼 같아")
        assertEquals(CloudForm.RABBIT, turn.state.body!!.form)
        assertEquals(CloudRole.FRIEND, turn.state.cloudWorld!!.clouds.single().role)
        assertTrue(turn.reply!!.contains("창밖"))
        assertEquals(ScenerySource.CLOUD, turn.state.memories.single().source)
    }
    @Test fun aShapeWithoutTheWordCloudStillWorksInTheEmptySky() {
        assertEquals(CloudForm.WHALE, say(empty(), "고래 같아").body!!.form)
        assertTrue(say(empty(), "길쭉해").body!!.features.contains(CloudFeature.LONG))
    }
    @Test fun aNewCloudWaitsForARoleWithoutReplacingTheRabbit() {
        val first = rabbit()
        val next = say(first, "저쪽 구름은 고래 같아")
        assertEquals(first.body, next.body)
        assertEquals(first.cloudWorld!!.clouds, next.cloudWorld!!.clouds)
        assertEquals(CloudForm.WHALE, next.cloudWorld.draft!!.form)
    }
    @Test fun backgroundChoiceKeepsTheSpeakingFriend() {
        val first = rabbit()
        val next = say(say(first, "저쪽 구름은 고래 같아"), "하늘에 띄우자")
        assertEquals(first.body, next.body)
        assertEquals(2, next.cloudWorld!!.clouds.size)
        assertEquals(CloudRole.BACKGROUND, next.cloudWorld.clouds.last().role)
        assertNull(next.cloudWorld.draft)
    }
    @Test fun secondFriendCanBeCreatedAndEitherFriendSelected() {
        val state = say(say(rabbit(), "고래 구름이 보여 고래 같아"), "친구로 만들자")
        assertEquals(2, CloudPlayEngine.friends(state).size)
        assertEquals(CloudForm.WHALE, state.body!!.form)
        val back = say(state, "토끼랑 이야기할래")
        assertEquals(CloudForm.RABBIT, back.body!!.form)
        assertEquals(state.cloudWorld!!.clouds, back.cloudWorld!!.clouds)
    }
    @Test fun aBackgroundCanBeTheFirstCreationWithoutMakingAnAvatar() {
        val state = say(empty(), "고래 같은 구름을 하늘에 띄워줘")
        assertNull(state.body)
        assertEquals(CloudRole.BACKGROUND, state.cloudWorld!!.clouds.single().role)
    }
    @Test fun anExplicitRoleDoesNotRequireASecondUtterance() {
        val state = say(rabbit(), "고래 구름을 친구로 만들어줘")
        assertEquals(2, CloudPlayEngine.friends(state).size)
        assertEquals(CloudForm.WHALE, state.body!!.form)
    }
    @Test fun everydayPlansNeverBecomeScenery() {
        val state = rabbit()
        for (text in listOf("오늘은 공원에서 놀 거야", "고래 인형을 갖고 놀 거야", "오늘 구름빵 먹고 싶어", "내 기분은 구름 같아", "토끼 좋아해", "할머니랑 노는 것 같아")) {
            val turn = CloudPlayEngine.observe(state, text)
            assertEquals(text, state, turn.state)
            assertEquals(text, TravelOutcome.CHAT, turn.outcome)
            assertNull(text, turn.reply)
        }
    }
    @Test fun questionsDoNotCreateCloudsOrRenameCharacters() {
        val state = rabbit()
        for (text in listOf("구름이 왜 토끼 같아?", "이름이 뭐야?", "고래도 좋아해?")) {
            assertEquals(state, say(state, text))
        }
    }
    @Test fun namesBelongOnlyToTheSelectedFriend() {
        val named = say(rabbit(), "이름은 뭉치야")
        assertEquals("뭉치", named.body!!.nickname)
        val second = say(say(named, "고래 같아"), "친구로 만들자")
        assertNull(second.body!!.nickname)
        assertEquals("뭉치", say(second, "뭉치랑 이야기할래").body!!.nickname)
    }
    @Test fun correctionChangesOnlyTheMostRecentlyHandledCloud() {
        val original = rabbit()
        val background = say(say(original, "고래 같아"), "하늘에 띄우자")
        val corrected = say(background, "그거 고래가 아니라 공룡이야")
        assertEquals(original.body, corrected.body)
        assertEquals(2, corrected.cloudWorld!!.clouds.size)
        assertEquals(CloudForm.DINOSAUR, corrected.cloudWorld.clouds.last().figure.form)
        assertEquals(CloudRole.BACKGROUND, corrected.cloudWorld.clouds.last().role)
    }
    @Test fun correctionOfAPreviewDoesNotCommitIt() {
        val pending = say(rabbit(), "고래 같아")
        val corrected = say(pending, "아니 고래가 아니라 공룡이야")
        assertEquals(1, corrected.cloudWorld!!.clouds.size)
        assertEquals(CloudForm.DINOSAUR, corrected.cloudWorld.draft!!.form)
    }
    @Test fun restClearsThePreviewAndPreservesEveryCommittedCloud() {
        val pending = say(rabbit(), "고래 같아")
        val paused = CloudPlayEngine.rest(pending).state
        assertNull(paused.cloudWorld!!.draft)
        assertEquals(pending.cloudWorld!!.clouds, paused.cloudWorld.clouds)
        assertEquals(paused, say(paused, "구름이 공룡 같아"))
    }
    @Test fun explicitCancellationDoesNotCreateACloud() {
        val pending = say(rabbit(), "고래 같아")
        val cancelled = say(pending, "나중에")
        assertNull(cancelled.cloudWorld!!.draft)
        assertEquals(1, cancelled.cloudWorld.clouds.size)
    }
    @Test fun repeatingAnObservationDoesNotDuplicateTheFriend() {
        assertEquals(1, say(rabbit(), "저 구름 토끼 같아").cloudWorld!!.clouds.size)
        assertNotNull(say(rabbit(), "다른 구름도 토끼 같아").cloudWorld!!.draft)
    }
    @Test fun unknownShapesKeepTheirNameWithoutClaimingASpecificMesh() {
        val state = say(empty(), "구름이 솜사탕 같아")
        assertEquals(CloudForm.OTHER, state.body!!.form)
        assertEquals("솜사탕", state.body.label)
    }
    @Test fun finishingAndStartingNewHaveDifferentEffects() {
        val trip = say(say(rabbit(), "고래 같아"), "하늘에 띄우자")
        val finished = CloudPlayEngine.finish(trip).state
        assertEquals(CloudPhase.KEEPSAKE, finished.phase)
        assertEquals(trip.cloudWorld!!.clouds, finished.cloudWorld!!.clouds)
        assertTrue(CloudPlayEngine.create().cloudWorld!!.clouds.isEmpty())
    }
}
