package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Stores grounded scenery summaries and appearance, never recordings or full conversation turns. */
class TravelStore(context: Context) {
    private val prefs = context.getSharedPreferences("landscape_friends_v1", Context.MODE_PRIVATE)
    private val legacy = CloudStore(context)

    fun save(state: TravelState) {
        val encoded = encode(state).toString()
        val edit = prefs.edit().putString("session", encoded)
        if (state.phase == CloudPhase.KEEPSAKE && (state.body != null || state.memories.isNotEmpty())) {
            edit.putString("last_keepsake", encoded)
        }
        edit.apply()
    }

    fun restore(): TravelState {
        val raw = prefs.getString("session", null)
        val saved = if (raw == null) migrate(legacy.restore()) else decode(raw) ?: TravelEngine.create()
        // Pending choices and listening consent never survive an Activity/process restart.
        return saved.copy(phase = if (saved.phase == CloudPhase.WATCHING) CloudPhase.RESTING else saved.phase,
            pending = FriendChoice.NONE)
    }

    fun lastKeepsake(): TravelState? {
        val raw = prefs.getString("last_keepsake", null)
        return if (raw != null) decode(raw) else legacy.lastKeepsake()?.let(::migrate)
    }

    private fun migrate(old: CloudState): TravelState {
        val body = old.friend ?: return TravelEngine.create()
        // The older schema never recorded a source. Do not invent a river/tree/cloud observation.
        val memory = TravelMemory(1, ScenerySource.IMAGINATION, "이전 여행에서 만든 ${body.label} 모습", FriendTrait.BODY)
        return TravelState(old.phase, body, listOf(memory), FriendChoice.NONE)
    }

    private fun bodyJson(body: CloudFriend) = JSONObject().put("form", body.form.name)
        .put("label", body.label.take(32)).put("nickname", body.nickname?.take(12))
        .put("features", JSONArray(body.features.distinct().map { it.name }))

    private fun encode(state: TravelState): JSONObject = JSONObject().put("version", 1)
        .put("phase", state.phase.name).put("body", state.body?.let(::bodyJson))
        .put("memories", JSONArray().also { array -> boundedMemories(state.memories).forEach { memory ->
            array.put(JSONObject().put("id", memory.id).put("source", memory.source.name)
                .put("detail", memory.detail.take(96)).put("trait", memory.trait.name))
        } })

    private fun boundedMemories(memories: List<TravelMemory>): List<TravelMemory> {
        // Keep current part origins even after many observations of another part.
        val unique = memories.distinctBy { it.id }
        val currentIds = unique.asReversed().distinctBy { it.trait.part }.map { it.id }.toSet()
        val historyIds = unique.filter { it.id !in currentIds }.takeLast((24 - currentIds.size).coerceAtLeast(0)).map { it.id }.toSet()
        return unique.filter { it.id in currentIds || it.id in historyIds }.takeLast(24)
    }

    private fun decode(raw: String?): TravelState? = runCatching {
        if (raw == null || raw.length > 24_000) return null
        val json = JSONObject(raw)
        if (json.optInt("version") != 1) return null
        val body = json.optJSONObject("body")?.let { item ->
            val form = CloudForm.valueOf(item.getString("form"))
            val features = item.optJSONArray("features") ?: JSONArray()
            CloudFriend(form, item.optString("label", form.label).take(32),
                item.optString("nickname").take(12).takeIf { it.isNotBlank() && it != "null" },
                (0 until minOf(features.length(), CloudFeature.entries.size)).mapNotNull { i ->
                    runCatching { CloudFeature.valueOf(features.getString(i)) }.getOrNull()
                }.distinct())
        }
        val array = json.optJSONArray("memories") ?: JSONArray()
        val memories = (0 until minOf(array.length(), 64)).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            val id = row.getInt("id")
            if (id <= 0) return@runCatching null
            TravelMemory(id, ScenerySource.valueOf(row.getString("source")), row.getString("detail").take(96),
                FriendTrait.valueOf(row.getString("trait")))
        }.getOrNull() }
        TravelState(CloudPhase.valueOf(json.getString("phase")), body, boundedMemories(memories), FriendChoice.NONE)
    }.getOrNull()
}
