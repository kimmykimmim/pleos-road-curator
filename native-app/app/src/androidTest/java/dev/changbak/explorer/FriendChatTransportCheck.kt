package dev.changbak.explorer

import android.content.Context
import android.os.Looper
import dev.changbak.explorer.network.FriendChatClient
import dev.changbak.explorer.network.FriendChatContext
import dev.changbak.explorer.network.FriendChatHealth
import dev.changbak.explorer.network.FriendChatMemory
import dev.changbak.explorer.network.FriendChatMessage
import dev.changbak.explorer.network.FriendChatReply
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Uses only synthetic text and server/transport-fixture.mjs through adb reverse. */
object FriendChatTransportCheck {
    private const val FIRST_MESSAGE = "할머니 집에 가"
    private const val FIRST_REPLY = "할머니를 만나러 가는구나. 어떤 기분이야?"
    private const val NEXT_REPLY = "보고 싶어서 신나는구나. 함께 가는 길이 즐겁겠어."
    private val friend = FriendChatContext(
        name = "시연 뭉치",
        appearance = "동그란 몸과 반짝이는 꼬리",
        memories = listOf(FriendChatMemory("강", "꼬리", "반짝이는 강물")),
    )

    @Suppress("UNUSED_PARAMETER")
    fun run(context: Context): String {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Run transport checks on the instrumentation thread" }
        val client = FriendChatClient()
        try {
            val health = awaitCallback<FriendChatHealth>("health") { client.health(it) }
            check(health.ready && health.mode == "adult-demo" && health.model == "transport-fixture") {
                "Start the synthetic transport fixture, not the real API bridge"
            }
            val baseline = fixtureStatus()
            val first = awaitCallback<FriendChatReply>("first chat") { callback ->
                client.chat(FIRST_MESSAGE, listOf(FriendChatMessage("assistant", "오늘 어디 가?")), friend, callback)
            }
            check(first.reply == FIRST_REPLY && first.mood == "happy") { "First synthetic reply was not decoded correctly" }

            val history = listOf(FriendChatMessage("user", FIRST_MESSAGE), FriendChatMessage("assistant", FIRST_REPLY))
            val cancelledCallback = CountDownLatch(1)
            check(client.chat("__transport_check_delay__", history, friend) { cancelledCallback.countDown() }) {
                "Delayed request was not accepted"
            }
            awaitFixture("delayed request arrival") {
                it.getInt("delayedStarted") == baseline.getInt("delayedStarted") + 1
            }
            client.cancel()
            // Longer than the fixture's 2-second reply delay: even a late response must not call back.
            check(!cancelledCallback.await(2700, TimeUnit.MILLISECONDS)) { "Cancelled request delivered a callback" }
            val afterCancel = awaitFixture("upstream cancellation") {
                it.getInt("cancelled") == baseline.getInt("cancelled") + 1
            }
            check(afterCancel.getInt("delayedCompleted") == baseline.getInt("delayedCompleted")) {
                "Cancelled request completed on the fixture"
            }

            val next = awaitCallback<FriendChatReply>("chat after cancellation") { callback ->
                client.chat("보고 싶어서 신나", history, friend, callback)
            }
            check(next.reply == NEXT_REPLY && next.mood == "excited") { "The next request did not recover after cancellation" }
            val finalStats = fixtureStatus()
            check(finalStats.getInt("accepted") == baseline.getInt("accepted") + 3) { "Synthetic requests were missing or duplicated" }
            check(finalStats.getInt("completed") == baseline.getInt("completed") + 2) { "Unexpected completed request count" }
            check(finalStats.getInt("rejected") == baseline.getInt("rejected")) { "The client sent an invalid payload or header" }
            check(cancelledCallback.count == 1L) { "Cancelled callback leaked into the next request" }
            return "PASS: actual Android client health, Korean payload and context, main-thread callbacks, in-flight cancellation, callback suppression, and next chat recovery; synthetic fixture only"
        } finally {
            client.release()
        }
    }

    private fun <T> awaitCallback(label: String, submit: ((Result<T>) -> Unit) -> Boolean): T {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Result<T>?>(null)
        val onMain = AtomicBoolean(false)
        check(submit { value ->
            onMain.set(Looper.myLooper() == Looper.getMainLooper())
            result.set(value)
            latch.countDown()
        }) { "$label request was rejected by the client" }
        check(latch.await(12, TimeUnit.SECONDS)) { "$label callback timed out" }
        check(onMain.get()) { "$label callback did not run on the main thread" }
        val received = checkNotNull(result.get()) { "$label callback result missing" }
        check(received.isSuccess) { "$label request failed" }
        return received.getOrThrow()
    }

    private fun awaitFixture(label: String, predicate: (JSONObject) -> Boolean): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            val value = fixtureStatus()
            if (predicate(value)) return value
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        error("$label was not observed by the fixture")
    }

    private fun fixtureStatus(): JSONObject {
        val connection = URL("http://127.0.0.1:8791/fixture/status").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("X-Scenery-Client", "pleos-local-v1")
            check(connection.responseCode == 200) { "Synthetic fixture status is unavailable" }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(text.length < 4096) { "Unexpected fixture status size" }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
