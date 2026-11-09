package dev.changbak.explorer

import android.content.Context
import dev.changbak.explorer.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Stores self-reported discoveries only. No recording, transcript or location is saved. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("window_explorer", Context.MODE_PRIVATE)
    var gentleHint: Boolean
        get() = prefs.getBoolean("gentle_hint", true)
        set(value) { prefs.edit().putBoolean("gentle_hint", value).apply() }

    fun save(state: MissionState) {
        val obj = JSONObject().put("version", 1).put("context", state.context.name).put("round", state.round)
        state.current?.let { mission ->
            obj.put("current", JSONObject().put("id", mission.id).put("label", mission.label)
                .put("prompt", mission.prompt).put("hint", mission.hint).put("kind", mission.kind.name)
                .put("theme", mission.theme.name).put("parent", mission.parentId))
        }
        obj.put("history", JSONArray().also { array -> state.history.takeLast(40).forEach {
            array.put(JSONObject().put("id", it.id).put("label", it.label).put("kind", it.kind.name)
                .put("theme", it.theme.name).put("parent", it.parentId).put("result", it.result.name).put("round", it.round))
        } })
        obj.put("discoveries", JSONArray().also { array -> state.discoveries.takeLast(30).forEach {
            array.put(JSONObject().put("label", it.label).put("theme", it.theme.name))
        } })
        obj.put("recent", JSONArray(state.recent.takeLast(4)))
        prefs.edit().putString("session", obj.toString()).apply()
    }

    fun restore(): MissionState = runCatching {
        val json = JSONObject(prefs.getString("session", "{}")!!)
        if (json.optInt("version") != 1) return MissionEngine.create()
        val current = json.optJSONObject("current")?.let {
            Mission(it.getString("id"), it.getString("label").take(80), it.getString("prompt").take(200),
                it.getString("hint").take(200), MissionKind.valueOf(it.getString("kind")),
                MissionTheme.valueOf(it.getString("theme")), it.optString("parent").takeIf(String::isNotBlank))
        }
        val history = json.optJSONArray("history") ?: JSONArray()
        val discoveries = json.optJSONArray("discoveries") ?: JSONArray()
        val recent = json.optJSONArray("recent") ?: JSONArray()
        MissionState(
            Environment.valueOf(json.getString("context")), current,
            (maxOf(0, history.length() - 40) until history.length()).map { i -> history.getJSONObject(i).let {
                History(it.getString("id"), it.getString("label").take(80), MissionKind.valueOf(it.getString("kind")),
                    MissionTheme.valueOf(it.getString("theme")), it.optString("parent").takeIf(String::isNotBlank),
                    MissionResult.valueOf(it.getString("result")), it.getInt("round").coerceAtLeast(0))
            } },
            (maxOf(0, discoveries.length() - 30) until discoveries.length()).map { i -> discoveries.getJSONObject(i).let {
                Discovery(it.getString("label").take(80), MissionTheme.valueOf(it.getString("theme")))
            } },
            (maxOf(0, recent.length() - 4) until recent.length()).map { recent.getString(it) },
            json.optInt("round").coerceIn(0, Int.MAX_VALUE - 1),
        )
    }.getOrElse { MissionEngine.create() }
}
