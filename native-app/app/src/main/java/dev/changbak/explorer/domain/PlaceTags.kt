package dev.changbak.explorer.domain

/**
 * The few words under a place's name, while the car is beside it.
 *
 * This line used to read "이 지점 근처를 지났어요" once a place had been passed, which tells a
 * child nothing: they can see out of the window, and the app has just spent a minute on this
 * exact place. The space is in the best position on the screen — directly under the name of
 * the thing they are looking at — so it holds what is true about that thing instead.
 *
 * Every tag comes from the same sourced sentence the place's explanation does. Nothing here is
 * a guess, a mood or a slogan: 숭례문 carries #한양도성남쪽문 because the south gate of the city wall is
 * what it is, and no place carries a tag its own source does not support.
 */
object PlaceTags {
    private val korean = mapOf(
        "lotte" to listOf("#555미터", "#아웃리거", "#제일높은건물"),
        "building63" to listOf("#커튼월", "#유리외벽", "#금빛건물"),
        "yeouido" to listOf("#모래섬", "#옛비행장", "#둑을쌓은땅"),
        "sungnyemun" to listOf("#한양도성", "#남쪽대문", "#밤에닫는문"),
        "gwanghwamun" to listOf("#경복궁정문", "#궁궐의문", "#조선"),
        "cheongjinok" to listOf("#1937년개업", "#3대가업", "#서울미래유산"),
    )

    private val english = mapOf(
        "lotte" to listOf("#555metres", "#outrigger", "#tallestinkorea"),
        "building63" to listOf("#curtainwall", "#glasswall", "#goldenwall"),
        "yeouido" to listOf("#sandbank", "#onceanairfield", "#builtupland"),
        "sungnyemun" to listOf("#citywallgate", "#southgate", "#shutatnight"),
        "gwanghwamun" to listOf("#gyeongbokgungmaingate", "#palacegate", "#joseonpalace"),
        "cheongjinok" to listOf("#openedin1937", "#threegenerations", "#seoulfutureheritage"),
    )

    /** What a guardian's own place carries: it is theirs, and the app knows nothing else. */
    private val familyTag = mapOf(
        ChildLanguage.KOREAN to listOf("#우리가족의기억"),
        ChildLanguage.ENGLISH to listOf("#ourfamilysmemory"),
    )

    /**
     * The tags for this place, or an empty list when the app has none it can stand behind.
     *
     * @param category the place's own category, which is how a guardian's place is recognised
     *   without this file having to know the ids they invent.
     */
    fun of(
        placeId: String?,
        language: ChildLanguage = ChildLanguage.KOREAN,
        category: String = "",
    ): List<String> {
        val pack = if (language == ChildLanguage.ENGLISH) english else korean
        pack[placeId]?.let { return it }
        if (category == "가족" || category == "Family") return familyTag[language].orEmpty()
        return emptyList()
    }

    /** The same tags as the one line that goes under the name. */
    fun line(
        placeId: String?,
        language: ChildLanguage = ChildLanguage.KOREAN,
        category: String = "",
    ): String = of(placeId, language, category).joinToString("  ")
}
