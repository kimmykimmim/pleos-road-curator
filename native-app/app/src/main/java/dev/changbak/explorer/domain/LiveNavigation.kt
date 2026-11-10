package dev.changbak.explorer.domain

data class TimedPosition(val point: GeoPoint, val receivedAtMs: Long)
data class NaviStop(val key: String, val name: String, val point: GeoPoint, val etaSeconds: Int,
    val remainingMeters: Int, val receivedAtMs: Long)
data class NaviArrival(val stopKey: String, val receivedAtMs: Long)
data class NaviFrame(val routeId: String, val sequence: Long, val nowMs: Long, val guiding: Boolean,
    val guidanceAtMs: Long, val position: TimedPosition?, val stops: List<NaviStop>,
    val arrivals: List<NaviArrival> = emptyList())
data class LiveDiscovery(val fix: CuratorFix, val timedCount: Int, val nearbyCount: Int)

/** Only per-stop SDK ETAs may announce five minutes. Proximity never fabricates an ETA or visibility. */
class LiveNavigation(private val placesSource: () -> List<LocatedPlace> = { LivePlaces.all }) {
    /** A fixed list. The live journey's list grows when a guardian adds a place. */
    constructor(places: List<LocatedPlace>) : this({ places })

    private val places: List<LocatedPlace> get() = placesSource()

    private var routeId: String?=null
    private var sequence=-1L
    private var lastPosition: TimedPosition?=null
    private val tracks=mutableMapOf<String,Track>()
    private val reached=mutableMapOf<String,Long>()
    private class Track(var min:Double, var last:Double, var approached:Boolean=false, var away:Int=0, var passedAt:Long?=null)
    fun reset() { routeId=null;sequence=-1;lastPosition=null;tracks.clear();reached.clear() }
    private fun recent(now:Long,then:Long,limit:Long) = then>=0 && now>=then && now-then<=limit
    fun discover(frame:NaviFrame): LiveDiscovery {
        fun invalid()=LiveDiscovery(CuratorFix(frame.routeId,frame.sequence,emptyList(),fresh=false),0,0)
        if(frame.routeId.isBlank() || !frame.guiding || !recent(frame.nowMs,frame.guidanceAtMs,15_000)) { reset();return invalid() }
        if(routeId!=frame.routeId) { reset();routeId=frame.routeId }
        if(frame.sequence<=sequence) return invalid()
        sequence=frame.sequence
        val position=frame.position?.takeIf { it.point.valid() && recent(frame.nowMs,it.receivedAtMs,10_000) }
        val previous=lastPosition
        if(position==null) { tracks.clear();lastPosition=null }
        else if(previous==null || position.receivedAtMs>previous.receivedAtMs) {
            val dt=previous?.let { (position.receivedAtMs-it.receivedAtMs)/1000.0 }
            // Reacquisition and implausible jumps cannot manufacture a passage.
            if(dt!=null && (dt>12 || position.point.metersTo(previous.point)>70*dt+80)) tracks.clear()
            places.filter { it.proximityEnabled }.forEach { p ->
                val distance=position.point.metersTo(p.anchor)
                val track=tracks[p.id]
                if(distance>p.radiusMeters+450 && track?.passedAt==null) tracks.remove(p.id)
                else if(track==null) tracks[p.id]=Track(distance,distance)
                else if(track.passedAt==null) {
                    if(distance<track.min-15) track.approached=true
                    track.min=minOf(track.min,distance)
                    // Two samples moving away by >=60m after an actual approach within the zone.
                    track.away=if(track.approached && track.min<=p.radiusMeters && distance>=track.min+60 && distance>track.last+5) track.away+1 else 0
                    if(track.away>=2) track.passedAt=frame.nowMs
                    track.last=distance
                }
            }
            lastPosition=position
        }
        val timed=frame.stops.filter { it.etaSeconds>=0 && it.remainingMeters>=0 && recent(frame.nowMs,it.receivedAtMs,12_000) }
            .mapNotNull { stop -> LivePlaces.match(stop.name,stop.point,places)?.let { it.id to stop } }.toMap()
        if(position==null && timed.isEmpty()) return invalid()
        // Arrival events must identify an already matched stop; ETA zero alone is not arrival.
        timed.forEach { (id,stop) ->
            if(frame.arrivals.any { it.stopKey==stop.key && recent(frame.nowMs,it.receivedAtMs,15_000) } &&
                position!=null && position.point.metersTo(stop.point)<=500) reached.putIfAbsent(id,frame.nowMs)
        }
        val approaches=places.mapNotNull { p ->
            val stop=timed[p.id];val track=tracks[p.id]
            val passed=track?.passedAt?.let { recent(frame.nowMs,it,15_000) }==true
            val arrived=reached[p.id]?.let { recent(frame.nowMs,it,15_000) }==true
            val nearby=position!=null && p.proximityEnabled && position.point.metersTo(p.anchor)<=p.radiusMeters
            val tracking=position!=null && track!=null && track.min<=p.radiusMeters && track.approached && track.passedAt==null
            if(stop==null && !nearby && !passed && !arrived && !tracking) return@mapNotNull null
            // Which window to look through, from how the car actually moved between these two fixes.
            val side=WindowDirection.of(previous,position,p.anchor)
            PlaceApproach(p.id,stop?.etaSeconds,passed=passed,nearby=nearby,arrived=arrived,side=side)
        }
        return LiveDiscovery(CuratorFix(frame.routeId,frame.sequence,approaches),timed.size,approaches.count { it.nearby })
    }
}
