package dev.changbak.explorer.network

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class FriendChatMessage(val role: String, val content: String)
data class FriendChatMemory(val source: String, val part: String, val detail: String)
data class FriendChatContext(val name: String, val appearance: String, val memories: List<FriendChatMemory>)
data class FriendChatReply(val reply: String, val mood: String)
data class FriendChatHealth(val ready: Boolean, val mode: String, val model: String)

/** The APK talks only to the local demo bridge. It contains no provider key or transcript log. */
class FriendChatClient {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var flight: Flight? = null
    private var closed = false

    private class Flight {
        @Volatile var connection: HttpURLConnection? = null
        var future: Future<*>? = null
        var timeout: Runnable? = null
    }

    fun health(callback: (Result<FriendChatHealth>) -> Unit): Boolean = request("/health", null, {
        FriendChatHealth(it.optBoolean("ready", false), it.optString("mode"), it.optString("model").take(80))
    }, callback)

    fun chat(
        message: String,
        history: List<FriendChatMessage>,
        friend: FriendChatContext,
        callback: (Result<FriendChatReply>) -> Unit,
    ): Boolean {
        val cleaned = message.trim()
        if (cleaned.isEmpty() || cleaned.length > 500) return false
        val payload = JSONObject().put("message", cleaned)
            .put("history", JSONArray().also { array -> history.takeLast(12).forEach {
                if (it.role == "user" || it.role == "assistant") {
                    array.put(JSONObject().put("role", it.role).put("content", it.content.take(500)))
                }
            } })
            .put("friend", JSONObject().put("name", friend.name.take(40)).put("appearance", friend.appearance.take(500))
                .put("memories", JSONArray().also { array -> friend.memories.takeLast(24).forEach {
                    array.put(JSONObject().put("source", it.source.take(40)).put("part", it.part.take(40)).put("detail", it.detail.take(120)))
                } }))
        return request("/chat", payload, {
            if (it.optString("source") != "openai") throw IOException("Unexpected response source")
            val answer = it.optString("reply").trim()
            if (answer.isEmpty() || answer.length > 500) throw IOException("Invalid reply")
            val mood = it.optString("mood", "neutral").lowercase().takeIf { value ->
                value in setOf("neutral", "happy", "sad", "excited", "sleepy")
            } ?: "neutral"
            FriendChatReply(answer, mood)
        }, callback)
    }

    private fun <T> request(path: String, body: JSONObject?, parse: (JSONObject) -> T, callback: (Result<T>) -> Unit): Boolean {
        val task = Flight()
        synchronized(lock) {
            if (closed || flight != null) return false
            flight = task
        }
        val timeout = Runnable {
            val expired = synchronized(lock) { if (flight === task && !closed) { flight = null; true } else false }
            if (expired) {
                task.future?.cancel(true)
                task.connection?.disconnect()
                callback(Result.failure(IOException("Request timed out")))
            }
        }
        task.timeout = timeout
        main.postDelayed(timeout, 25_000L)
        task.future = worker.submit {
            val result = runCatching {
                val connection = URL("http://127.0.0.1:8791$path").openConnection() as HttpURLConnection
                try {
                    synchronized(lock) {
                        if (flight !== task || closed) throw IOException("Request cancelled")
                        task.connection = connection
                    }
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 20_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.requestMethod = if (body == null) "GET" else "POST"
                    connection.setRequestProperty("X-Scenery-Client", "pleos-local-v1")
                    connection.setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        val bytes = body.toString().toByteArray(Charsets.UTF_8)
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    if (connection.responseCode !in 200..299) throw IOException("Bridge request failed")
                    val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val text = StringBuilder()
                        val buffer = CharArray(2048)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (text.length + count > 32_768) throw IOException("Response too large")
                            text.append(buffer, 0, count)
                        }
                        text.toString()
                    }
                    parse(JSONObject(response))
                } finally {
                    connection.disconnect()
                    task.connection = null
                }
            }
            main.post {
                val deliver = synchronized(lock) { if (flight === task && !closed) { flight = null; true } else false }
                if (deliver) {
                    main.removeCallbacks(timeout)
                    callback(result)
                }
            }
        }
        return true
    }

    fun cancel() {
        val task = synchronized(lock) { val current = flight; flight = null; current } ?: return
        task.timeout?.let(main::removeCallbacks)
        task.future?.cancel(true)
        task.connection?.disconnect()
    }

    fun release() {
        synchronized(lock) { closed = true }
        cancel()
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }
}
