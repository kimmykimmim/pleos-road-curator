package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed road, and what it has time to say.
 */
class RoutePlanTest {
    private val route = CuratorRoutes.fixed
    private val places = CuratorCatalog.places
    private val drive = route.endSeconds()
    private val all = RoutePlan.segments(places, route.placeIds)

    @Test
    fun `the road is one road`() {
        assertEquals(1, CuratorRoutes.routes.size)
        assertEquals(listOf("sungnyemun", "gwanghwamun", "cheongjinok"), route.placeIds)
    }

    @Test
    fun `every category offered is one this road can answer`() {
        val fields = all.map { it.field }.toSet()
        // Four categories, and each of them has a place on this road with a real question.
        assertTrue(InterestField.HISTORY in fields)
        assertTrue(InterestField.ART in fields)
        assertTrue(InterestField.LIVING in fields)
        assertTrue(InterestField.SCIENCE in fields)
        // Nature is not one of them: nothing on this road has a nature question written for
        // it, and inventing one is the thing this app does not do.
        assertTrue(InterestField.NATURE !in fields)
    }

    @Test
    fun `a story is never started unless it fits whole`() {
        val plan = RoutePlan.fit(all, available = 60)
        // One minute cannot hold a place's whole story, so it holds none of it.
        assertTrue(RoutePlan.totalSeconds(plan) <= 60)
        plan.forEach { assertTrue(it.seconds <= 60) }
    }

    @Test
    fun `the whole road fits the whole drive`() {
        val plan = RoutePlan.fit(all, drive)

        assertEquals(route.placeIds, plan.map { it.placeId })
        assertTrue(RoutePlan.totalSeconds(plan) < drive)
    }

    @Test
    fun `the child's category decides which story each place tells`() {
        val art = RoutePlan.fit(all, drive, setOf(InterestField.ART))
        val food = RoutePlan.fit(all, drive, setOf(InterestField.LIVING))

        // Both plans visit the same places in the same order: one story per place.
        assertEquals(art.map { it.placeId }, food.map { it.placeId })
        // Where a place can answer the chosen category, it does.
        assertEquals(InterestField.ART, art.first { it.placeId == "sungnyemun" }.field)
        assertEquals(InterestField.LIVING, food.first { it.placeId == "sungnyemun" }.field)
        // 청진옥 has no art question, so it keeps its own rather than inventing one.
        assertTrue(art.first { it.placeId == "cheongjinok" }.field != InterestField.ART)
    }

    @Test
    fun `a longer story takes longer to tell`() {
        val short = RoutePlan.spokenSeconds("짧은 말")
        val long = RoutePlan.spokenSeconds("짧은 말".repeat(20))

        assertTrue(long > short)
        assertEquals(0, RoutePlan.spokenSeconds("   "))
        // English letters carry less than Korean characters, so the same count is quicker.
        assertTrue(
            RoutePlan.spokenSeconds("a".repeat(100), ChildLanguage.ENGLISH) <
                RoutePlan.spokenSeconds("가".repeat(100), ChildLanguage.KOREAN),
        )
    }
}
