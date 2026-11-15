package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clippings the explanation is made of.
 */
class SceneScrapsTest {
    private val all = SceneScraps.byVisual.values.flatten()

    @Test
    fun `every clipping names its author and licence`() {
        assertTrue(all.isNotEmpty())
        all.forEach { scrap ->
            assertTrue(scrap.asset, scrap.usable)
            assertTrue(scrap.page.startsWith("https://"))
            assertTrue(scrap.credit.contains(scrap.author))
            assertTrue(scrap.credit.contains(scrap.license))
        }
    }

    @Test
    fun `an uncredited photograph is never shown`() {
        val bare = SceneScrap(asset = "x.jpg", caption = "x")

        assertFalse(bare.usable)
        assertFalse(bare.copy(author = "Someone").usable)
        assertTrue(bare.copy(author = "Someone", license = "CC0").usable)
    }

    @Test
    fun `every explanation but a family's own is made of photographs`() {
        CuratorVisual.values().filter { it != CuratorVisual.FAMILY }.forEach { visual ->
            assertTrue("$visual has no clipping", SceneScraps.of(visual).isNotEmpty())
        }
        // A family's place is theirs. The app has no photograph of it and does not borrow one.
        assertTrue(SceneScraps.of(CuratorVisual.FAMILY).isEmpty())
    }

    @Test
    fun `a photograph of somewhere else says where it is`() {
        // The gate scene is shown at 숭례문 as well as 광화문, and the photograph is the guard
        // ceremony at 경복궁. Unlabelled, a child is looking at one place being told it is
        // another.
        val gate = SceneScraps.of(CuratorVisual.GATE).single()

        assertTrue(gate.caption.contains("경복궁"))
        assertTrue(gate.caption.contains("예시"))
        assertTrue(gate.captionEn.contains("Gyeongbokgung"))
    }

    @Test
    fun `captions stay short enough to be read from the back seat`() {
        all.forEach { assertTrue(it.caption, it.caption.length <= 24) }
    }

    @Test
    fun `what is ringed is somewhere inside the photograph`() {
        all.mapNotNull { it.mark }.forEach { mark ->
            assertTrue(mark.x in 0f..1f)
            assertTrue(mark.y in 0f..1f)
            assertTrue(mark.r > 0f && mark.r < .5f)
        }
    }

    @Test
    fun `every file is listed once`() {
        val assets = SceneScraps.assets()

        assertEquals(assets.size, assets.toSet().size)
        assertTrue(assets.all { it.endsWith(".jpg") })
    }
}
