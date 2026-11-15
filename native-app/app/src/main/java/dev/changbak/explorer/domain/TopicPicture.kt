package dev.changbak.explorer.domain

/**
 * The small drawing that appears for a moment while a question is being asked.
 *
 * A six year old reads very little of what is on this screen. A picture beside the words is
 * how they find out what this turn is about before anyone has read a sentence to them, which
 * is why there is one at all.
 *
 * Everything here is about **which** picture, and the rule is short: it must be a picture of
 * what the app is talking about right now. A dinosaur belongs on screen when the app has just
 * said "공룡 좋아한댔지?" and nowhere else. During a question about why a building is shaped
 * the way it is, the picture is a brush, not a dinosaur — a child who loves dinosaurs is not
 * thinking about dinosaurs at that moment, and a dinosaur there is just noise over the view.
 */
enum class TopicPicture {
    DINOSAUR, SPACE, ANIMAL, VEHICLE, PALETTE, MUSIC, ROBOT,
    GEAR, HOURGLASS, BRUSH, LEAF, BOWL,
    /** The places themselves, which is what the picture is about nearly all of the time. */
    GATE, TOWER, GLASS, ISLAND, HEART;

    companion object {
        /** What a child's favourite thing looks like. */
        fun of(topic: FavouriteTopic): TopicPicture = when (topic) {
            FavouriteTopic.DINOSAUR -> DINOSAUR
            FavouriteTopic.SPACE -> SPACE
            FavouriteTopic.ANIMALS -> ANIMAL
            FavouriteTopic.VEHICLES -> VEHICLE
            FavouriteTopic.DRAWING -> PALETTE
            FavouriteTopic.MUSIC -> MUSIC
            FavouriteTopic.ROBOTS -> ROBOT
        }

        /**
         * What the place itself looks like.
         *
         * This is the one that matters. The picture used to come from the kind of question, so
         * a question about how many people walked through 숭례문 — a question about living —
         * put a bowl of soup beside a photograph of a gate. A child reads the picture before
         * anybody reads them the words, and a bowl at a gate is simply wrong.
         */
        fun of(visual: CuratorVisual): TopicPicture = when (visual) {
            CuratorVisual.TOWER -> TOWER
            CuratorVisual.GLASS -> GLASS
            CuratorVisual.ISLAND -> ISLAND
            CuratorVisual.RECIPE -> BOWL
            CuratorVisual.GATE -> GATE
            CuratorVisual.FAMILY -> HEART
        }

        /** What a kind of question looks like, when nothing more particular is being said. */
        fun of(field: InterestField): TopicPicture = when (field) {
            InterestField.SCIENCE -> GEAR
            InterestField.HISTORY -> HOURGLASS
            InterestField.ART -> BRUSH
            InterestField.NATURE -> LEAF
            InterestField.LIVING -> BOWL
        }

        /**
         * The picture for this turn.
         *
         * @param spokenFavourite the child's favourite thing **only if the app is about to
         *   mention it**. A favourite that is merely set in the guardian's screen is not a
         *   reason to draw it; the line about it being said is.
         * @param field the kind of question being asked, which is what the picture falls back
         *   to. Null for a turn that is asking nothing, and then there is no picture: a drawing
         *   that appears for no reason teaches a child to stop looking at the screen.
         */
        fun pick(
            spokenFavourite: FavouriteTopic?,
            field: InterestField?,
            visual: CuratorVisual? = null,
        ): TopicPicture? = spokenFavourite?.let(::of) ?: visual?.let(::of) ?: field?.let(::of)
    }
}
