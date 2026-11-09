package dev.changbak.explorer.domain

import org.junit.Assert.*
import org.junit.Test

class CloudEngineTest {
    private fun watching() = CloudEngine.start(CloudEngine.create()).state
    private fun say(text: String, state: CloudState = watching()) = CloudEngine.observe(state, text)

    @Test fun startingAndWaitingNeverInventACloud() {
        val start = CloudEngine.start(CloudEngine.create())
        assertEquals(CloudPhase.WATCHING, start.state.phase)
        assertNull(start.state.friend)
        assertTrue(start.state.memories.isEmpty())
        val silence = say("  ", start.state)
        assertEquals(start.state, silence.state)
        assertNull(silence.reply)
        assertEquals(CloudOutcome.IGNORED, silence.outcome)
    }

    @Test fun childCanNameAFormWithoutMakingAStory() {
        listOf("고래야", "저 구름은 고래 같아", "고래처럼 보여", "저구름은고래같아").forEach {
            val result = say(it)
            assertEquals(it, CloudForm.WHALE, result.state.friend?.form)
            assertEquals(CloudOutcome.OBSERVED, result.outcome)
            assertEquals(1, result.state.memories.size)
        }
    }

    @Test fun correctionUsesOnlyTheAffirmedShape() {
        listOf("고래 아니고 토끼야", "고래가 아니라 토끼처럼 보여", "고래말고토끼", "아니야, 토끼 같아").forEach {
            assertEquals(it, CloudForm.RABBIT, say(it).state.friend?.form)
        }
        listOf("고래 아니야", "고래가 아니에요", "고래 같지 않아", "고래처럼 보이지 않아", "토끼는 없어").forEach {
            val turn = say(it)
            assertNull(it, turn.state.friend)
            assertTrue(it, turn.state.memories.isEmpty())
        }
    }

    @Test fun disappearingCloudIsARestNotASuccessOrFailure() {
        val whale = say("고래야").state
        listOf("구름이 안 보여", "안보여", "구름이없어", "가려졌어", "터널이야").forEach {
            val turn = say(it, whale)
            assertEquals(it, CloudOutcome.REST, turn.outcome)
            assertEquals(CloudPhase.RESTING, turn.state.phase)
            assertEquals(whale.friend, turn.state.friend)
            assertEquals(whale.memories, turn.state.memories)
        }
    }

    @Test fun unchangedCloudIsAlwaysAcceptedWithoutAnotherMemory() {
        val whale = say("고래야").state
        listOf("그대로야", "아직그대로야", "똑같아", "비슷해", "달라진거없어", "안바뀌었어", "고래야").forEach {
            val turn = say(it, whale)
            assertEquals(it, CloudOutcome.UNCHANGED, turn.outcome)
            assertEquals(whale, turn.state)
        }
    }

    @Test fun aFeatureCanBeTheFirstObservationAndANameIsOptional() {
        val turn = say("몽실몽실해")
        assertEquals(CloudForm.CLOUD, turn.state.friend?.form)
        assertEquals(listOf(CloudFeature.FLUFFY), turn.state.friend?.features)
        assertNull(turn.state.friend?.nickname)
        assertEquals(CloudForm.CLOUD, say("동그란거같아").state.friend?.form)
    }

    @Test fun featureUpdatesRetainIdentityAndReplaceContradictions() {
        var state = say("고래야").state
        state = say("이름은 뭉치야", state).state
        state = say("엄청 커", state).state
        state = say("이제 작아졌어", state).state
        assertEquals(listOf(CloudFeature.SMALL), state.friend?.features)
        state = say("길쭉해", state).state
        state = say("동그랗게 보여", state).state
        assertTrue(state.friend!!.features.contains(CloudFeature.ROUND))
        assertFalse(state.friend!!.features.contains(CloudFeature.LONG))
        assertEquals("뭉치", state.friend!!.nickname)
    }

    @Test fun aLongTailDoesNotMakeTheWholeCloudLong() {
        val round = say("동글동글해").state
        val turn = say("꼬리가 엄청 길어졌어", round)
        assertTrue(turn.state.friend!!.features.contains(CloudFeature.ROUND))
        assertTrue(turn.state.friend!!.features.contains(CloudFeature.LONG_TAIL))
        assertFalse(turn.state.friend!!.features.contains(CloudFeature.LONG))
    }

    @Test fun explicitFeatureNegationRemovesAnOldFeature() {
        val winged = say("날개가 있어").state
        val noWings = say("날개가 없어", winged)
        assertEquals(CloudOutcome.OBSERVED, noWings.outcome)
        assertFalse(noWings.state.friend!!.features.contains(CloudFeature.WINGS))
        val notRound = say("동그랗지 않아", say("동그랗게 보여").state)
        assertFalse(notRound.state.friend!!.features.contains(CloudFeature.ROUND))
        assertNull(say("날개가 없어").state.friend)
    }

    @Test fun aHiddenPartRemovesOnlyThatFeatureInsteadOfResting() {
        val whale = say("꼬리가 길어", say("날개가 있어", say("고래야").state).state).state
        val noWings = say("날개가 안 보여", whale)
        assertEquals(CloudPhase.WATCHING, noWings.state.phase)
        assertEquals(CloudOutcome.OBSERVED, noWings.outcome)
        assertFalse(noWings.state.friend!!.features.contains(CloudFeature.WINGS))
        assertTrue(noWings.state.friend!!.features.contains(CloudFeature.LONG_TAIL))
        listOf("꼬리가 없어", "꼬리가 안 보여").forEach {
            val noTail = say(it, whale)
            assertEquals(it, CloudPhase.WATCHING, noTail.state.phase)
            assertFalse(it, noTail.state.friend!!.features.contains(CloudFeature.LONG_TAIL))
        }
        listOf("구름이 안 보여", "이제 안 보여", "안 보여", "건물 때문에 안 보여", "날개도 안 보이고 구름도 안 보여").forEach {
            assertEquals(it, CloudOutcome.REST, say(it, whale).outcome)
        }
    }

    @Test fun anUnspacedDescriptionKeepsTheKnownFormAndItsExplicitFeatures() {
        listOf("날개달린용같아", "날개가달린용같아", "저구름은날개달린용같아", "날개 달린 용 같아").forEach {
            val turn = say(it)
            assertEquals(it, CloudForm.DRAGON, turn.state.friend?.form)
            assertTrue(it, turn.state.friend!!.features.contains(CloudFeature.WINGS))
        }
        val longTail = say("꼬리가긴고래같아")
        assertEquals(CloudForm.WHALE, longTail.state.friend?.form)
        assertTrue(longTail.state.friend!!.features.contains(CloudFeature.LONG_TAIL))
        assertTrue(longTail.reply!!.contains("꼬리"))
        assertTrue(longTail.reply.contains("고래"))
        assertTrue(say("날개없는용같아").state.friend!!.features.isEmpty())
    }

    @Test fun aNewFormDoesNotInheritUnconfirmedFeaturesButKeepsItsNickname() {
        var state = say("고래야").state
        state = say("이름은 뭉치야", state).state
        state = say("꼬리가 길어", state).state
        val dragon = say("이제 용 같아", state)
        assertEquals(CloudForm.DRAGON, dragon.state.friend?.form)
        assertEquals("뭉치", dragon.state.friend?.nickname)
        assertTrue(dragon.state.friend!!.features.isEmpty())
        assertEquals(CloudForm.WHALE, state.friend!!.form)
    }

    @Test fun repeatedNewFriendWordsDoNotAccumulateDuplicates() {
        val state = say("새 친구는 토끼야").state
        val repeated = say("새 친구는 토끼야", state)
        assertEquals(CloudOutcome.UNCHANGED, repeated.outcome)
        assertEquals(state, repeated.state)
        assertNull(say("새 친구").state.friend)
    }

    @Test fun repeatingOneOfSeveralFeaturesDoesNotReorderOrDuplicateTheMemory() {
        val state = say("몽실몽실해", say("동글동글해").state).state
        val repeated = say("동글동글해", state)
        assertEquals(CloudOutcome.UNCHANGED, repeated.outcome)
        assertEquals(state, repeated.state)
    }

    @Test fun nicknameIsNotMistakenForAChangeOfShapeOrFeature() {
        val whale = say("고래야").state
        val named = say("이름은 몽실이야", whale)
        assertEquals(CloudForm.WHALE, named.state.friend?.form)
        assertEquals("몽실", named.state.friend?.nickname)
        assertTrue(named.state.friend!!.features.isEmpty())
        assertEquals(CloudOutcome.UNCHANGED, say("이름은 몽실이야", named.state).outcome)
    }

    @Test fun aShortUnknownNounCanBeStoredButItsUtteranceCannot() {
        val turn = say("저 구름은 감자처럼 보여")
        assertEquals(CloudForm.OTHER, turn.state.friend?.form)
        assertEquals("감자", turn.state.friend?.label)
        assertFalse(turn.state.toString().contains("저 구름은"))
        assertEquals("감자", say("저구름은감자같아").state.friend?.label)
        assertTrue(say("아주아주엄청나게커다란수박같아").state.friend!!.label.length <= 12)
    }

    @Test fun normalSpeechDoesNotInventObjectsFromAmbiguousSyllables() {
        listOf("새 친구", "배고파", "두 개야", "그 기능을 이용해", "정말 멋지다", "재미있는 것 같아").forEach {
            val turn = say(it)
            assertNull(it, turn.state.friend)
            assertEquals(it, CloudOutcome.IGNORED, turn.outcome)
        }
    }

    @Test fun helpDoesNotChangeTheChildsObservationOrAppendAQuestionTask() {
        val whale = say("고래야").state
        val turn = say("잘 모르겠어", whale)
        assertEquals(CloudOutcome.HELP, turn.outcome)
        assertEquals(whale, turn.state)
        assertFalse(turn.reply!!.contains("찾아"))
    }

    @Test fun restingAndFinishedSessionsRequireExplicitResume() {
        val whale = say("고래야").state
        val rest = CloudEngine.rest(whale).state
        assertEquals(rest, say("토끼야", rest).state)
        val resumed = say("다시보여", rest)
        assertEquals(CloudOutcome.RESUME, resumed.outcome)
        assertEquals(whale.friend, resumed.state.friend)
        val finished = CloudEngine.finish(resumed.state).state
        assertEquals(CloudPhase.KEEPSAKE, finished.phase)
        assertEquals(finished, say("토끼야", finished).state)
    }

    @Test fun finishingAnEmptyTripDoesNotInventAKeepsake() {
        val turn = CloudEngine.finish(watching())
        assertEquals(CloudOutcome.FINISH, turn.outcome)
        assertNull(turn.state.friend)
        assertTrue(turn.state.memories.isEmpty())
    }

    @Test fun repeatAndUndoAreSignalsForTheHostNotFakeStateChanges() {
        val state = say("고래야").state
        assertEquals(state, say("잘못말했어", state).state)
        assertEquals(CloudOutcome.UNDO, say("잘못말했어", state).outcome)
        val repeat = say("다시들려줘", state)
        assertEquals(CloudOutcome.REPEAT, repeat.outcome)
        assertEquals(state, repeat.state)
        assertTrue(repeat.reply!!.contains("고래"))
    }

    @Test fun memoryIsBoundedAndInputStateStaysImmutable() {
        val original = watching()
        var state = original
        repeat(40) { state = say(if (it % 2 == 0) "고래야" else "토끼야", state).state }
        assertEquals(12, state.memories.size)
        assertNull(original.friend)
        assertTrue(original.memories.isEmpty())
    }

    @Test fun veryLongInputIsNotStoredAsANameOrShape() {
        val turn = say("아주긴이야기".repeat(50))
        assertEquals(CloudOutcome.IGNORED, turn.outcome)
        assertNull(turn.state.friend)
        assertTrue(turn.state.memories.isEmpty())
    }
}
