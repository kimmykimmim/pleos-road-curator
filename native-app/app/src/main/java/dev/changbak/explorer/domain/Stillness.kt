package dev.changbak.explorer.domain

enum class GearPosition { UNKNOWN, PARK, NEUTRAL, REVERSE, DRIVE }

/** What the car has told us about standing still. Absent values stay unknown, never assumed. */
data class MotionSignals(
    val gear: GearPosition = GearPosition.UNKNOWN,
    /** When the brake pedal was last pressed and still is, or null when it is released. */
    val brakeHeldSinceMs: Long? = null,
)

/**
 * Whether the car is standing still right now.
 *
 * Driving restrictions treat a moving car as something to take things away from. Standing still
 * is the opposite: it is the one moment when looking at the screen costs the child nothing. So
 * the journey waits for it and offers the short film then, instead of nagging on the move.
 *
 * Nothing here is inferred from silence. With no signal from the car the answer is "not stopped",
 * which keeps the screen quiet rather than inviting a child to look up at the wrong time.
 */
object Stillness {
    /** A brake held this long is a stop, not a dab of the pedal in traffic. */
    const val BRAKE_HOLD_MS = 3_000L

    fun isStopped(signals: MotionSignals, nowMs: Long): Boolean {
        if (signals.gear == GearPosition.PARK) return true
        // Reversing is a moment to watch the mirrors, never a moment to watch a film.
        if (signals.gear != GearPosition.DRIVE && signals.gear != GearPosition.NEUTRAL) return false
        val since = signals.brakeHeldSinceMs ?: return false
        return nowMs >= since && nowMs - since >= BRAKE_HOLD_MS
    }
}
