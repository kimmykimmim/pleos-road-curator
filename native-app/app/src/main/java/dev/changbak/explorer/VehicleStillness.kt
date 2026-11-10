package dev.changbak.explorer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import dev.changbak.explorer.domain.GearPosition
import dev.changbak.explorer.domain.MotionSignals
import dev.changbak.explorer.domain.Stillness

/**
 * Read-only view of whether the car is standing still, from the Pleos Vehicle SDK.
 *
 * It only ever reads: gear position and whether the brake pedal is held. It never sets a vehicle
 * value, and a car that does not report these simply stays "not stopped", which keeps the screen
 * quiet rather than inviting a child to look up while moving.
 */
class VehicleStillness(context: Context, private val onChanged: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var signals = MotionSignals()
    private var stopped = false
    private var released = false
    /** Re-checks a brake that is still held, since holding it longer is what makes it a stop. */
    private val recheck = Runnable { publish() }

    val isStopped: Boolean get() = stopped

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onMain {
            signals = signals.copy(gear = translate(gear))
            publish()
        }
        // A car that cannot report its gear stays unknown, which reads as "not stopped".
        override fun onFailed(e: Exception) = onMain {
            signals = signals.copy(gear = GearPosition.UNKNOWN)
            publish()
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = onMain {
            // Keep the first moment it went down: the hold is what separates a stop from a dab.
            signals = signals.copy(brakeHeldSinceMs =
                if (pressed == true) signals.brakeHeldSinceMs ?: now() else null)
            publish()
        }
        override fun onFailed(e: Exception) = onMain {
            signals = signals.copy(brakeHeldSinceMs = null)
            publish()
        }
    }

    fun start() = onMain {
        if (released || vehicle != null) return@onMain
        runCatching {
            val engine = Vehicle(app)
            engine.initialize()
            vehicle = engine
            engine.getGear().registerGearSelection(gearListener)
            engine.getBrake().registerBrakePedalStatus(brakeListener)
            // Seed from the current values so a car already parked counts straight away.
            engine.getGear().getCurrentGearSelectionState({ gear -> onMain {
                signals = signals.copy(gear = translate(gear)); publish()
            } }, { })
            engine.getBrake().getCurrentBrakePedalStatus({ pressed -> onMain {
                signals = signals.copy(brakeHeldSinceMs =
                    if (pressed == true) signals.brakeHeldSinceMs ?: now() else null)
                publish()
            } }, { })
        }.onFailure { release() }
    }

    fun release() = onMain {
        released = true
        main.removeCallbacks(recheck)
        val engine = vehicle
        vehicle = null
        runCatching { engine?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { engine?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { engine?.release() }
        if (stopped) { stopped = false; runCatching { onChanged(false) } }
    }

    private fun publish() {
        if (released) return
        main.removeCallbacks(recheck)
        val value = Stillness.isStopped(signals, now())
        // A brake that is down but not yet held long enough becomes a stop purely by time passing,
        // and no further callback would arrive to tell us.
        if (!value && signals.brakeHeldSinceMs != null) main.postDelayed(recheck, Stillness.BRAKE_HOLD_MS)
        if (value == stopped) return
        stopped = value
        runCatching { onChanged(value) }
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun translate(gear: VehicleGear?) = when (gear) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
        else -> GearPosition.DRIVE
    }
}
