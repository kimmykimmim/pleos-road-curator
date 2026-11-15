package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clippings each place's explanation is made of.
 */
class SceneScrapsTest {
    private val all = SceneScraps.byPlace.values.flatMap { it.scraps }

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
    fun `every place on the route explains itself with its own photographs`() {
        CuratorRoutes.fixed.placeIds.forEach { id ->
            assertTrue("$id has no clipping", SceneScraps.of(id).isNotEmpty())
            assertTrue("$id has no title", SceneScraps.titleFor(id) != null)
        }
    }

    @Test
    fun `no two places share a photograph`() {
        // 숭례문 and 광화문 shared one scene, and the photograph in it was taken at 경복궁.
        // A child was shown somewhere else and told it was what they were looking at.
        val used = SceneScraps.byPlace.mapValues { it.value.scraps.map { scrap -> scrap.asset } }
        used.forEach { (place, assets) ->
            used.forEach { (other, theirs) ->
                if (place != other) {
                    assertTrue("$place and $other share a photograph",
                        assets.none { it in theirs })
                }
            }
        }
    }

    @Test
    fun `each place's explanation has its own title`() {
        val titles = SceneScraps.byPlace.values.map { it.title }

        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `a guardian's own place borrows nobody's photograph`() {
        assertTrue(SceneScraps.of("own:1700000000000").isEmpty())
        assertTrue(SceneScraps.of(null).isEmpty())
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
