package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStagesTest {
    private val all = PhotoStages.byPlace.values.flatten()

    @Test
    fun `a photograph with no author or licence is never shown`() {
        val bare = PhotoStage("lotte-now.jpg", "지금", "뾰족해")

        assertFalse(bare.usable)
        assertFalse(bare.copy(author = "Ox1997cow").usable)
        assertFalse(bare.copy(license = "CC BY 3.0").usable)
        assertTrue(bare.copy(author = "Ox1997cow", license = "CC BY 3.0").usable)
    }

    @Test
    fun `a stage with no file is never usable, however well it is credited`() {
        assertFalse(PhotoStage("", "지금", "도시", "Ox1997cow", "CC BY 3.0").usable)
    }

    @Test
    fun `every photograph the app ships names its author and licence`() {
        assertTrue(all.isNotEmpty())
        all.forEach { stage ->
            assertTrue(stage.usable)
            assertTrue(stage.author.isNotBlank())
            assertTrue(stage.license.isNotBlank())
            assertTrue(stage.page.startsWith("https://"))
        }
    }

    @Test
    fun `the credit line carries author, licence and source together`() {
        val credit = all.first().credit

        assertTrue(credit.contains(all.first().author))
        assertTrue(credit.contains(all.first().license))
        assertTrue(credit.contains("Wikimedia Commons"))
    }

    @Test
    fun `one photograph is enough while the child is still looking`() {
        // Recognising the place out of the window is the whole point of this phase.
        assertEquals(1, PhotoStages.forRecognising("lotte").size)
    }

    @Test
    fun `one photograph does not replace the drawing that explains the place`() {
        // After the place has been passed the child has already seen the real thing. The
        // drawing earns the screen by showing what the window cannot.
        assertTrue(PhotoStages.forExplaining("lotte").isEmpty())
        assertTrue(PhotoStages.byPlace.keys.all { PhotoStages.forExplaining(it).isEmpty() })
    }

    @Test
    fun `a place with no photograph asks for nothing`() {
        // 청진옥 is an alley restaurant with no freely licensed photograph, so it keeps its drawing.
        assertTrue(PhotoStages.forRecognising("cheongjinok").isEmpty())
        assertTrue(PhotoStages.forRecognising("own:1700000000000").isEmpty())
        assertTrue(PhotoStages.forRecognising(null).isEmpty())
    }

    @Test
    fun `every photographed place is a place the app actually knows`() {
        val known = CuratorCatalog.places.map { it.id }.toSet()
        assertTrue(PhotoStages.byPlace.keys.all { it in known })
    }

    @Test
    fun `each file is claimed by one place only`() {
        val assets = PhotoStages.expectedAssets()

        assertEquals(assets.size, assets.toSet().size)
        assertTrue(assets.all { it.endsWith(".jpg") })
    }

    @Test
    fun `the focus point names a place inside the picture`() {
        all.forEach { stage ->
            // Outside this range the view would clamp it back anyway, which would quietly
            // undo a typo instead of showing it. A photograph framed on nothing is the whole
            // problem this field exists to fix.
            assertTrue(stage.focusX in 0f..1f)
            assertTrue(stage.focusY in 0f..1f)
        }
    }

    @Test
    fun `captions speak to a child looking out of a moving window`() {
        all.forEach { stage ->
            assertTrue(stage.year.isNotBlank())
            assertTrue(stage.caption.isNotBlank())
            assertTrue(stage.caption.length <= 28)
        }
    }
}
