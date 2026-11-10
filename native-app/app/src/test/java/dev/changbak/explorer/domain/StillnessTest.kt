package dev.changbak.explorer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessTest {
    private fun stopped(gear: GearPosition, brakeHeldSinceMs: Long? = null, nowMs: Long = 100_000) =
        Stillness.isStopped(MotionSignals(gear, brakeHeldSinceMs), nowMs)

    @Test
    fun `a parked car is standing still`() {
        assertTrue(stopped(GearPosition.PARK))
    }

    @Test
    fun `a brake held through a red light is standing still`() {
        assertTrue(stopped(GearPosition.DRIVE, brakeHeldSinceMs = 100_000 - Stillness.BRAKE_HOLD_MS))
        assertTrue(stopped(GearPosition.NEUTRAL, brakeHeldSinceMs = 100_000 - Stillness.BRAKE_HOLD_MS))
    }

    @Test
    fun `a dab of the brake in traffic is not a stop`() {
        assertFalse(stopped(GearPosition.DRIVE, brakeHeldSinceMs = 100_000 - Stillness.BRAKE_HOLD_MS + 1))
    }

    @Test
    fun `a released brake ends the stop`() {
        assertFalse(stopped(GearPosition.DRIVE, brakeHeldSinceMs = null))
    }

    @Test
    fun `reversing is never a moment to watch a film`() {
        assertFalse(stopped(GearPosition.REVERSE, brakeHeldSinceMs = 0))
    }

    @Test
    fun `a car that says nothing is never assumed to be stopped`() {
        assertFalse(stopped(GearPosition.UNKNOWN))
        assertFalse(stopped(GearPosition.UNKNOWN, brakeHeldSinceMs = 0))
        assertFalse(Stillness.isStopped(MotionSignals(), 100_000))
    }

    @Test
    fun `a brake stamped in the future never counts as a long hold`() {
        assertFalse(stopped(GearPosition.DRIVE, brakeHeldSinceMs = 200_000))
    }

    @Test
    fun `parking outranks the brake, however briefly it was pressed`() {
        assertTrue(stopped(GearPosition.PARK, brakeHeldSinceMs = 100_000))
    }
}
