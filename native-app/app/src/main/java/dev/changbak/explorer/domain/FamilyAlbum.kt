package dev.changbak.explorer.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * A place the guardian says the family has been to before. The guardian writes this; the app
 * never infers it from location history, and nothing here leaves the car.
 *
 * Two kinds share this shape. One marks a place the app already knows. The other is a place the
 * guardian added themselves while parked there, which carries its own name and coordinates. The
 * second kind is how a family reaches places the app's own six could never cover, without the
 * app inventing a single fact: every word of its story is the guardian's.
 *
 * @param whenLabel how the guardian wants the time said out loud, picked from [WHEN_CHOICES].
 *   Only said aloud when there is no [note]: a written story usually carries its own when, and
 *   the two together read as the app repeating itself.
 * @param note the story itself, in the guardian's own words. This is the feature: a parent
 *   writing "엄마 아빠가 처음 만난 곳이야" and the car saying it as the family drives past.
 */
data class FamilyVisit(
    val placeId: String,
    val whenLabel: String,
    val note: String? = null,
    /** The guardian's own name for a place they added; null for one of the app's places. */
    val ownName: String? = null,
    /** Where that place is. Only set for a guardian's own place. */
    val anchor: GeoPoint? = null,
) {
    val isOwn: Boolean get() = ownName != null && anchor != null && anchor.valid()

    companion object {
        val WHEN_CHOICES = listOf(
            "네가 지금보다 더 어렸을 때",
            "네가 아기였을 때",
            "작년에",
            "얼마 전에",
        )
        /**
         * Long enough for the kind of thing a parent actually writes.
         *
         * Sixty characters stopped them mid-sentence: "네가 다섯 살 때 여행 가기 전에 들렀던
         * 가게야. 어찌나 울었는지 엄마가 혼났어." is longer than that, and it is exactly the
         * sort of story this is for.
         */
        const val MAX_NOTE = 140
        const val MAX_NAME = 20
        /** Guardian places get their own id space so they can never shadow a catalog place. */
        const val OWN_PREFIX = "own:"
        fun ownId(createdAtMs: Long) = "$OWN_PREFIX$createdAtMs"
    }
}

/**
 * The guardian's records, keyed by place. Reading is what the journey does on the road, so it
 * stays a plain lookup with no I/O.
 */
class FamilyAlbum(
    visits: List<FamilyVisit> = emptyList(),
    /** The child's language, for the app's own frame around a guardian's record. */
    private val language: ChildLanguage = ChildLanguage.DEFAULT,
) {
    private val say: Narration get() = Narration.of(language)

    private val byPlace = visits.associateBy { it.placeId }
    val places: Set<String> get() = byPlace.keys
    val isEmpty: Boolean get() = byPlace.isEmpty()
    fun of(placeId: String?): FamilyVisit? = placeId?.let(byPlace::get)

    /** Whether somebody at home wrote a story for this place, rather than the app noticing it. */
    fun wrote(placeId: String?): Boolean = of(placeId)?.note?.isBlank() == false
    fun with(visit: FamilyVisit) = FamilyAlbum(byPlace.values.filter { it.placeId != visit.placeId } + visit, language)
    fun without(placeId: String) = FamilyAlbum(byPlace.values.filter { it.placeId != placeId }, language)
    fun toList(): List<FamilyVisit> = byPlace.values.sortedBy { it.placeId }

    val ownVisits: List<FamilyVisit> get() = byPlace.values.filter { it.isOwn }.sortedBy { it.placeId }
    val ownPlaceIds: List<String> get() = ownVisits.map { it.placeId }

    /**
     * What the curator says when the family's own place comes up. It tells the child the family
     * was here and then lets go: a child who does not remember has nothing to get wrong.
     */
    fun recall(place: CuratorPlace): String? {
        val visit = of(place.id) ?: return null
        // A guardian's own place already tells this story as its explanation. Saying it twice
        // would make the one real memory of the trip sound like a recording.
        if (visit.isOwn) return null
        return story(visit)
    }

    /** Places the guardian added, shaped so the journey treats them like any other place. */
    fun ownCuratorPlaces(): List<CuratorPlace> = ownVisits.map { visit ->
        CuratorPlace(
            id = visit.placeId,
            name = visit.ownName!!,
            theme = "우리 가족의 장소",
            // One round only. A place the app knows nothing about has no second question of
            // ours to ask; the guardian's own line is the whole story here.
            rounds = listOf(RoundPrompt(
                kind = RoundKind.HOOK,
                // A family's own place is theirs; no interest of ours selects it away.
                field = InterestField.LIVING,
                lead = "",
                question = "여기는 우리에게 어떤 곳이었을까?",
                choices = listOf("기억이 나", "잘 모르겠어"),
            )),
            observe = "창밖을 한번 봐.",
            // Every word here is the guardian's. The app adds no history of its own.
            explanation = story(visit),
            sourceTitle = "보호자가 차 안에서 적어 둔 가족의 기억",
            sourceUrl = "",
            category = "가족",
            visual = CuratorVisual.FAMILY,
            // No bundled film belongs to a place only this family knows.
            mediaKey = "",
            // The app knows nothing about this place, so it has nothing to ask back. It can
            // still say what the child said, which is the whole of what it honestly has.
            talk = PlaceTalk(
                topics = emptyList(),
                echo = "“${PlaceTalk.THEIR_WORDS}”라고 했구나. 그것도 우리 가족 이야기야.",
                // A guardian's own place has no field and nothing written for one, so the
                // follow-up asks about the family rather than about a subject.
                follow = "그때 누구랑 같이 있었어?",
                unsure = "몰라도 괜찮아. 네 생각을 들려줘.",
                silence = "괜찮아. 오늘 다시 지나가는 것만으로도 좋아.",
                closing = "창밖을 한번 봐.",
            ),
            activity = PlaceActivity(
                // The screen already prefixes "오늘의", so the role must not repeat it.
                "이야기꾼", "이곳에서 무엇이 기억나는지 말해 줄래?",
                listOf(
                    PlayChoice("warm", "좋았던 기억이야", "그 기억을 오늘 길 위에 하나 더 얹었어."),
                    PlayChoice("new", "처음 듣는 이야기야", "오늘 새로 알게 된 우리 가족의 장소가 되었네."),
                ),
            ),
        )
    }

    /** The same places as encounter anchors, so proximity finds them like the app's own places. */
    fun ownLocatedPlaces(): List<LocatedPlace> = ownVisits.map { visit ->
        LocatedPlace(
            id = visit.placeId,
            aliases = setOf(visit.ownName!!),
            anchor = visit.anchor!!,
            radiusMeters = OWN_RADIUS_METERS,
            locationSource = "보호자가 차 안에서 저장한 위치",
        )
    }

    /**
     * What the child hears at a place somebody at home wrote about.
     *
     * A written story is the point of the whole feature, so when there is one it carries the
     * place and the app only introduces it. The app's own sentence is used alone when nothing
     * was written — and then it can say the one thing it does know, which is when.
     *
     * The guardian's words go in exactly as they wrote them: not translated, not trimmed into
     * the app's voice, not rephrased. That sentence is a parent talking to their child, and
     * the app is carrying it, not writing it.
     */
    private fun story(visit: FamilyVisit): String {
        val note = visit.note?.trim()?.take(FamilyVisit.MAX_NOTE).orEmpty()
        return buildString {
            if (note.isEmpty()) {
                append(say.familyRecall(visit.whenLabel))
                // The app noticed this one, so remembering is a fair thing to ask about.
                append(" ${say.familyReassure}")
            } else {
                append(say.familyIntro); append(" $note")
                // Somebody's own story. Nothing is being asked of the child's memory.
                append(" ${say.familyShared}")
            }
        }
    }

    companion object {
        const val MAX_VISITS = 40
        /** A guardian drops a pin from a parked car, so the encounter zone stays modest. */
        const val OWN_RADIUS_METERS = 300.0

        /** Stored as text so the record can be read back, checked and deleted by hand. */
        fun encode(visits: List<FamilyVisit>): String = JSONObject()
            .put("version", 2)
            .put("visits", JSONArray().also { array ->
                visits.take(MAX_VISITS).forEach { visit ->
                    val item = JSONObject()
                        .put("placeId", visit.placeId)
                        .put("whenLabel", visit.whenLabel)
                        .put("note", visit.note.orEmpty())
                    if (visit.isOwn) item.put("ownName", visit.ownName)
                        .put("latitude", visit.anchor!!.latitude)
                        .put("longitude", visit.anchor.longitude)
                    array.put(item)
                }
            })
            .toString()

        /** A record written by an older or damaged version is dropped, never guessed at. */
        fun decode(text: String?): List<FamilyVisit> = runCatching {
            val json = JSONObject(text.orEmpty().ifBlank { "{}" })
            if (json.optInt("version") !in SUPPORTED_VERSIONS) return emptyList()
            val array = json.optJSONArray("visits") ?: return emptyList()
            (0 until minOf(array.length(), MAX_VISITS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val placeId = item.optString("placeId").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val whenLabel = item.optString("whenLabel").takeIf(String::isNotBlank)
                    ?: FamilyVisit.WHEN_CHOICES.first()
                val ownName = item.optString("ownName").trim().take(FamilyVisit.MAX_NAME)
                    .takeIf(String::isNotEmpty)
                // A guardian place without a usable position cannot be met on the road, so the
                // whole record goes rather than becoming a place that never arrives.
                val anchor = if (ownName == null) null else
                    GeoPoint(item.optDouble("latitude", Double.NaN), item.optDouble("longitude", Double.NaN))
                        .takeIf { it.valid() } ?: return@mapNotNull null
                FamilyVisit(placeId, whenLabel.take(40),
                    item.optString("note").trim().take(FamilyVisit.MAX_NOTE).takeIf(String::isNotEmpty),
                    ownName, anchor)
            }.distinctBy { it.placeId }
        }.getOrElse { emptyList() }

        /** Version 1 had no guardian places; its records still read correctly. */
        private val SUPPORTED_VERSIONS = setOf(1, 2)
    }
}
