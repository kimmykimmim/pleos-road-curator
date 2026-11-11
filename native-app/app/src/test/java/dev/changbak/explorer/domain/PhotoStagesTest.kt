package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStagesTest {
    private fun cleared(asset: String, year: String, caption: String) =
        PhotoStage(asset, year, caption, source = "서울역사박물관", license = "공공누리 제1유형")

    @Test
    fun `a photograph with no stated source is never usable`() {
        val stage = PhotoStage("yeouido-1-sandbar.jpg", "아주 오래전", "모래섬이었어요")

        assertFalse(stage.usable)
        assertFalse(stage.copy(source = "서울역사박물관").usable)
        assertFalse(stage.copy(license = "공공누리 제1유형").usable)
        assertTrue(stage.copy(source = "서울역사박물관", license = "공공누리 제1유형").usable)
    }

    @Test
    fun `a stage with no file is never usable, however well it is credited`() {
        assertFalse(PhotoStage("", "지금", "도시", "서울기록원", "공공누리 제1유형").usable)
    }

    @Test
    fun `the app ships no photographs, so no place shows any yet`() {
        // Every stage in the catalog is still waiting for a cleared file and a source. Until
        // then each place keeps the drawing, which is the honest thing to show.
        PhotoStages.byPlace.keys.forEach { assertEquals(emptyList<PhotoStage>(), PhotoStages.of(it)) }
    }

    @Test
    fun `one photograph alone does not replace a drawing that explains something`() {
        val one = listOf(cleared("a.jpg", "지금", "오늘의 모습"))
        // A single picture shows no change, which is the only reason to take the screen.
        assertTrue(one.all { it.usable })
        assertTrue(one.size < 2)
    }

    @Test
    fun `a place with no entry asks for nothing`() {
        assertEquals(emptyList<PhotoStage>(), PhotoStages.of("own:1700000000000"))
        assertEquals(emptyList<PhotoStage>(), PhotoStages.of(null))
    }

    @Test
    fun `every staged place is a place the app actually knows`() {
        val known = CuratorCatalog.places.map { it.id }.toSet()
        assertTrue(PhotoStages.byPlace.keys.all { it in known })
    }

    @Test
    fun `every staged place has at least a before and an after`() {
        PhotoStages.byPlace.forEach { (place, stages) ->
            assertTrue("$place 는 변화를 보여 줄 두 장이 필요합니다", stages.size >= 2)
        }
    }

    @Test
    fun `each file is asked for once, so two places cannot claim the same photograph`() {
        val assets = PhotoStages.expectedAssets()

        assertEquals(assets.size, assets.toSet().size)
        assertTrue(assets.all { it.endsWith(".jpg") })
    }

    @Test
    fun `captions speak to a child, not to an archivist`() {
        PhotoStages.byPlace.values.flatten().forEach { stage ->
            assertTrue(stage.year.isNotBlank())
            assertTrue(stage.caption.isNotBlank())
            // Short enough to read from the back seat while the car is moving.
            assertTrue(stage.caption.length <= 28)
        }
    }
}
