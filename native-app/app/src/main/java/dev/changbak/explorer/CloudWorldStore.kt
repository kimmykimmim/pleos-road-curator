package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Separate storage leaves earlier landscape-friend prototypes intact and starts with an empty sky. */
class CloudWorldStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_world_v1", Context.MODE_PRIVATE)

    fun save(state: TravelState) {
        val json = encode(state).toString()
        val editor = prefs.edit().putString("session", json)
        if (state.phase == CloudPhase.KEEPSAKE && !state.cloudWorld?.clouds.isNullOrEmpty()) editor.putString("last_keepsake", json)
        editor.apply()
    }

    fun restore(): TravelState {
        val state = decode(prefs.getString("session", null)) ?: CloudPlayEngine.create()
        return state.copy(phase = if (state.phase == CloudPhase.WATCHING) CloudPhase.RESTING else state.phase)
    }
    fun lastKeepsake(): TravelState? = decode(prefs.getString("last_keepsake", null))

    private fun encode(state: TravelState): JSONObject {
        val world = state.cloudWorld ?: CloudWorld()
        return JSONObject().put("version", 1).put("phase", state.phase.name)
            .put("active", world.activeId).put("last", world.lastTouchedId)
            .put("expectation", world.expectation?.name).put("intro_done", world.introDone).put("favorite", world.favoriteId)
            .put("journey", JSONObject().put("meters", world.journey.meters).put("source", world.journey.source.name)
                .put("complete", world.journey.complete).put("tunnel", world.journey.tunnel)
                .put("landmarks", JSONArray().also { array -> world.journey.landmarks.takeLast(32).forEach {
                    array.put(JSONObject().put("kind", it.kind.name).put("meters", it.meters))
                } }))
            .put("clouds", JSONArray().also { array -> world.clouds.take(CloudPlayEngine.MAX_CLOUDS).forEach { cloud ->
                array.put(JSONObject().put("id", cloud.id).put("role", cloud.role.name)
                    .put("form", cloud.figure.form.name).put("label", cloud.figure.label.take(32))
                    .put("name", cloud.figure.nickname?.take(12))
                    .put("features", JSONArray(cloud.figure.features.distinct().map { it.name })))
            } })
    }
    private fun decode(raw: String?): TravelState? = runCatching {
        if (raw == null || raw.length > 16_000) return null
        val json = JSONObject(raw)
        if (json.optInt("version") != 1) return null
        val items = json.getJSONArray("clouds")
        val clouds = (0 until minOf(items.length(), CloudPlayEngine.MAX_CLOUDS)).mapNotNull { i -> runCatching {
            val item = items.getJSONObject(i)
            val id = item.getInt("id")
            if (id <= 0 || id > 100_000) return@runCatching null
            val form = CloudForm.valueOf(item.getString("form"))
            val features = item.optJSONArray("features") ?: JSONArray()
            SkyCloud(id, CloudFriend(form,
                if (form == CloudForm.OTHER) item.optString("label").filter { it.isLetterOrDigit() }.take(32).ifBlank { form.label } else form.label,
                item.optString("name").takeUnless { it.isBlank() || it == "null" }?.filter { it.isLetterOrDigit() }?.take(12),
                (0 until minOf(features.length(), CloudFeature.entries.size)).mapNotNull { j ->
                    runCatching { CloudFeature.valueOf(features.getString(j)) }.getOrNull()
                }.distinct()), CloudRole.valueOf(item.getString("role")))
        }.getOrNull() }.distinctBy { it.id }
        val active = json.optInt("active").takeIf { id -> clouds.any { it.id == id && it.role == CloudRole.FRIEND } }
            ?: clouds.firstOrNull { it.role == CloudRole.FRIEND }?.id
        val last = json.optInt("last").takeIf { id -> clouds.any { it.id == id } } ?: clouds.lastOrNull()?.id
        val expectation = runCatching { JourneyExpectation.valueOf(json.optString("expectation")) }.getOrNull()
        val favorite = json.optInt("favorite").takeIf { id -> clouds.any { it.id == id } }
        val travel = json.optJSONObject("journey")
        val journey = if (travel == null) SkyJourney() else {
            val meters = travel.optDouble("meters", 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1_000_000f) ?: 0f
            val marks = travel.optJSONArray("landmarks") ?: JSONArray()
            SkyJourney(meters, runCatching { JourneySource.valueOf(travel.optString("source")) }.getOrDefault(JourneySource.NONE),
                (0 until minOf(marks.length(), 32)).mapNotNull { i -> runCatching {
                    val mark = marks.getJSONObject(i)
                    val at = mark.getDouble("meters").toFloat()
                    require(at.isFinite() && at >= 0f && at <= meters)
                    SkyWaypoint(WaypointKind.valueOf(mark.getString("kind")), at)
                }.getOrNull() }, travel.optBoolean("tunnel"), travel.optBoolean("complete"))
        }
        CloudPlayEngine.project(TravelState(phase = CloudPhase.valueOf(json.getString("phase"))), CloudWorld(clouds, active, last,
            expectation = expectation, introDone = json.optBoolean("intro_done", clouds.isNotEmpty()), favoriteId = favorite, journey = journey))
    }.getOrNull()
}
