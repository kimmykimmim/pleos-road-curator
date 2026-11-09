package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Only structured imagined forms are saved, never audio or recognition transcripts. */
class CloudStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_friends_v1", Context.MODE_PRIVATE)
    fun save(state: CloudState) {
        val encoded = encode(state).toString()
        val edit = prefs.edit().putString("session", encoded)
        if (state.phase == CloudPhase.KEEPSAKE && state.friend != null) edit.putString("last_keepsake", encoded)
        edit.apply()
    }
    fun restore(): CloudState = decode(prefs.getString("session", null))?.let {
        if (it.phase == CloudPhase.WATCHING) it.copy(phase = CloudPhase.RESTING) else it
    } ?: CloudEngine.create()
    fun lastKeepsake(): CloudState? = decode(prefs.getString("last_keepsake", null))
    private fun friendJson(friend: CloudFriend) = JSONObject()
        .put("form", friend.form.name).put("label", friend.label.take(32))
        .put("nickname", friend.nickname?.take(12))
        .put("features", JSONArray(friend.features.distinct().map { it.name }))
    private fun encode(state: CloudState) = JSONObject().put("version", 1).put("phase", state.phase.name)
        .put("friend", state.friend?.let(::friendJson))
        .put("memories", JSONArray().also { rows -> state.memories.takeLast(12).forEach { rows.put(friendJson(it.friend)) } })
    private fun friendFrom(json: JSONObject): CloudFriend {
        val form = CloudForm.valueOf(json.getString("form"))
        val array = json.optJSONArray("features") ?: JSONArray()
        val features = (0 until minOf(array.length(), CloudFeature.entries.size)).mapNotNull { i ->
            runCatching { CloudFeature.valueOf(array.getString(i)) }.getOrNull()
        }.distinct()
        return CloudFriend(form, json.optString("label", form.label).take(32),
            json.optString("nickname").take(12).takeIf { it.isNotBlank() && it != "null" }, features)
    }
    private fun decode(raw: String?): CloudState? = runCatching {
        if (raw == null || raw.length > 20_000) return null
        val json = JSONObject(raw)
        if (json.optInt("version") != 1) return null
        val memories = json.optJSONArray("memories") ?: JSONArray()
        CloudState(CloudPhase.valueOf(json.getString("phase")), json.optJSONObject("friend")?.let(::friendFrom),
            (maxOf(0, memories.length() - 12) until memories.length()).map { CloudMemory(friendFrom(memories.getJSONObject(it))) })
    }.getOrNull()
}
