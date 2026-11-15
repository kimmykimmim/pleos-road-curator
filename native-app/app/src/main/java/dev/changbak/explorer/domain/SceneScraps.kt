package dev.changbak.explorer.domain

/**
 * The photographs each place's explanation is made of.
 *
 * Two rules, and both of them were broken before: every explanation is a real photograph, and
 * every photograph is of **this** place. The scenes used to be keyed by the kind of drawing
 * they once were, so 숭례문 and 광화문 shared one picture — and that picture was taken inside
 * 경복궁. A child looking out at 숭례문 was shown somewhere else and told it was the story of
 * what they were looking at.
 *
 * So the key is the place. A place with no honest photograph of its own gets no photograph at
 * all rather than a near-enough one.
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

    /**
     * The line that rides under the clipping for as long as it is on screen.
     *
     * Author and licence only. Adding "Wikimedia Commons" pushed the line past the width of
     * the paper and it was cut off mid-word, which credits nobody; [source] and [page] are
     * kept in the repository, where the claim can actually be checked.
     */
    val credit: String get() = "사진 $author · $license"

    fun captionIn(language: ChildLanguage) =
        if (language == ChildLanguage.ENGLISH && captionEn.isNotBlank()) captionEn else caption
}

/** A ring drawn on the photograph, at [x], [y] across and down it, [r] wide. */
data class ScrapMark(val x: Float, val y: Float, val r: Float, val label: String = "")

/** One place's explanation: what it is called, and the clippings it is made of. */
data class PlaceScene(val title: String, val titleEn: String, val scraps: List<SceneScrap>)

object SceneScraps {
    private const val CC_BY_3 = "CC BY 3.0"
    private const val CC_BY_4 = "CC BY 4.0"
    private const val CC_BY_SA_3 = "CC BY-SA 3.0"
    private const val CC_BY_SA_4 = "CC BY-SA 4.0"
    private const val CC0 = "CC0"
    private const val PD = "Public domain"

    val byPlace: Map<String, PlaceScene> = mapOf(
        // The tower while it was still open: the middle column is standing and the outer ring
        // of columns is going up around it, which is the whole of what the words say.
        "lotte" to PlaceScene("높은 건물 안의 힘 나누기 팀", "The force-sharing team inside a tall building", listOf(
            SceneScrap(
                asset = "tower-frame.jpg",
                caption = "다 짓기 전의 롯데월드타워",
                captionEn = "Lotte World Tower before it was finished",
                author = "Mtfr", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:Lotte_World_Tower,_Seoul,_Korea,_under_construction_(zoom)_2013-12-04.JPG",
                focusY = .42f,
                mark = ScrapMark(.50f, .40f, .15f, "가운데 몸통"),
            ),
        )),
        // The building before its coat, and the same building wearing it.
        "building63" to PlaceScene("건물의 뼈대와 유리 겉옷", "A building's frame and its glass coat", listOf(
            SceneScrap(
                asset = "glass-1984.jpg",
                caption = "1984년, 아파트 너머의 63빌딩",
                captionEn = "1984: the 63 Building beyond the flats",
                author = "서울사진아카이브", license = CC_BY_3,
                page = "https://commons.wikimedia.org/wiki/File:63_Building_1984_06.jpg",
                focusY = .40f,
                mark = ScrapMark(.46f, .27f, .13f, "63빌딩"),
            ),
            SceneScrap(
                asset = "glass-wall.jpg",
                caption = "유리 겉옷을 두른 지금의 63빌딩",
                captionEn = "The same building in its glass coat today",
                author = "Maris Teteris", license = CC_BY_3,
                page = "https://commons.wikimedia.org/wiki/File:63_Building_-_panoramio.jpg",
            ),
        )),
        // The island in 1969, with the bank already round it and the old airstrip still on it.
        "yeouido" to PlaceScene("모래섬에서 오늘의 도시까지", "From a sandbank to today's city", listOf(
            SceneScrap(
                asset = "island-1969.jpg",
                caption = "1969년 여의도. 둑을 쌓던 때",
                captionEn = "Yeouido in 1969, while the bank was built",
                author = "경기도청", license = PD,
                page = "https://commons.wikimedia.org/wiki/File:%EC%84%9C%EC%9A%B8,_%ED%95%9C%EA%B0%95,_%EC%97%AC%EC%9D%98%EB%8F%84-1969%EB%85%84.jpg",
                focusY = .55f,
            ),
            SceneScrap(
                asset = "yeouido-today.jpg",
                caption = "같은 섬의 지금",
                captionEn = "The same island now",
                author = "Ox1997cow", license = CC_BY_SA_3,
                page = "https://commons.wikimedia.org/wiki/File:%EB%8F%99%EC%9E%91%EB%8C%80%EA%B5%90%EC%97%90%EC%84%9C_%EB%B3%B8_%EC%97%AC%EC%9D%98%EB%8F%84%EC%9D%98_%EB%A7%88%EC%B2%9C%EB%A3%A8%EB%93%A4.jpg",
                focusY = .45f,
            ),
        )),
        // 숭례문's own story, in 숭례문's own photographs: the city wall still joined to it,
        // and the gate at night, which is when it was shut.
        "sungnyemun" to PlaceScene("밤에 닫고 새벽에 열던 도시의 문", "The city door shut at night", listOf(
            SceneScrap(
                asset = "gate-1900.jpg",
                caption = "1900년 무렵. 성벽이 문에 이어져 있어",
                captionEn = "Around 1900: the city wall still joined the gate",
                author = "Homer Hulbert", license = PD,
                page = "https://commons.wikimedia.org/wiki/File:South_Gate,_Seoul,_c.1900.jpg",
                focusY = .48f,
                mark = ScrapMark(.50f, .63f, .07f, "드나들던 통로"),
            ),
            SceneScrap(
                asset = "gate-night-1929.jpg",
                caption = "1929년, 불을 밝힌 밤의 숭례문",
                captionEn = "Sungnyemun lit up at night in 1929",
                author = "알 수 없음 (1929년 사진)", license = PD,
                page = "https://commons.wikimedia.org/wiki/File:Sungnyemun_in_night_1929.png",
            ),
        )),
        // 광화문 is a palace gate, so its photographs are of the palace it opens into.
        "gwanghwamun" to PlaceScene("궁궐로 들어가는 문", "The gate into a palace", listOf(
            SceneScrap(
                asset = "palace-guard.jpg",
                caption = "광화문을 지키는 수문장",
                captionEn = "A guard standing at Gwanghwamun",
                author = "Frank Schulenburg", license = CC_BY_SA_4,
                page = "https://commons.wikimedia.org/wiki/File:Royal_Guard_at_Gwanghwamun_Gate,_Gyeongbokgung,_Seoul.jpg",
                focusY = .42f,
            ),
            SceneScrap(
                asset = "gate-guard.jpg",
                caption = "문 안쪽 경복궁에서 열리는 교대식",
                captionEn = "The changing ceremony inside Gyeongbokgung",
                author = "Garam", license = "Attribution",
                page = "https://commons.wikimedia.org/wiki/File:Royal_Guard-Changing_Ceremony_of_Joseon_in_2018.jpg",
                focusY = .55f,
            ),
        )),
        // The street the place is on, with its own sign in the photograph, and the bowl that
        // has been handed down three generations.
        "cheongjinok" to PlaceScene("사람에서 사람으로 건너가는 이야기", "A story handed from person to person", listOf(
            SceneScrap(
                asset = "recipe-street.jpg",
                caption = "1999년 청진동 해장국 골목",
                captionEn = "The Cheongjin-dong haejangguk alley in 1999",
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
        )),
        // A guardian's own place has none on purpose. The app has no photograph of it, and
        // putting somebody else's there would be making their memory up for them.
    )

    /** The clippings for this place, credits checked. */
    fun of(placeId: String?): List<SceneScrap> =
        byPlace[placeId]?.scraps.orEmpty().filter { it.usable }

    /** What this place's explanation is called. */
    fun titleFor(placeId: String?, language: ChildLanguage = ChildLanguage.KOREAN): String? =
        byPlace[placeId]?.let { if (language == ChildLanguage.ENGLISH) it.titleEn else it.title }

    /** Every file the clippings need, for checking what the build actually ships. */
    fun assets(): List<String> = byPlace.values.flatMap { it.scraps }.map { it.asset }
        .distinct().sorted()
}
