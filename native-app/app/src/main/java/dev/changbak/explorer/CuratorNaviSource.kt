package dev.changbak.explorer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.*
import dev.changbak.explorer.domain.*

/** Read-only public SDK. Never sets destinations, inserts waypoints or recomputes the driver's route. */
class CuratorNaviSource(context: Context, private val status: (String) -> Unit,
    private val onFrame: (NaviFrame) -> Unit = {}) {
    private val app=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private var sdk:NaviHelper?=null
    private var listener:NaviHelperEventListener?=null
    private var epoch=0L
    private var active=false
    private var guiding=false
    private var guidanceAt=Long.MIN_VALUE
    private var routeRevision=0L
    private var sdkRouteId:String?=null
    private var seq=0L
    private var position:TimedPosition?=null
    private var destination:NaviStop?=null
    private var waypoints=emptyList<NaviStop>()
    private var arrivals=emptyList<NaviArrival>()
    private var polls=0
    private fun now()=SystemClock.elapsedRealtime()
    private fun emit() { if(active) onFrame(NaviFrame("$epoch:$routeRevision:${sdkRouteId.orEmpty()}",++seq,now(),guiding,guidanceAt,position,listOfNotNull(destination)+waypoints,arrivals)) }
    private fun invalidate(message:String) {
        routeRevision++;guiding=false;guidanceAt=now();position=null;destination=null;waypoints=emptyList();arrivals=emptyList()
        status(message);emit()
    }
    private fun queryAll() {
        runCatching { sdk?.getRouteStateInfo();sdk?.getCurrentLocationInfo();sdk?.getDestinationInfo();sdk?.getWaypointInfo() }
            .onFailure { invalidate("내비게이션 조회가 중단됐어요. 연결을 다시 확인해 주세요.") }
    }
    private val poll=object:Runnable {
        override fun run() {
            if(!active) return
            runCatching {
                sdk?.getRouteStateInfo();sdk?.getCurrentLocationInfo()
                if(polls++%3==0) { sdk?.getDestinationInfo();sdk?.getWaypointInfo() }
            }.onFailure { invalidate("내비게이션 정보가 끊겨 안내를 쉬고 있어요.") }
            emit();handler.postDelayed(this,2_000)
        }
    }
    fun connect() {
        close();active=true;val token=epoch
        status("내비게이션의 현재 경로를 확인하고 있어요.")
        val events=object:NaviHelperEventListener {
            private fun dispatch(block:()->Unit) { handler.post { if(active && epoch==token) block() } }
            override fun onRouteStateInfo(info:RouteStateInfo)=dispatch {
                guiding=info.naviMode in setOf(NaviMode.ROUTE_GUIDING,NaviMode.SIMULATION_GUIDING)
                guidanceAt=now()
                if(!guiding) { position=null;destination=null;waypoints=emptyList();arrivals=emptyList() }
                // Said to a child in the back seat, so it says what is happening, not how.
                status(if(guiding) "내비와 연결됐어. 길을 보고 있어." else "내비에 목적지를 정하면 시작해.")
                emit()
            }
            override fun onCurrentLocationInfo(info:CurrentLocationInfo)=dispatch {
                if(!guiding) return@dispatch
                val key=info.routeId?.takeIf { it.isNotBlank() }
                if(key!=null && sdkRouteId!=null && key!=sdkRouteId) {
                    invalidate("경로가 바뀌어 장소를 다시 확인해요.");sdkRouteId=key;queryAll();return@dispatch
                }
                sdkRouteId=key ?: sdkRouteId
                position=TimedPosition(GeoPoint(info.latitude,info.longitude),now());emit()
            }
            override fun onDestinationInfo(info:DestinationInfo)=dispatch {
                if(!guiding) return@dispatch
                destination=NaviStop("destination",info.name,GeoPoint(info.latitude,info.longitude),info.remainTime,info.remainDistance,now());emit()
            }
            override fun onWayPointInfo(list:List<WaypointInfo>)=dispatch {
                if(!guiding) return@dispatch
                waypoints=list.mapIndexed { i,p -> NaviStop("waypoint:$i",p.name,GeoPoint(p.latitude,p.longitude),p.remainTime,p.remainDistance,now()) };emit()
            }
            override fun onDrivingInfo(info:DrivingInfo)=dispatch {
                if(!guiding) return@dispatch
                destination=destination?.let { p -> if(p.name==info.destination.name) p.copy(etaSeconds=info.destination.duration,remainingMeters=info.destination.distance,receivedAtMs=now()) else p }
                waypoints=waypoints.mapIndexed { i,p -> info.waypoints.getOrNull(i)?.takeIf { it.name==p.name }?.let { p.copy(etaSeconds=it.duration,remainingMeters=it.distance,receivedAtMs=now()) } ?: p }
                emit()
            }
            private fun arrival(name:String, candidates:List<NaviStop>) {
                val stop=candidates.singleOrNull { it.name==name } ?: return
                arrivals=arrivals+NaviArrival(stop.key,now());emit()
            }
            override fun onDestinationArrived(info:DestinationArrivedInfo)=dispatch { arrival(info.name,listOfNotNull(destination)) }
            override fun onWayPointArrived(info:WaypointArrivedInfo)=dispatch { arrival(info.name,waypoints) }
            override fun onRouteStarted(info:RouteStartInfo)=dispatch { invalidate("새 경로를 확인하고 있어요.") }
            override fun onRoutingCompleted()=dispatch { queryAll() }
            override fun onReRouteEnded()=dispatch { invalidate("바뀐 길에서 장소를 다시 찾고 있어요.");queryAll() }
            override fun onDestinationChanged()=dispatch { invalidate("목적지가 바뀌었어요.");queryAll() }
            override fun onWaypointChanged(info:WaypointChangedInfo)=dispatch { invalidate("경유지가 바뀌었어요.");queryAll() }
            override fun onRouteCancelled()=dispatch { invalidate("경로 안내가 취소돼 이야기를 멈췄어요.") }
            override fun onNavigationStatus(value:NavigationStatus)=dispatch {
                if(value!=NavigationStatus.ROUTE_DRIVE) invalidate("주행 안내를 기다리고 있어요.") else queryAll()
            }
            override fun onError(error:NaviErrorCode)=dispatch { invalidate("내비게이션이 경로를 확인하지 못했어요.") }
        }
        listener=events
        try {
            sdk=NaviHelper(app).also { helper ->
                helper.initialize(onConnected={ handler.post {
                    if(!active || token!=epoch || sdk!==helper) return@post
                    runCatching { helper.addListener(events);queryAll();handler.post(poll) }
                        .onFailure { invalidate("내비게이션 조회 권한을 확인해 주세요.") }
                } },onFailed={ handler.post { if(active && epoch==token) invalidate("내비게이션 서비스에 연결하지 못했어요.") } })
            }
        } catch(_:Exception) { invalidate("이 환경에서는 내비게이션 연결을 사용할 수 없어요.") }
    }
    fun close() {
        active=false;epoch++;handler.removeCallbacksAndMessages(null)
        listener?.let { runCatching { sdk?.removeListener(it) } };runCatching { sdk?.release() }
        sdk=null;listener=null;guiding=false;position=null;destination=null;waypoints=emptyList();arrivals=emptyList();sdkRouteId=null;polls=0
    }
}
