package dev.changbak.explorer.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * A place the guardian says the family has been to before. The guardian writes this; the app
 * never infers it from location history, and nothing here leaves the car.
 *
 * @param whenLabel how the guardian wants the time said out loud, picked from [WHEN_CHOICES].
 * @param note one short line the guardian adds, such as what the family did there.
 */
data class FamilyVisit(val placeId: String, val whenLabel: String, val note: String? = null) {
    companion object {
        val WHEN_CHOICES = listOf(
            "네가 지금보다 더 어렸을 때",
            "네가 아기였을 때",
            "작년에",
            "얼마 전에",
        )
        const val MAX_NOTE = 60
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

    /**
     * What the curator says when the family's own place comes up. It tells the child the family
     * was here and then lets go: a child who does not remember has nothing to get wrong.
     */
    fun recall(place: CuratorPlace): String? {
        val visit = of(place.id) ?: return null
        val note = visit.note?.trim()?.take(FamilyVisit.MAX_NOTE).orEmpty()
        return buildString {
            append("그런데 있잖아. 여기는 ${visit.whenLabel} 우리 가족이 함께 왔던 곳이야.")
            if (note.isNotEmpty()) append(" $note")
            append(" 기억나지 않아도 괜찮아. 오늘 다시 지나가는 것만으로도 좋아.")
        }
    }

    companion object {
        /** Stored as text so the record can be read back, checked and deleted by hand. */
        fun encode(visits: List<FamilyVisit>): String = JSONObject()
            .put("version", 1)
            .put("visits", JSONArray().also { array ->
                visits.take(MAX_VISITS).forEach { visit ->
                    array.put(JSONObject()
                        .put("placeId", visit.placeId)
                        .put("whenLabel", visit.whenLabel)
                        .put("note", visit.note.orEmpty()))
                }
            })
            .toString()

        /** A record written by an older or damaged version is dropped, never guessed at. */
        fun decode(text: String?): List<FamilyVisit> = runCatching {
            val json = JSONObject(text.orEmpty().ifBlank { "{}" })
            if (json.optInt("version") != 1) return emptyList()
            val array = json.optJSONArray("visits") ?: return emptyList()
            (0 until minOf(array.length(), MAX_VISITS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val placeId = item.optString("placeId").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val whenLabel = item.optString("whenLabel").takeIf(String::isNotBlank)
                    ?: FamilyVisit.WHEN_CHOICES.first()
                FamilyVisit(placeId, whenLabel.take(40), item.optString("note").trim()
                    .take(FamilyVisit.MAX_NOTE).takeIf(String::isNotEmpty))
            }.distinctBy { it.placeId }
        }.getOrElse { emptyList() }

        const val MAX_VISITS = 40
    }
}
