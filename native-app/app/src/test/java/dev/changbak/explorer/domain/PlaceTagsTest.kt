package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line under a place's name.
 */
class PlaceTagsTest {
    @Test
    fun `every place in both packs has tags of its own`() {
        ChildLanguage.values().forEach { language ->
            CuratorCatalog.places(language).forEach { place ->
                val tags = PlaceTags.of(place.id, language, place.category)
                assertTrue("${place.id} in $language has no tags", tags.isNotEmpty())
                tags.forEach { assertTrue("$it is not a hashtag", it.startsWith("#") && it.length > 2) }
            }
        }
    }

    @Test
    fun `tags differ by place, so the line is never a generic one`() {
        val lines = CuratorCatalog.places.map { PlaceTags.line(it.id) }
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test
    fun `a guardian's own place is recognised by its category rather than its id`() {
        val mine = PlaceTags.of("family-1763100000000", ChildLanguage.KOREAN, "가족")
        assertEquals(listOf("#우리가족의기억"), mine)
    }

    @Test
    fun `a place the app knows nothing about claims nothing`() {
        assertTrue(PlaceTags.of("somewhere-else").isEmpty())
        assertEquals("", PlaceTags.line(null))
    }
}
