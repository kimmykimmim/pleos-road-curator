package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyOwnPlaceTest {
    private val grandma = FamilyVisit(
        placeId = FamilyVisit.ownId(1_700_000_000_000),
        whenLabel = "네가 아기였을 때",
        note = "마당에서 강아지랑 놀았어.",
        ownName = "할머니 집",
        anchor = GeoPoint(37.5665, 126.9780),
    )
    private val album = FamilyAlbum(listOf(grandma))

    @Test
    fun `a guardian place becomes a place the journey can meet`() {
        val place = album.ownCuratorPlaces().single()

        assertEquals("할머니 집", place.name)
        assertEquals(grandma.placeId, place.id)
        // What the guardian wrote is the explanation. The app's own wrapper is only used
        // when they wrote nothing, and here they did.
        assertTrue(place.explanation.contains("마당에서 강아지랑 놀았어."))
        assertFalse(place.explanation.contains("함께 왔던 곳"))
    }

    @Test
    fun `the app adds no history of its own to a guardian place`() {
        val place = album.ownCuratorPlaces().single()

        // Every sentence must be traceable to what the guardian typed or to the fixed wrapper.
        assertEquals("보호자가 차 안에서 적어 둔 가족의 기억", place.sourceTitle)
        assertEquals("", place.sourceUrl)
        assertEquals("가족", place.category)
    }

    @Test
    fun `a guardian place is not drawn as a building the app invented`() {
        val place = album.ownCuratorPlaces().single()

        // The gate artwork belongs to 광화문 and 숭례문. Putting it on grandma's house would be
        // the app inventing what a place it has never seen looks like.
        assertEquals(CuratorVisual.FAMILY, place.visual)
        assertTrue(CuratorCatalog.places.none { it.visual == CuratorVisual.FAMILY })
    }

    @Test
    fun `a guardian place promises no film, because none was made for it`() {
        assertEquals("", album.ownCuratorPlaces().single().mediaKey)
        assertTrue(CuratorCatalog.places.all { it.mediaKey.isNotBlank() })
    }

    @Test
    fun `the role reads correctly once the screen adds its own prefix`() {
        val role = album.ownCuratorPlaces().single().activity.role

        // The screen renders "04  오늘의 ${'$'}role", so a role starting with 오늘의 would stutter.
        assertFalse(role.startsWith("오늘의"))
        assertTrue(CuratorCatalog.places.none { it.activity.role.startsWith("오늘의") })
    }

    @Test
    fun `a guardian place can be found by proximity like any other`() {
        val located = album.ownLocatedPlaces().single()

        assertEquals(grandma.placeId, located.id)
        assertEquals(grandma.anchor, located.anchor)
        assertTrue(located.proximityEnabled)
        assertEquals(FamilyAlbum.OWN_RADIUS_METERS, located.radiusMeters, 0.0)
    }

    @Test
    fun `a guardian place never announces minutes it was not given`() {
        val engine = RouteCurator(CuratorCatalog.places + album.ownCuratorPlaces())
        // Proximity supplies no ETA, so the only way to reach it is by being near.
        val turn = engine.tick(CuratorState(phase = CuratorPhase.WAITING),
            CuratorFix("r", 1, listOf(PlaceApproach(grandma.placeId, etaSeconds = null, nearby = true))))

        assertEquals(CuratorPhase.WATCH, turn.state.phase)
        assertFalse(turn.narration!!.contains("분 뒤"))
    }

    @Test
    fun `its story is told once, as the place's own, not twice`() {
        val engine = RouteCurator(CuratorCatalog.places + album.ownCuratorPlaces())
        val explaining = CuratorState(phase = CuratorPhase.EXPLAIN, currentId = grandma.placeId,
            visits = listOf(PlaceMemory(grandma.placeId, explained = true)))

        // The explanation already is the family's memory, so there is no second family beat.
        assertNull(album.recall(album.ownCuratorPlaces().single()))
        assertEquals(CuratorPhase.PLAY, engine.afterExplanation(explaining, album).state.phase)
    }

    @Test
    fun `a catalog place still gets its separate family beat`() {
        val lotte = CuratorCatalog.places.first { it.id == "lotte" }
        val mixed = album.with(FamilyVisit("lotte", "작년에"))
        val engine = RouteCurator(CuratorCatalog.places + mixed.ownCuratorPlaces())
        val explaining = CuratorState(phase = CuratorPhase.EXPLAIN, currentId = "lotte",
            visits = listOf(PlaceMemory("lotte", explained = true)))

        assertTrue(mixed.recall(lotte)!!.contains("작년에 우리 가족이 함께 왔던 곳"))
        assertEquals(CuratorPhase.FAMILY, engine.afterExplanation(explaining, mixed).state.phase)
    }

    @Test
    fun `guardian ids cannot shadow a place the app already has`() {
        assertTrue(grandma.placeId.startsWith(FamilyVisit.OWN_PREFIX))
        assertTrue(CuratorCatalog.places.none { it.id.startsWith(FamilyVisit.OWN_PREFIX) })
        assertTrue(LivePlaces.all.none { it.id.startsWith(FamilyVisit.OWN_PREFIX) })
    }

    @Test
    fun `a guardian place survives being written and read back`() {
        val restored = FamilyAlbum.decode(FamilyAlbum.encode(listOf(grandma)))

        assertEquals(listOf(grandma), restored)
        assertTrue(restored.single().isOwn)
    }

    @Test
    fun `a guardian place with no usable position is dropped, not left unreachable`() {
        val text = """{"version":2,"visits":[{"placeId":"own:1","whenLabel":"작년에","ownName":"할머니 집"}]}"""

        assertTrue(FamilyAlbum.decode(text).isEmpty())
    }

    @Test
    fun `records written before guardian places still read correctly`() {
        val old = """{"version":1,"visits":[{"placeId":"lotte","whenLabel":"작년에","note":""}]}"""
        val restored = FamilyAlbum.decode(old)

        assertEquals(listOf(FamilyVisit("lotte", "작년에")), restored)
        assertFalse(restored.single().isOwn)
    }

    @Test
    fun `a record missing its name or position is not treated as a guardian place`() {
        assertFalse(grandma.copy(ownName = null).isOwn)
        assertFalse(grandma.copy(anchor = null).isOwn)
        assertFalse(grandma.copy(anchor = GeoPoint(0.0, 0.0)).isOwn)
    }

    @Test
    fun `the demo visits guardian places after the route's own`() {
        val route = CuratorRoutes.routes.first()
        val stops = route.stops(listOf(grandma.placeId))

        assertEquals(route.placeIds + grandma.placeId, stops)
        assertTrue(route.endSeconds(listOf(grandma.placeId)) > route.endSeconds())
        assertEquals(route.placeIds, route.stops())
    }

    @Test
    fun `the demo never lists the same place twice`() {
        val route = CuratorRoutes.routes.first()

        assertEquals(route.placeIds, route.stops(listOf(route.placeIds.first())))
    }
}
