package dev.changbak.explorer

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Read-only hardware probe. Reports counts/mode only, never destination, address or coordinates. */
object NaviConnectionCheck {
    fun run(context:Context):String {
        val main=Handler(Looper.getMainLooper());val done=CountDownLatch(1)
        val states=AtomicInteger();val positions=AtomicInteger();val destinations=AtomicInteger();val waypoints=AtomicInteger();val errors=AtomicInteger()
        var connected=false;var mode="NO_RESPONSE";var failure="none";var helper:NaviHelper?=null
        val listener=object:NaviHelperEventListener {
            override fun onRouteStateInfo(info:RouteStateInfo) { states.incrementAndGet();mode=info.naviMode.name }
            override fun onCurrentLocationInfo(info:CurrentLocationInfo) { positions.incrementAndGet() }
            override fun onDestinationInfo(info:DestinationInfo) { destinations.incrementAndGet() }
            override fun onWayPointInfo(list:List<WaypointInfo>) { waypoints.incrementAndGet() }
            override fun onError(error:NaviErrorCode) { errors.incrementAndGet() }
        }
        main.post {
            try {
                helper=NaviHelper(context).also { h -> h.initialize(onConnected={ main.post {
                    connected=true
                    runCatching { h.addListener(listener);h.getRouteStateInfo();h.getCurrentLocationInfo();h.getDestinationInfo();h.getWaypointInfo() }
                        .onFailure { failure=it.javaClass.simpleName }
                } },onFailed={ e -> failure=e.javaClass.simpleName;done.countDown() }) }
            } catch(e:Exception) { failure=e.javaClass.simpleName;done.countDown() }
            main.postDelayed({ done.countDown() },12_000)
        }
        check(done.await(16,TimeUnit.SECONDS)) { "Probe timed out" }
        val released=CountDownLatch(1)
        main.post { runCatching { helper?.removeListener(listener);helper?.release() };released.countDown() }
        check(released.await(3,TimeUnit.SECONDS))
        return "SDK_PROBE connected=$connected mode=$mode stateCallbacks=${states.get()} locationCallbacks=${positions.get()} destinationCallbacks=${destinations.get()} waypointCallbacks=${waypoints.get()} errors=${errors.get()} failure=$failure"
    }
}
