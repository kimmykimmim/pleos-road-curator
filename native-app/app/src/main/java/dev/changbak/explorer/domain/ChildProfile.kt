package dev.changbak.explorer.domain

import java.util.Locale

/**
 * The kind of thing a child leans towards.
 *
 * This picks which question a place asks, not how a place is explained. A child who loves
 * history is not told that 롯데월드타워 is a historical building; they are asked the history
 * question at the place that has one. Bending every place to every interest would mean
 * inventing an angle, and an invented angle is the first step to an invented fact.
 */
enum class InterestField(val id: String) {
    SCIENCE("science"),
    HISTORY("history"),
    ART("art"),
    NATURE("nature"),
    /** How people lived and live: shops, neighbourhoods, work, food. */
    LIVING("living");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Something the child is into right now, chosen from a list rather than typed.
 *
 * A fixed list because each one has a written line for the places where a true connection
 * exists. A guardian typing "공룡" into a box would make the app look clever and then have
 * nothing honest to say about it at 숭례문.
 */
enum class FavouriteTopic(val id: String) {
    DINOSAUR("dinosaur"),
    SPACE("space"),
    ANIMALS("animals"),
    VEHICLES("vehicles"),
    DRAWING("drawing"),
    MUSIC("music"),
    ROBOTS("robots");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/**
 * The language the child speaks in the car.
 *
 * [locale] is handed to the Pleos speech services, so this is not a label on translated text:
 * it decides what the car says out loud and what it listens for. A language is only listed here
 * once its content pack is complete, because a half-translated journey would drop a child into
 * a language they do not read in the middle of a sentence.
 */
enum class ChildLanguage(val id: String, val locale: Locale) {
    KOREAN("ko", Locale.KOREA),
    ENGLISH("en", Locale.US);

    companion object {
        val DEFAULT = KOREAN
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * What the guardian told the app about their child.
 *
 * All of it is optional and all of it stays in this car. Empty fields mean the journey behaves
 * exactly as it did before anyone filled this in: every place asks its own question.
 */
data class ChildProfile(
    val fields: Set<InterestField> = emptySet(),
    val favourites: Set<FavouriteTopic> = emptySet(),
    val language: ChildLanguage = ChildLanguage.DEFAULT,
) {
    val hasInterests: Boolean get() = fields.isNotEmpty()

    /** True when this place's question should be chosen for [field]. */
    fun likes(field: InterestField?) = field != null && field in fields

    companion object {
        /** More than this and the guardian is describing a curriculum, not a child. */
        const val MAX_FAVOURITES = 3

        fun encode(profile: ChildProfile): String = listOf(
            profile.fields.joinToString(",") { it.id },
            profile.favourites.joinToString(",") { it.id },
            profile.language.id,
        ).joinToString("|")

        fun decode(raw: String?): ChildProfile {
            val parts = raw.orEmpty().split("|")
            return ChildProfile(
                fields = parts.getOrNull(0).orEmpty().split(",").mapNotNull(InterestField::of).toSet(),
                favourites = parts.getOrNull(1).orEmpty().split(",").mapNotNull(FavouriteTopic::of)
                    .take(MAX_FAVOURITES).toSet(),
                language = ChildLanguage.of(parts.getOrNull(2)),
            )
        }
    }
}
