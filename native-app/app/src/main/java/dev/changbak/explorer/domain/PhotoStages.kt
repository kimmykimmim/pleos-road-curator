package dev.changbak.explorer.domain

/**
 * One real photograph of a place.
 *
 * A diagram explains why a building stands up. It does not help a six year old pick that
 * building out of a moving window. For that the child needs to have seen the real thing a
 * moment earlier, from roughly the angle the road gives: then the window becomes "어? 이거구나".
 *
 * [author] and [license] are not decoration. A photograph with no stated origin cannot be told
 * apart from an invented one, so [PhotoStage.usable] refuses it and the place keeps its drawing.
 *
 * @param asset file under `assets/photos/`.
 * @param year what the caption says about when, in the child's words.
 * @param caption one short line, read from the back seat of a moving car.
 * @param author who took it, shown on screen with the photograph.
 * @param license the terms it is used under, shown on screen and recorded in the repository.
 * @param source where it was obtained.
 * @param page the file's page, so the claim can be checked.
 */
data class PhotoStage(
    val asset: String,
    val year: String,
    val caption: String,
    val author: String = "",
    val license: String = "",
    val source: String = "Wikimedia Commons",
    val page: String = "",
) {
    /** A photograph may only be shown when it can say who made it and on what terms. */
    val usable: Boolean get() = asset.isNotBlank() && author.isNotBlank() && license.isNotBlank()

    /** What rides along the bottom of the photograph for as long as it is on screen. */
    val credit: String get() = "사진 $author · $license · $source"
}

/**
 * The photographs each place can show, in the order the change happened.
 *
 * Only pictures with a licence that allows this use are listed, and each carries its author.
 * Nothing here is generated: inventing what a place looked like and showing it to a child as
 * the real thing is the one mistake this app must never make.
 *
 * 청진옥 has no entry. It is a small restaurant in an alley with no freely licensed photograph,
 * and a picture of the wrong shopfront would be worse than the drawing it already has.
 */
object PhotoStages {
    private const val CC_BY_3 = "CC BY 3.0"
    private const val CC_BY_4 = "CC BY 4.0"
    private const val CC_BY_SA_3 = "CC BY-SA 3.0"
    private const val CC_BY_SA_4 = "CC BY-SA 4.0"
    private const val CC0 = "CC0"

    val byPlace: Map<String, List<PhotoStage>> = mapOf(
        "lotte" to listOf(
            PhotoStage(
                asset = "lotte-now.jpg", year = "지금", caption = "한강 건너에서 보면 이렇게 뾰족해",
                author = "Ox1997cow", license = CC_BY_3,
                page = "https://commons.wikimedia.org/wiki/File:Lotte_World_Tower_near_Cheongdam_Bridge.jpg",
            ),
        ),
        "building63" to listOf(
            PhotoStage(
                asset = "building63-now.jpg", year = "지금", caption = "금빛으로 반짝이는 벽을 찾아봐",
                author = "Sebastian Hoering", license = CC_BY_SA_4,
                page = "https://commons.wikimedia.org/wiki/File:63_Building.jpg",
            ),
        ),
        "yeouido" to listOf(
            PhotoStage(
                asset = "yeouido-now.jpg", year = "지금", caption = "강 가운데 섬에 높은 건물이 가득해",
                author = "Ox1997cow", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:%EB%8F%99%EC%9E%91%EB%8C%80%EA%B5%90%EC%97%90%EC%84%9C_%EB%B3%B8_%EC%97%AC%EC%9D%98%EB%8F%84%EC%9D%98_%EB%A7%88%EC%B2%9C%EB%A3%A8%EB%93%A4.jpg",
            ),
        ),
        "sungnyemun" to listOf(
            PhotoStage(
                asset = "sungnyemun-now.jpg", year = "지금", caption = "높은 건물 사이에 기와 지붕이 있어",
                author = "Sean Young (@assanges)", license = CC_BY_4,
                page = "https://commons.wikimedia.org/wiki/File:Namdaemun_Sungnyemun_Gate_20240219.jpg",
            ),
        ),
        "gwanghwamun" to listOf(
            PhotoStage(
                asset = "gwanghwamun-now.jpg", year = "지금", caption = "넓은 길 끝에 산을 등진 문이 있어",
                author = "Huntsmanleader", license = CC0,
                page = "https://commons.wikimedia.org/wiki/File:Gwanghwamun_December_2023.jpg",
            ),
        ),
    )

    /** Every photograph this place can show, credits checked. */
    fun of(placeId: String?): List<PhotoStage> = byPlace[placeId].orEmpty().filter { it.usable }

    /**
     * What to put in front of the child while they are still looking for the place. One
     * photograph is enough here, and is the whole point: recognising it out of the window.
     */
    fun forRecognising(placeId: String?): List<PhotoStage> = of(placeId)

    /**
     * What to show once the place has been passed. A single photograph adds nothing the child
     * has not just seen out of the window, so the drawing keeps the screen and explains the
     * thing the window cannot show. Two or more become the change itself.
     */
    fun forExplaining(placeId: String?): List<PhotoStage> =
        of(placeId).takeIf { it.size >= 2 }.orEmpty()

    /** Every file the photographs use, for checking what the build actually ships. */
    fun expectedAssets(): List<String> = byPlace.values.flatten().map { it.asset }
}
