package dev.changbak.explorer.domain

import kotlin.math.abs

enum class WindowSide(val hint: String?) {
    LEFT("왼쪽 창밖을 봐."),
    RIGHT("오른쪽 창밖을 봐."),
    AHEAD("앞쪽 창밖을 봐."),
    /** Not confident enough to point. The journey then says nothing about a side. */
    UNKNOWN(null),
}

/**
 * Which window a place is at, from the direction the car is actually travelling.
 *
 * A child in a moving car cannot act on "look outside": by the time they pick a window the place
 * is gone. Naming the side is the difference between finding it and missing it, and it is
 * something a phone cannot do, because it does not know which way the car is pointing.
 *
 * This only points. It never decides that the child saw anything.
 */
object WindowDirection {
    /** Below this the heading between two fixes is mostly noise, not travel. */
    const val MIN_TRAVEL_METERS = 25.0
    /** An older pair of fixes no longer describes where the car points now. */
    const val MAX_GAP_MS = 20_000L
    /** Right on top of a place every side is arguable, so say nothing. */
    const val MIN_TARGET_METERS = 40.0
    const val AHEAD_DEGREES = 30.0
    const val BEHIND_DEGREES = 150.0

    fun of(previous: TimedPosition?, current: TimedPosition?, target: GeoPoint): WindowSide {
        if (previous == null || current == null) return WindowSide.UNKNOWN
        if (!previous.point.valid() || !current.point.valid() || !target.valid()) return WindowSide.UNKNOWN
        val gap = current.receivedAtMs - previous.receivedAtMs
        if (gap <= 0 || gap > MAX_GAP_MS) return WindowSide.UNKNOWN
        if (previous.point.metersTo(current.point) < MIN_TRAVEL_METERS) return WindowSide.UNKNOWN
        if (current.point.metersTo(target) < MIN_TARGET_METERS) return WindowSide.UNKNOWN
        val relative = signedDifference(current.point.bearingTo(target), previous.point.bearingTo(current.point))
        return when {
            abs(relative) <= AHEAD_DEGREES -> WindowSide.AHEAD
            // Behind the car there is no window worth naming; the passage line covers that case.
            abs(relative) >= BEHIND_DEGREES -> WindowSide.UNKNOWN
            relative < 0 -> WindowSide.LEFT
            else -> WindowSide.RIGHT
        }
    }

    /** Degrees from [from] to [to], in (-180, 180]. Negative turns to the left. */
    internal fun signedDifference(to: Double, from: Double): Double {
        val d = (to - from + 540.0) % 360.0 - 180.0
        return if (d == -180.0) 180.0 else d
    }
}
