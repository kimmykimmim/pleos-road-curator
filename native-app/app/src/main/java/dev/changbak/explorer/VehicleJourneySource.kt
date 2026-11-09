package dev.changbak.explorer

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import dev.changbak.explorer.domain.VehicleDistanceIntegrator

class VehicleJourneySource(private val onDistance: (Float) -> Unit, private val onStatus: (String) -> Unit) {
    private var manager: CarPropertyManager? = null
    private val integrator = VehicleDistanceIntegrator()
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) { integrator.reset(); onStatus("차량 이동 정보 대기 중"); return }
            val speed = value.value as? Float ?: return
            val distance = integrator.sample(speed, value.timestamp)
            onStatus("차량 이동 연결됨")
            if (distance > 0f) onDistance(distance)
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { integrator.reset(); onStatus("차량 이동 정보 대기 중") }
    }
    fun connect(car: Car) {
        close()
        try {
            manager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val registered = manager!!.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 1f)
            onStatus(if (registered) "차량 이동 정보 대기 중" else "이 차량은 이동 정보를 제공하지 않아요")
        } catch (_: Exception) { close(); onStatus("보호자 메뉴에서 차량 이동 연결을 확인해 주세요") }
    }
    fun close() { runCatching { manager?.unregisterCallback(callback) }; manager = null; integrator.reset() }
}
