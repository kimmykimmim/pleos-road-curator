package dev.changbak.explorer.domain

enum class JourneySource { NONE, VEHICLE, DEMO }
enum class WaypointKind(val label: String) { TREES("나무 숲"), BRIDGE("구름 다리"), MOUNTAIN("산 쉼터"), WATER("반짝이는 물"), TUNNEL("빛 터널"), LIGHT("다시 만난 햇빛") }
data class SkyWaypoint(val kind: WaypointKind, val meters: Float)
data class SkyJourney(
    val meters: Float = 0f,
    val source: JourneySource = JourneySource.NONE,
    val landmarks: List<SkyWaypoint> = emptyList(),
    val tunnel: Boolean = false,
    val complete: Boolean = false,
)

/** Distance-only imaginative trail: no GPS coordinates, speed score or destination storage. */
object SkyJourneyEngine {
    fun advance(journey: SkyJourney, meters: Float, source: JourneySource): SkyJourney {
        if (journey.complete || !meters.isFinite() || meters <= 0f || source == JourneySource.NONE) return journey
        if (journey.source != JourneySource.NONE && journey.source != source) return journey
        return journey.copy(meters = (journey.meters + meters).coerceAtMost(1_000_000f), source = source)
    }
    fun observe(state: TravelState, text: String): TravelTurn? {
        val world = state.cloudWorld ?: return null
        if (world.journey.complete || state.phase != CloudPhase.WATCHING) return null
        val compact = text.replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
        if (Regex("구름|같아|모양|아니|없어|안보여|기억|할까|갈거|가고싶|보고싶|어디|뭐|왜|인형|장난감|사진|책에서|꿈에서").containsMatchIn(compact)) return null
        val kind = when {
            Regex("터널.*(나왔|나왔어|끝)|다시밝|밖으로나왔").containsMatchIn(compact) -> WaypointKind.LIGHT
            Regex("터널.*(이야|들어|지나|왔)|어두워졌").containsMatchIn(compact) -> WaypointKind.TUNNEL
            Regex("나무|숲").containsMatchIn(compact) -> WaypointKind.TREES
            Regex("다리.*(건너|지나|보여|있어)|다리야").containsMatchIn(compact) -> WaypointKind.BRIDGE
            Regex("산이|산을|산보|산이야").containsMatchIn(compact) -> WaypointKind.MOUNTAIN
            Regex("강물|바다|호수").containsMatchIn(compact) -> WaypointKind.WATER
            else -> return null
        }
        val journey = world.journey
        if (journey.landmarks.lastOrNull()?.let { it.kind == kind && journey.meters - it.meters < 20f } == true)
            return TravelTurn(state, "응, 그 풍경은 우리 길에 남아 있어. 천천히 보고 와도 좋아.", TravelOutcome.CHAT)
        val updated = journey.copy(landmarks = (journey.landmarks + SkyWaypoint(kind, journey.meters)).takeLast(32),
            tunnel = when (kind) { WaypointKind.TUNNEL -> true; WaypointKind.LIGHT -> false; else -> journey.tunnel })
        val action = when (kind) {
            WaypointKind.TREES -> "네가 본 나무로 우리 길에 숲이 생겼어. 나는 여기서 잠깐 쉴게."
            WaypointKind.BRIDGE -> "우리 길에도 다리를 놓았어! 나도 너와 함께 건너갈게."
            WaypointKind.MOUNTAIN -> "네가 알려준 산에 쉼터를 만들었어. 잠깐 쉬어갈까?"
            WaypointKind.WATER -> "우리 길 옆에도 물이 반짝여. 나는 물결을 따라 떠갈게."
            WaypointKind.TUNNEL -> "작은 빛을 켰어. 어두운 길도 함께 지나가자. 밖이 밝아지면 알려줘."
            WaypointKind.LIGHT -> "다시 밝아졌네! 빛을 활짝 펼쳤어. 우리가 지나온 터널도 길에 남았어."
        }
        return TravelTurn(state.copy(cloudWorld = world.copy(journey = updated)), action, TravelOutcome.OBSERVED)
    }
}

/** Integrate only consecutive fresh vehicle samples. Never extrapolate stale speed. */
class VehicleDistanceIntegrator {
    private var timestamp: Long? = null
    private var speed = 0f
    fun reset() { timestamp = null; speed = 0f }
    fun sample(metersPerSecond: Float, elapsedNanos: Long): Float {
        if (!metersPerSecond.isFinite() || kotlin.math.abs(metersPerSecond) > 100f) { reset(); return 0f }
        val old = timestamp
        if (old != null && elapsedNanos <= old) return 0f
        val seconds = old?.let { (elapsedNanos - it) / 1_000_000_000f } ?: 0f
        val distance = if (seconds > 0f && seconds <= 3f) (speed + kotlin.math.abs(metersPerSecond)) * 0.5f * seconds else 0f
        timestamp = elapsedNanos; speed = kotlin.math.abs(metersPerSecond)
        return distance
    }
}
