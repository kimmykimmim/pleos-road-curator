package dev.changbak.explorer.domain

/**
 * One real photograph of a place at one point in time.
 *
 * A diagram explains a principle; a photograph shows that the change actually happened. For a
 * place whose whole story is "it did not always look like this", seeing the real thing land on
 * top of the real thing before it is worth more than any drawing.
 *
 * [source] is not decoration. A photograph with no stated source is indistinguishable from an
 * invented one, so [PhotoStages.of] refuses to hand one over and the place keeps its drawing.
 *
 * @param asset file under `assets/photos/`, supplied by whoever holds the right to use it.
 * @param year what the caption says about when, in the child's words.
 * @param caption one short line naming what changed.
 * @param source the archive or holder the image came from, shown on screen with the photo.
 * @param license the terms the image is used under, recorded so the claim can be checked.
 */
data class PhotoStage(
    val asset: String,
    val year: String,
    val caption: String,
    val source: String = "",
    val license: String = "",
) {
    /** A photo may only be shown when it can say where it came from. */
    val usable: Boolean get() = asset.isNotBlank() && source.isNotBlank() && license.isNotBlank()
}

/**
 * The staged photographs each place would show, in the order the change happened.
 *
 * The captions and the running order are written here; the image files and their sources are
 * not, because the app's authors do not hold those rights. Until a stage carries a real file
 * and a real source it stays unusable and the place shows its drawing instead, so the app
 * never fills the gap with something it made up.
 */
object PhotoStages {
    /** Stages in time order. Fill `source` and `license` as each image is cleared for use. */
    val byPlace: Map<String, List<PhotoStage>> = mapOf(
        "yeouido" to listOf(
            PhotoStage("yeouido-1-sandbar.jpg", "아주 오래전", "한강 가운데 넓은 모래섬이었어요"),
            PhotoStage("yeouido-2-airfield.jpg", "그 뒤", "비행장으로 쓰이던 때도 있었어요"),
            PhotoStage("yeouido-3-city.jpg", "지금", "둑을 쌓고 땅을 넓혀 도시가 되었어요"),
        ),
        "gwanghwamun" to listOf(
            PhotoStage("gwanghwamun-1-before.jpg", "옛날", "지금 자리와 모습이 달랐어요"),
            PhotoStage("gwanghwamun-2-today.jpg", "지금", "제자리를 찾아 다시 세웠어요"),
        ),
        "sungnyemun" to listOf(
            PhotoStage("sungnyemun-1-old.jpg", "옛날", "도시의 남쪽 문이었어요"),
            PhotoStage("sungnyemun-2-today.jpg", "지금", "사람들이 다시 세워 돌아왔어요"),
        ),
        "cheongjinok" to listOf(
            PhotoStage("cheongjinok-1-old.jpg", "오래전", "골목에 문을 연 작은 가게였어요"),
            PhotoStage("cheongjinok-2-today.jpg", "지금", "3대를 이어 같은 자리에 있어요"),
        ),
        "building63" to listOf(
            PhotoStage("building63-1-building.jpg", "짓는 중", "뼈대를 먼저 세웠어요"),
            PhotoStage("building63-2-today.jpg", "지금", "유리 겉옷을 입은 모습이에요"),
        ),
        "lotte" to listOf(
            PhotoStage("lotte-1-building.jpg", "짓는 중", "가운데 몸통부터 올라갔어요"),
            PhotoStage("lotte-2-today.jpg", "지금", "완성된 모습이에요"),
        ),
    )

    /**
     * The stages this place can actually show. A sequence of one photograph shows no change,
     * so it is not worth taking the screen from the drawing that does explain something.
     */
    fun of(placeId: String?): List<PhotoStage> {
        val stages = byPlace[placeId].orEmpty().filter { it.usable }
        return if (stages.size >= 2) stages else emptyList()
    }

    /** Every file the photo sequences would use, for checking what is still missing. */
    fun expectedAssets(): List<String> = byPlace.values.flatten().map { it.asset }
}
