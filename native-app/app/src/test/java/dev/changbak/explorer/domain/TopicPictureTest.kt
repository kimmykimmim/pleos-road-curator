package dev.changbak.explorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule that decides which small picture a turn gets.
 *
 * The whole value of the picture is that it matches what is being said. A dinosaur over an
 * art question is not a nice extra, it is the app looking like it is not listening to itself.
 */
class TopicPictureTest {
    @Test
    fun `the favourite wins only when its line is being said`() {
        // The predict step hands the favourite over because the line about it is on screen.
        assertEquals(
            TopicPicture.DINOSAUR,
            TopicPicture.pick(FavouriteTopic.DINOSAUR, InterestField.ART),
        )
        // Any other step hands over null, and the question's own field decides instead.
        assertEquals(TopicPicture.BRUSH, TopicPicture.pick(null, InterestField.ART))
    }

    @Test
    fun `no question means no picture`() {
        assertNull(TopicPicture.pick(null, null))
    }

    @Test
    fun `every favourite and every field has a picture of its own`() {
        val favourites = FavouriteTopic.entries.map(TopicPicture::of)
        val fields = InterestField.entries.map(TopicPicture::of)
        // Two topics sharing a picture would make the screen say the wrong thing for one of
        // them, which is worse than that one having no picture at all.
        assertEquals(favourites.size, favourites.toSet().size)
        assertEquals(fields.size, fields.toSet().size)
        assertEquals(favourites.size + fields.size, (favourites + fields).toSet().size)
    }

    @Test
    fun `the picture is of the place, not of the kind of question`() {
        // 숭례문 asked a question about the people who walked through it — a "living" question
        // — and the screen put a bowl of soup beside a photograph of a gate.
        assertEquals(TopicPicture.GATE,
            TopicPicture.pick(null, InterestField.LIVING, CuratorVisual.GATE))
        assertEquals(TopicPicture.TOWER,
            TopicPicture.pick(null, InterestField.HISTORY, CuratorVisual.TOWER))
        assertEquals(TopicPicture.BOWL,
            TopicPicture.pick(null, InterestField.HISTORY, CuratorVisual.RECIPE))
    }

    @Test
    fun `a favourite that is actually being said still wins`() {
        // The line "공룡 좋아한댔지" is on screen, so the dinosaur is what the app is talking
        // about at that moment.
        assertEquals(TopicPicture.DINOSAUR,
            TopicPicture.pick(FavouriteTopic.DINOSAUR, InterestField.SCIENCE, CuratorVisual.TOWER))
    }

    @Test
    fun `every place in the catalogue has a picture of its own kind`() {
        CuratorCatalog.places.forEach { place ->
            assertEquals(TopicPicture.of(place.visual),
                TopicPicture.pick(null, place.rounds.first().field, place.visual))
        }
    }
}
