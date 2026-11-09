package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionEngineTest {
    private val universal = setOf("notice-one", "notice-color", "notice-light", "notice-outline", "notice-change", "repeated-shapes", "round-shape", "long-line", "neighbor-colors")
    private val easy = setOf("notice-one", "notice-color", "notice-light", "notice-outline", "notice-change")
    private val eligible = mapOf(
        Environment.CITY to universal + setOf("tree", "plants-together", "cloud", "sky-gap", "building", "windows", "lit-window", "lights-in-row", "light-change", "color-patch"),
        Environment.NATURE to universal + setOf("tree", "plants-together", "cloud", "sky-gap", "hill", "water", "color-patch"),
        Environment.TUNNEL to universal + setOf("lights-in-row", "wall-pattern", "light-change"),
        Environment.NIGHT to universal + setOf("building", "windows", "lit-window", "lights-in-row", "light-change"),
    )

    @Test fun createAndResetProduceEmptyIndependentStates() {
        assertEquals(MissionState(), MissionEngine.create())
        assertEquals(listOf("두루 살피기", "도시", "자연", "터널", "밤"), Environment.values().map { it.displayName })
        val started = MissionEngine.advance(MissionEngine.create(Environment.NATURE))
        val found = MissionEngine.advance(started, MissionResult.FOUND)
        assertEquals(1, found.discoveries.size)
        val reset = MissionEngine.create(found.context)
        assertEquals(Environment.NATURE, reset.context)
        assertNull(reset.current)
        assertTrue(reset.history.isEmpty())
        assertTrue(reset.discoveries.isEmpty())
        assertTrue(reset.recent.isEmpty())
        assertEquals(0, reset.round)
    }

    @Test fun startIssuesPromptWithoutClaimingObservation() {
        val state = MissionEngine.advance(MissionEngine.create())
        val current = requireNotNull(state.current)
        assertEquals(1, state.round)
        assertTrue(state.history.isEmpty())
        assertTrue(state.discoveries.isEmpty())
        assertNull(current.parentId)
        assertNotEquals(MissionKind.DETAIL, current.kind)
        assertTrue(current.prompt.isNotEmpty())
    }

    @Test fun contextChangeReplacesTargetWithEligibleRootWithoutAddingDiscovery() {
        for ((context, pool) in eligible) {
            val before = MissionEngine.advance(MissionEngine.create())
            var state = MissionEngine.setContext(before, context)
            assertEquals(context, state.context)
            assertNotEquals(before.current!!.id, state.current!!.id)
            assertEquals(MissionResult.CHANGED, state.history.last().result)
            assertTrue(state.discoveries.isEmpty())
            assertEquals(Environment.ANY, before.context)
            repeat(80) { index ->
                assertTrue("$context: ${state.current!!.id}", state.current!!.id in pool)
                state = MissionEngine.advance(state, if (index % 3 == 0) MissionResult.CHANGED else MissionResult.NEXT)
            }
        }
    }

    @Test fun foundRootOffersOneOptionalDetailAndDetailAddsNoSecondDiscovery() {
        val start = MissionEngine.advance(MissionEngine.create(Environment.NATURE))
        val root = requireNotNull(start.current)
        val detail = MissionEngine.advance(start, MissionResult.FOUND)
        assertEquals(listOf(Discovery(root.label, root.theme)), detail.discoveries)
        assertEquals(MissionKind.DETAIL, detail.current!!.kind)
        assertEquals(root.id, detail.current!!.parentId)
        assertTrue(detail.current!!.hint.contains("지나갔다면"))
        assertEquals(start.recent, detail.recent)
        val next = MissionEngine.advance(detail, MissionResult.FOUND)
        assertNotEquals(MissionKind.DETAIL, next.current!!.kind)
        assertNotEquals(root.id, next.current!!.id)
        assertNull(next.current!!.parentId)
        assertEquals(detail.discoveries, next.discoveries)
        assertEquals(MissionKind.DETAIL, next.history.last().kind)
        assertEquals(MissionResult.FOUND, next.history.last().result)
    }

    @Test fun sameCompletesAComparisonButPreservesTheActualAnswer() {
        var state = MissionEngine.advance(MissionEngine.create(Environment.TUNNEL))
        repeat(100) { if (state.current!!.kind != MissionKind.CHANGE) state = MissionEngine.advance(state, MissionResult.NEXT) }
        val comparison = requireNotNull(state.current)
        assertEquals(MissionKind.CHANGE, comparison.kind)
        val next = MissionEngine.advance(state, MissionResult.SAME)
        assertEquals(MissionResult.SAME, next.history.last().result)
        assertEquals(listOf(Discovery(comparison.label, comparison.theme)), next.discoveries)
        assertEquals(MissionKind.DETAIL, next.current!!.kind)
        assertEquals(comparison.id, next.current!!.parentId)
        assertTrue(state.discoveries.isEmpty())
    }

    @Test fun sameDoesNotChangeFindDetailOrUnstartedMission() {
        val initial = MissionEngine.create()
        val find = MissionEngine.advance(initial)
        assertEquals(MissionKind.FIND, find.current!!.kind)
        val detail = MissionEngine.advance(find, MissionResult.FOUND)
        for (state in listOf(initial, find, detail)) {
            val next = MissionEngine.advance(state, MissionResult.SAME)
            assertEquals(state, next)
            assertNotSame(state, next)
        }
    }

    @Test fun nonSuccessResultsMoveToNewRootWithoutPenaltyOrDiscovery() {
        val unsafe = Regex("실패|틀렸|벌점|감점|다시 찾아|고개를 돌|뒤를 돌아")
        for (result in listOf(MissionResult.SKIP, MissionResult.CHANGED, MissionResult.NEXT)) {
            for (fromDetail in listOf(false, true)) {
                var state = MissionEngine.advance(MissionEngine.create())
                if (fromDetail) state = MissionEngine.advance(state, MissionResult.FOUND)
                val previous = requireNotNull(state.current)
                val next = MissionEngine.advance(state, result)
                assertNotEquals(MissionKind.DETAIL, next.current!!.kind)
                assertNotEquals(previous.parentId ?: previous.id, next.current!!.id)
                assertEquals(state.discoveries, next.discoveries)
                assertEquals(result, next.history.last().result)
                assertFalse(unsafe.containsMatchIn(next.current!!.prompt + next.current!!.hint))
            }
        }
    }

    @Test fun twoConsecutiveSkipsChooseEasyFreshObservationsInEveryEnvironment() {
        for (context in Environment.values()) {
            var state = MissionEngine.advance(MissionEngine.create(context))
            state = MissionEngine.advance(state, MissionResult.SKIP)
            repeat(15) {
                val recent = state.recent.toList()
                state = MissionEngine.advance(state, MissionResult.SKIP)
                assertTrue("$context: ${state.current!!.id}", state.current!!.id in easy)
                assertFalse(state.current!!.id in recent)
                assertTrue(state.discoveries.isEmpty())
            }
        }
    }

    @Test fun recentFourRootsAreAvoidedAndThemesAlternateAcrossContexts() {
        for (context in Environment.values()) {
            var state = MissionEngine.advance(MissionEngine.create(context))
            repeat(100) {
                val previous = state
                state = MissionEngine.advance(state, MissionResult.NEXT)
                assertFalse("$context: ${state.current!!.id}", state.current!!.id in previous.recent)
                assertNotEquals(previous.current!!.theme, state.current!!.theme)
                assertTrue(state.recent.size <= 4)
            }
        }
    }

    @Test fun contextChangesKeepExistingDiscoveriesWithoutRecordingMore() {
        var state = MissionEngine.advance(MissionEngine.advance(MissionEngine.create(Environment.NATURE)), MissionResult.FOUND)
        val discoveries = state.discoveries.toList()
        for (context in Environment.values()) {
            state = MissionEngine.setContext(state, context)
            assertEquals(discoveries, state.discoveries)
            assertNotEquals(MissionKind.DETAIL, state.current!!.kind)
        }
        repeat(20) { state = MissionEngine.advance(state, MissionResult.CHANGED) }
        assertEquals(discoveries, state.discoveries)
    }

    @Test fun previousStatesStayUnchangedAndRecordsRemainBounded() {
        var state = MissionEngine.advance(MissionEngine.create())
        val savedStates = mutableListOf<MissionState>()
        val expectedStates = mutableListOf<MissionState>()
        repeat(150) {
            savedStates += state
            expectedStates += state.copy(history = state.history.toList(), discoveries = state.discoveries.toList(), recent = state.recent.toList())
            val previous = state
            state = MissionEngine.advance(state, MissionResult.FOUND)
            assertNotSame(previous, state)
            assertNotSame(previous.current, state.current)
            assertTrue(state.history.size <= 40)
            assertTrue(state.discoveries.size <= 30)
            assertTrue(state.recent.size <= 4)
        }
        assertEquals(expectedStates, savedStates)
        assertEquals(40, state.history.size)
        assertEquals(30, state.discoveries.size)
        // UI undo restores a whole immutable snapshot, including selection and skip history.
        val restored = savedStates[7]
        assertEquals(MissionEngine.advance(expectedStates[7], MissionResult.SKIP), MissionEngine.advance(restored, MissionResult.SKIP))
    }

    @Test fun callerOwnedListsCannotChangeAnAlreadyReturnedSnapshot() {
        val source = MissionEngine.advance(MissionEngine.create())
        val mutableHistory = source.history.toMutableList()
        val mutableDiscoveries = mutableListOf(Discovery("구름", MissionTheme.SKY))
        val mutableRecent = source.recent.toMutableList()
        val incoming = source.copy(history = mutableHistory, discoveries = mutableDiscoveries, recent = mutableRecent)
        val output = MissionEngine.advance(incoming, MissionResult.SAME)
        mutableDiscoveries.clear()
        mutableRecent.clear()
        assertEquals(listOf(Discovery("구름", MissionTheme.SKY)), output.discoveries)
        assertEquals(source.recent, output.recent)
    }

    @Test fun knownWebPrototypeRoutesRemainIdentical() {
        // Captured from the original JS engine: context and enum order form part of selection.
        val expectedStarts = mapOf(Environment.ANY to "long-line", Environment.CITY to "sky-gap", Environment.NATURE to "notice-color", Environment.TUNNEL to "wall-pattern", Environment.NIGHT to "light-change")
        for ((context, id) in expectedStarts) assertEquals(id, MissionEngine.advance(MissionEngine.create(context)).current!!.id)
        val results = listOf(MissionResult.START, MissionResult.NEXT, MissionResult.CHANGED, MissionResult.SKIP, MissionResult.SKIP, MissionResult.FOUND, MissionResult.FOUND, MissionResult.NEXT)
        fun route(actions: List<MissionResult>): List<String> {
            var state = MissionEngine.create()
            return actions.map { state = MissionEngine.advance(state, it); state.current!!.id }
        }
        assertEquals(listOf("long-line", "notice-change", "notice-outline", "light-change", "notice-light", "notice-light-detail", "cloud", "color-patch"), route(results))
        assertEquals(route(results), route(results))
        assertNotEquals(route(results), route(results.mapIndexed { index, action -> if (index == 0) action else MissionResult.NEXT }))
    }

    @Test fun allTwentyTwoRootsAndTheirDetailsUseBriefSafeKoreanPrompts() {
        val seen = mutableMapOf<String, Mission>()
        for (context in Environment.values()) {
            var state = MissionEngine.create(context)
            repeat(200) { index ->
                state = MissionEngine.advance(state, if (index % 3 == 1) MissionResult.FOUND else MissionResult.NEXT)
                val current = requireNotNull(state.current)
                seen[current.id] = current
            }
        }
        assertEquals(22, seen.values.count { it.kind != MissionKind.DETAIL })
        val unsafe = Regex("센서|카메라|감지했|확인했어|네가 봤|고개를 돌|뒤를 돌아|안전띠|창문을 열|빨리|사라지기 전|태양|글자를 읽")
        for (mission in seen.values) {
            assertTrue(mission.id, mission.prompt.length <= 100)
            assertTrue(mission.id, mission.label.length <= 24)
            assertFalse(mission.id, unsafe.containsMatchIn(mission.prompt + mission.hint))
        }
    }

    @Test fun restoredInputIsCanonicalBoundedAndCannotOverflowRound() {
        val root = MissionEngine.advance(MissionEngine.create()).current!!
        val corrupted = MissionState(
            current = root.copy(label = "wrong", prompt = "wrong"),
            history = List(80) { History(root.id, "wrong", root.kind, root.theme, null, MissionResult.NEXT, -7) },
            discoveries = List(50) { Discovery("🦕".repeat(100), MissionTheme.SHAPE) },
            recent = listOf("unknown", root.id, "cloud", "tree", "water", "hill"),
            round = Int.MAX_VALUE,
        )
        val restored = MissionEngine.advance(corrupted, MissionResult.SAME)
        assertEquals(root, restored.current)
        assertEquals(40, restored.history.size)
        assertTrue(restored.history.all { it.label == root.label && it.round == 0 })
        assertEquals(30, restored.discoveries.size)
        assertEquals("🦕".repeat(80), restored.discoveries.first().label)
        assertEquals(listOf("cloud", "tree", "water", "hill"), restored.recent)
        val advanced = MissionEngine.advance(restored, MissionResult.NEXT)
        assertEquals(Int.MAX_VALUE, advanced.round)
        assertTrue(advanced.current != null)
        val unknown = corrupted.copy(current = root.copy(id = "unknown", parentId = null), round = -1)
        val recovered = MissionEngine.advance(unknown, MissionResult.FOUND)
        assertEquals(30, recovered.discoveries.size)
        assertEquals(1, recovered.round)
        assertNotEquals(MissionKind.DETAIL, recovered.current!!.kind)
    }
}
