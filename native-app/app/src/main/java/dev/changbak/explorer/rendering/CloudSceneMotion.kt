package dev.changbak.explorer.rendering

import dev.changbak.explorer.domain.*

data class CloudLayer(
    val key: String, val cloudId: Int, val figure: CloudFriend,
    val x: Float, val y: Float, val z: Float, val scale: Float,
    val opacity: Float = 1f, val face: Float = 1f, val active: Float = 0f,
)

/** Pure, interruptible scene interpolation. The GL thread owns this object. */
class CloudSceneMotion(initial: CloudWorld?) {
    companion object { const val DURATION_MS = 820L }
    private var targetWorld = initial
    private var from = layout(initial)
    private var to = from
    private var started = 0L
    private var duration = 0L

    fun isMoving(now: Long) = duration > 0 && now - started < duration

    fun retarget(world: CloudWorld?, now: Long, animate: Boolean = true) {
        if (world == targetWorld) { if (!animate) { from = to; duration = 0L }; return }
        var current = frame(now)
        val next = layout(world)
        if (next == to) { targetWorld = world; if (!animate) { from = to; duration = 0L }; return }
        // The preview becomes the committed cloud; keep its visible pose instead of duplicating it.
        val preview = current.firstOrNull { it.cloudId == -1 }
        if (preview != null && world?.draft == null) {
            val born = next.firstOrNull { item -> item.cloudId > 0 &&
                current.none { it.cloudId == item.cloudId } && sameShape(item.figure, preview.figure) }
            if (born != null) current = current.map { if (it.key == preview.key) it.copy(key = born.key, cloudId = born.cloudId) else it }
        }
        targetWorld = world
        from = current
        to = next
        started = now
        duration = if (animate) DURATION_MS else 0L
    }

    fun frame(now: Long): List<CloudLayer> {
        if (duration == 0L || now - started >= duration) return to
        val t = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
        val ease = t * t * (3f - 2f * t)
        val oldByKey = from.associateBy { it.key }
        val newByKey = to.associateBy { it.key }
        val keys = (from.map { it.key } + to.map { it.key }).distinct()
        return keys.map { key ->
            val old = oldByKey[key]
            val new = newByKey[key]
            val start = old ?: run {
                val anchor = from.firstOrNull { it.cloudId == new!!.cloudId }
                new!!.copy(x = anchor?.x ?: new.x, y = anchor?.y ?: (new.y + 0.16f),
                    z = anchor?.z ?: new.z, scale = anchor?.scale ?: (new.scale * 0.88f),
                    opacity = 0f, face = 0f, active = 0f)
            }
            val end = new ?: run {
                val replacement = to.firstOrNull { it.cloudId == old!!.cloudId }
                old!!.copy(x = replacement?.x ?: old.x, y = replacement?.y ?: (old.y + 0.10f),
                    z = replacement?.z ?: old.z, scale = replacement?.scale ?: (old.scale * 0.94f),
                    opacity = 0f, active = 0f)
            }
            // Swapping speakers must not make their faces pass through each other.
            val arc = if (old != null && new != null && start.active != end.active)
                kotlin.math.sin(Math.PI.toFloat() * t) * if (end.active > start.active) 0.75f else -0.75f else 0f
            start.copy(x = lerp(start.x, end.x, ease) + arc, y = lerp(start.y, end.y, ease),
                z = lerp(start.z, end.z, ease), scale = lerp(start.scale, end.scale, ease),
                opacity = lerp(start.opacity, end.opacity, ease), face = lerp(start.face, end.face, ease),
                active = lerp(start.active, end.active, ease))
        }.filter { it.opacity > 0.0001f }.sortedBy { it.z }
    }

    private fun sameShape(a: CloudFriend, b: CloudFriend) = a.copy(nickname = null) == b.copy(nickname = null)
    private fun lerp(a: Float, b: Float, progress: Float) = a + (b - a) * progress

    private fun layout(world: CloudWorld?): List<CloudLayer> {
        if (world == null) return emptyList()
        val positions = listOf(-1.48f to 1.16f, 1.48f to 1.07f, -1.52f to -0.04f,
            1.52f to -0.12f, -1.45f to -1.13f, 1.45f to -1.10f, 0f to 1.65f, 0f to -1.68f)
        var slot = 0
        val layers = world.clouds.map { cloud ->
            val active = cloud.id == world.activeId && cloud.role == CloudRole.FRIEND
            val (x, y) = if (active) 0f to -0.18f else positions[(slot++) % positions.size]
            CloudLayer(key(cloud.id, cloud.figure), cloud.id, cloud.figure, x, y,
                if (active) 0f else -0.35f, if (active) 0.82f else 0.35f,
                face = if (cloud.role == CloudRole.FRIEND) 1f else 0f, active = if (active) 1f else 0f)
        }.toMutableList()
        world.draft?.let { figure -> layers.add(CloudLayer(key(-1, figure), -1, figure, 0f, 1.18f, -0.35f, 0.35f, face = 0f)) }
        return layers.sortedBy { it.z }
    }
    private fun key(id: Int, figure: CloudFriend) = "$id:${figure.form}:${figure.label}:${figure.features.joinToString()}"
}
