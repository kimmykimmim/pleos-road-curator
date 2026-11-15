package dev.changbak.explorer.domain

/**
 * The photographs the explanation is made of.
 *
 * The explanation used to be a model: a gate built out of four rectangles, three people built
 * out of a circle and a box. A model says "this is roughly the shape of the idea", which is a
 * thing an adult can read and a six year old cannot. A photograph says "this is the thing",
 * and it is the only picture that can send a child back to the window looking for it.
 *
 * So every explanation is a clipping now — a real photograph, cut out and laid on the page the
 * way a newspaper cutting is stuck in a scrapbook. Nothing here is drawn, generated or
 * rearranged: the pixels are the photographer's, and [author] and [license] ride along with
 * them on screen for as long as they are up.
 *
 * A place with no honest photograph gets no photograph. 청진옥 is a counter in an alley, and
 * the picture here is its street in 1999 with its own sign in it, not a stock shot of a
 * restaurant.
 *
 * @param mark what to ring on the photograph, in fractions of the photograph itself, so a
 *   child is told exactly where to look rather than at a picture in general.
 */
data class SceneScrap(
    val asset: String,
    val caption: String,
    val captionEn: String = "",
    val author: String = "",
    val license: String = "",
    val page: String = "",
    val source: String = "Wikimedia Commons",
    val focusX: Float = .5f,
    val focusY: Float = .5f,
    val mark: ScrapMark? = null,
) {
    /** A clipping may only be shown when it can say who took it and on what terms. */
    val usable: Boolean get() = asset.isNotBlank() && author.isNotBlank() && license.isNotBlank()

    /** The line that rides under the clipping for as long as it is on screen. */
    val credit: String get() = "사진 $author · $license · $source"

    fun captionIn(language: ChildLanguage) =
        if (language == ChildLanguage.ENGLISH && captionEn.isNotBlank()) captionEn else caption
}

/** A ring drawn on the photograph, at [x], [y] across and down it, [r] wide. */
data class ScrapMark(val x: Float, val y: Float, val r: Float, val label: String = "")

object SceneScraps {
    private const val CC_BY_3 = "CC BY 3.0"
    private const val CC_BY_4 = "CC BY 4.0"
    private const val CC_BY_SA_2 = "CC BY-SA 2.0"
    private const val CC_BY_SA_3 = "CC BY-SA 3.0"
    private const val CC0 = "CC0"
    private const val ATTRIBUTION = "Attribution"

    val byVisual: Map<CuratorVisual, List<SceneScrap>> = mapOf(
        // The tower while it was still open: the middle column is standing and the outer ring
        // of columns is going up around it, which is the whole of what the words say.
        CuratorVisual.TOWER to listOf(
            SceneScrap(
                asset = "tower-frame.jpg",
                caption = "다 짓기 전의 롯데월드타워",
                captionEn = "Lotte World Tower before it was finished: the middle goes up first",
                author = "Mtfr", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:Lotte_World_Tower,_Seoul,_Korea,_under_construction_(zoom)_2013-12-04.JPG",
                focusY = .42f,
                mark = ScrapMark(.50f, .40f, .15f, "가운데 몸통"),
            ),
        ),
        // One building's skeleton standing without its skin yet, and then a glass skin close
        // enough to see that it is hung on the outside rather than holding anything up.
        CuratorVisual.GLASS to listOf(
            SceneScrap(
                asset = "glass-1984.jpg",
                caption = "1984년, 아파트 너머로 올라간 63빌딩",
                captionEn = "1984: the 63 Building rising beyond the flats",
                author = "서울사진아카이브", license = CC_BY_3,
                page = "https://commons.wikimedia.org/wiki/File:63_Building_1984_06.jpg",
                focusY = .40f,
                mark = ScrapMark(.46f, .27f, .13f, "63빌딩"),
            ),
            SceneScrap(
                asset = "glass-skin.jpg",
                caption = "가까이서 본 유리 겉옷",
                captionEn = "Up close, the glass coat hangs on the outside",
                author = "Tim1965", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:1900_M_Street_NW_-_Washington_DC_-_curtain_wall_detail.JPG",
            ),
        ),
        // The island in 1969, with the bank already round it and the old airstrip still on it.
        CuratorVisual.ISLAND to listOf(
            SceneScrap(
                asset = "island-1969.jpg",
                caption = "1969년 여의도. 둑을 쌓던 때",
                captionEn = "Yeouido in 1969, while the bank was being built around it",
                author = "경기도청", license = "Public domain",
                page = "https://commons.wikimedia.org/wiki/File:%EC%84%9C%EC%9A%B8,_%ED%95%9C%EA%B0%95,_%EC%97%AC%EC%9D%98%EB%8F%84-1969%EB%85%84.jpg",
                focusY = .55f,
            ),
            SceneScrap(
                asset = "yeouido-today.jpg",
                caption = "같은 섬의 지금",
                captionEn = "The same island now, full of buildings",
                author = "Ox1997cow", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:%EB%8F%99%EC%9E%91%EB%8C%80%EA%B5%90%EC%97%90%EC%84%9C_%EB%B3%B8_%EC%97%AC%EC%9D%98%EB%8F%84%EC%9D%98_%EB%A7%88%EC%B2%9C%EB%A3%A8%EB%93%A4.jpg",
                focusY = .45f,
            ),
        ),
        // The street the place is on, with its own sign in the photograph, and the bowl that
        // has been handed down three generations.
        CuratorVisual.RECIPE to listOf(
            SceneScrap(
                asset = "recipe-street.jpg",
                caption = "1999년 청진동 해장국 골목",
                captionEn = "The Cheongjin-dong alley in 1999 — find the name on the sign",
                author = "서울연구원", license = CC_BY_4,
                page = "https://commons.wikimedia.org/wiki/File:Cheongjin-dong_Haejang-guk_street_in_1999.jpg",
                focusY = .30f,
                mark = ScrapMark(.35f, .18f, .16f, "청진옥"),
            ),
            SceneScrap(
                asset = "recipe-bowl.jpg",
                caption = "3대를 건너온 해장국 한 그릇",
                captionEn = "The soup handed down three generations",
                author = "sangmins", license = CC0,
                page = "https://commons.wikimedia.org/wiki/File:Haejangguk_(hangover_soup).jpg",
            ),
        ),
        // The gate being opened, by people, the way it was opened every morning.
        CuratorVisual.GATE to listOf(
            SceneScrap(
                asset = "gate-guard.jpg",
                caption = "문을 지키던 수문장의 교대 의식",
                captionEn = "The gate guards — the ceremony still happens today",
                author = "Garam", license = ATTRIBUTION,
                page = "https://commons.wikimedia.org/wiki/File:Royal_Guard-Changing_Ceremony_of_Joseon_in_2018.jpg",
                focusY = .55f,
            ),
        ),
        // FAMILY has none on purpose. The app has no photograph of a family's own place, and
        // putting somebody else's there would be making their memory up for them.
    )

    /** The clippings for this scene, credits checked. */
    fun of(visual: CuratorVisual): List<SceneScrap> =
        byVisual[visual].orEmpty().filter { it.usable }

    /** Every file the clippings need, for checking what the build actually ships. */
    fun assets(): List<String> = byVisual.values.flatten().map { it.asset }.distinct().sorted()
}
