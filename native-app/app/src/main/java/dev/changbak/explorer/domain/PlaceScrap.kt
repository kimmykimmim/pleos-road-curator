package dev.changbak.explorer.domain

/**
 * The places whose real photograph can be shown with its background taken away.
 *
 * A drawing can say how a building stands up, but it cannot tell a child which building to
 * look for. For that it has to be the real one. These are the licensed photographs the app
 * already carries, with the subject lifted out of them — the same pixels, minus the sky.
 *
 * Two places are deliberately absent. The 63빌딩 photograph is a gold tower against a bright
 * haze and the subject will not separate from it; 청진옥 has no freely licensed photograph at
 * all. Both keep their explanatory drawing, because a wrong picture of a place is worse for a
 * child looking out of a window than an honest diagram of one.
 */
object PlaceScrap {
    private val byPlace = mapOf(
        "lotte" to "cutouts/lotte.png",
        "sungnyemun" to "cutouts/sungnyemun.png",
        "gwanghwamun" to "cutouts/gwanghwamun.png",
        "yeouido" to "cutouts/yeouido.png",
    )

    /** The asset to show for this place, or null when it has none. */
    fun of(placeId: String?): String? = placeId?.let(byPlace::get)

    /** Every asset that must exist in the build. */
    fun assets(): List<String> = byPlace.values.sorted()

    /** The places that keep a drawing instead, so the reason stays written down. */
    val drawnInstead = setOf("building63", "cheongjinok")
}
