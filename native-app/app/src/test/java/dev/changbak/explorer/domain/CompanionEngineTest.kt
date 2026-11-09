package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class CompanionEngineTest {
    private val friend = TravelState(body = CloudFriend(nickname = "뭉치"))
    private fun open() = CompanionEngine.open(CompanionState(), friend)
    private fun say(text: String, state: CompanionState = open().state) = CompanionEngine.respond(state, text, friend)

    @Test fun openingIntroducesTheExistingFriendAndInvitesADestination() {
        val turn = open()
        assertTrue(turn.reply.contains("뭉치"))
        assertTrue(turn.reply.contains("오늘 어디"))
        assertEquals(TalkTopic.DESTINATION, turn.state.pending)
        assertNull(turn.state.destination)
    }

    @Test fun destinationThenMoodThenPlanFormsAConversation() {
        val destination = say("할머니 집에 가")!!
        assertEquals("할머니 집", destination.state.destination)
        assertEquals(TalkTopic.MOOD, destination.state.pending)
        val feeling = say("보고 싶어서 신나", destination.state)!!
        assertEquals(TalkMood.EXCITED, feeling.state.mood)
        assertEquals(TalkTopic.PLAN, feeling.state.pending)
        assertTrue(feeling.reply.contains("보고 싶어서"))
        val plan = say("안아주고 싶어", feeling.state)!!
        assertEquals(TalkTopic.NONE, plan.state.pending)
        assertEquals("할머니 집", plan.state.destination)
        assertFalse(plan.reply.contains('?'))
    }

    @Test fun shortKnownPlaceAnswersAreAllowedOnlyWhenAsked() {
        assertEquals("놀이공원", say("놀이공원")!!.state.destination)
        assertEquals("할머니 집", say("할머니집이야")!!.state.destination)
        assertNull(say("놀이공원", CompanionState()))
    }

    @Test fun aNewDestinationReplacesTheOldOneWithoutInventingAMood() {
        val grandma = say("할머니 집에 가")!!.state
        val corrected = say("아니, 할머니 집이 아니라 동물원에 가", grandma)!!
        assertEquals("동물원", corrected.state.destination)
        assertEquals(TalkMood.NEUTRAL, corrected.state.mood)
        assertTrue(say("내가 어디 간다고 했지?", corrected.state)!!.reply.contains("동물원"))
    }

    @Test fun cancellingADestinationRemovesItFromRecall() {
        val grandma = say("할머니 집에 가")!!.state
        val cancelled = say("할머니 집 안 가", grandma)!!
        assertNull(cancelled.state.destination)
        assertEquals(TalkTopic.DESTINATION, cancelled.state.pending)
        assertTrue(say("내가 어디 간다고 했지?", cancelled.state)!!.reply.contains("아직"))
    }

    @Test fun landscapeReportsCannotBecomeDestinationsOrSocialPlans() {
        listOf("구름이 동글동글해", "나뭇잎이 살랑여", "강물이 반짝여", "새가 날아가", "저기 고래 같아", "꼬리가 길어", "나무가 보여").forEach {
            assertNull(it, say(it))
            assertNull(it, say(it, CompanionState("할머니 집", TalkMood.EXCITED, TalkTopic.PLAN)))
        }
    }

    @Test fun moodQuestionsAreNotRecordedAsTheChildsMood() {
        assertNull(say("내일은 신날까?"))
        assertNull(say("졸리면 어떻게 해?"))
        assertEquals(TalkMood.NEUTRAL, say("너는 어때?")!!.state.mood)
    }

    @Test fun reciprocalQuestionAcknowledgesTheFriendsActualAppearance() {
        val decorated = friend.copy(memories = listOf(TravelMemory(1, ScenerySource.LEAVES, "살랑이는 나뭇잎", FriendTrait.SWAY_EARS)))
        val state = CompanionState("할머니 집", TalkMood.EXCITED, TalkTopic.PLAN)
        val reply = CompanionEngine.respond(state, "너는 어때?", decorated)!!
        assertTrue(reply.reply.contains("살랑이는 귀"))
        assertEquals(state, reply.state)
        assertFalse(reply.reply.contains('?'))
        listOf("기분 어때?", "기분은 어때?", "오늘 기분 어때?").forEach {
            val omittedSubject = CompanionEngine.respond(state, it, decorated)
            assertNotNull(it, omittedSubject)
            assertEquals(it, state, omittedSubject!!.state)
            assertTrue(it, omittedSubject.reply.contains("살랑이는 귀"))
        }
    }

    @Test fun repeatingTheSameFeelingDoesNotRepeatThePlanQuestion() {
        val first = say("신나")!!
        val second = say("정말 신나", first.state)!!
        assertEquals(first.state, second.state)
        assertFalse(second.reply.contains('?'))
    }

    @Test fun sleepyAndRestRequestsStopAskingQuestions() {
        val sleepy = say("좀 졸려")!!
        assertEquals(TalkMood.SLEEPY, sleepy.state.mood)
        assertEquals(TalkTopic.NONE, sleepy.state.pending)
        assertFalse(sleepy.reply.contains('?'))
        val rest = say("쉬고 싶어", CompanionState("할머니 집", TalkMood.HAPPY, TalkTopic.PLAN))!!
        assertEquals(TalkTopic.NONE, rest.state.pending)
        assertEquals(TalkMood.HAPPY, rest.state.mood)
        assertFalse(rest.reply.contains('?'))
    }

    @Test fun sadnessGetsAnOptionalChoiceWithoutInventingItsCause() {
        val sad = say("좀 속상해")!!
        assertEquals(TalkMood.SAD, sad.state.mood)
        assertEquals(1, sad.reply.count { it == '?' })
        assertFalse(sad.reply.contains("누가"))
        val quiet = say("조용히 있고 싶어", sad.state)!!
        assertEquals(TalkTopic.NONE, quiet.state.pending)
    }

    @Test fun moodRecallUsesOnlyTheStoredMood() {
        val state = say("보고 싶어서 신나")!!.state
        val reply = say("내 기분이 어땠지?", state)!!
        assertTrue(reply.reply.contains("신나"))
        assertEquals(state, reply.state)
        assertTrue(say("내 기분이 어땠지?", CompanionState())!!.reply.contains("기억하지 못해"))
    }

    @Test fun negativeMoodWordsDoNotBecomePositiveMemories() {
        assertNull(say("안 신나"))
        assertNull(say("안 졸려"))
        assertEquals(TalkMood.SAD, say("기분이 안 좋아")!!.state.mood)
    }

    @Test fun unknownQuestionsAndSensitiveDestinationDetailsAreNotStored() {
        assertNull(say("달까지 얼마나 멀어?"))
        assertNull(say("서울시 강남구 123번지로 가"))
        assertNull(say("휴대폰번호는 01012345678이야"))
        assertNull(say("내 이름은 민수야"))
    }

    @Test fun reopeningAndRepeatingADestinationKeepsSessionContext() {
        val state = CompanionState("할머니 집", TalkMood.EXCITED, TalkTopic.NONE)
        val reopened = CompanionEngine.open(state, friend)
        assertEquals("할머니 집", reopened.state.destination)
        assertFalse(reopened.reply.contains("오늘 어디"))
        val repeated = say("할머니 집에 가", reopened.state)!!
        assertEquals(reopened.state, repeated.state)
        assertFalse(repeated.reply.contains('?'))
    }
}
