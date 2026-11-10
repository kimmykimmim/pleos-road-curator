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
 * @param note one short line the guardian adds, such as what the family did there.
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
        const val MAX_NOTE = 60
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
class FamilyAlbum(visits: List<FamilyVisit> = emptyList()) {
    private val byPlace = visits.associateBy { it.placeId }
    val places: Set<String> get() = byPlace.keys
    val isEmpty: Boolean get() = byPlace.isEmpty()
    fun of(placeId: String?): FamilyVisit? = placeId?.let(byPlace::get)
    fun with(visit: FamilyVisit) = FamilyAlbum(byPlace.values.filter { it.placeId != visit.placeId } + visit)
    fun without(placeId: String) = FamilyAlbum(byPlace.values.filter { it.placeId != placeId })
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
            question = "여기는 우리에게 어떤 곳이었을까?",
            guesses = listOf("기억이 나", "잘 모르겠어"),
            observe = "창밖을 한번 봐.",
            // Every word here is the guardian's. The app adds no history of its own.
            explanation = story(visit),
            sourceTitle = "보호자가 차 안에서 적어 둔 가족의 기억",
            sourceUrl = "",
            category = "가족",
            visual = CuratorVisual.FAMILY,
            // No bundled film belongs to a place only this family knows.
            mediaKey = "",
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

    private fun story(visit: FamilyVisit): String {
        val note = visit.note?.trim()?.take(FamilyVisit.MAX_NOTE).orEmpty()
        return buildString {
            append("그런데 있잖아. 여기는 ${visit.whenLabel} 우리 가족이 함께 왔던 곳이야.")
            if (note.isNotEmpty()) append(" $note")
            append(" 기억나지 않아도 괜찮아. 오늘 다시 지나가는 것만으로도 좋아.")
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
