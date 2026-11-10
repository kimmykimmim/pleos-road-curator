package dev.changbak.explorer.domain

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
    fun metersTo(other: GeoPoint): Double {
        val a = Math.toRadians(latitude); val b = Math.toRadians(other.latitude)
        val d = sin((b-a)/2).pow(2) + cos(a)*cos(b)*sin(Math.toRadians(other.longitude-longitude)/2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(d.coerceIn(0.0,1.0)))
    }
    /** Compass degrees from this point to [other], 0 at north, increasing clockwise. */
    fun bearingTo(other: GeoPoint): Double {
        val a = Math.toRadians(latitude); val b = Math.toRadians(other.latitude)
        val dl = Math.toRadians(other.longitude - longitude)
        val y = sin(dl) * cos(b)
        val x = cos(a) * sin(b) - sin(a) * cos(b) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

/** Source map markers are encounter anchors, not entrance/road/visibility coordinates. */
data class LocatedPlace(val id: String, val aliases: Set<String>, val anchor: GeoPoint,
    val radiusMeters: Double, val locationSource: String, val proximityEnabled: Boolean = true,
    val identityRadiusMeters: Double = 700.0)

object LivePlaces {
    val all = listOf(
        LocatedPlace("lotte", setOf("롯데월드타워","롯데월드타워서울스카이","서울스카이","Lotte World Tower","Seoul Sky"),
            GeoPoint(37.5137519612953,127.104446890835),400.0,"https://english.visitseoul.net/attractions/SeoulSky_/44181"),
        LocatedPlace("building63",setOf("63빌딩","63스퀘어","63시티","63 Square","63 Building"),
            GeoPoint(37.51966514725452,126.9399110340612),300.0,"https://english.visitseoul.net/attractions/63-Square_/210"),
        LocatedPlace("sungnyemun",setOf("숭례문","남대문","Sungnyemun","Sungnyemun Gate"),
            GeoPoint(37.5599777857,126.9753357686),220.0,"https://english.visitseoul.net/PalaceArea/Sungnyemun-Gate-Namdaemun-Gate/ENP022888"),
        LocatedPlace("gwanghwamun",setOf("광화문","경복궁광화문","Gwanghwamun Gate"),
            GeoPoint(37.57701264219352,126.97661272532484),300.0,"https://english.visitseoul.net/area/Changing-of-the-Royal-Guard-at-Gyeongbokgung-Palace-en/ENP008256"),
        LocatedPlace("cheongjinok",setOf("청진옥","청진옥본점","Cheongjinok"),
            GeoPoint(37.5710725,126.979766),160.0,"https://futureheritage.seoul.go.kr/futureHeritageMeet/futureHeritage/view.do?htNo=135"),
        // An island has no single passing point. Match an explicitly named route stop only.
        LocatedPlace("yeouido",setOf("여의도","Yeouido"),GeoPoint(37.525,126.925),0.0,
            "https://museum.seoul.go.kr/www/board/NR_boardView.do?bbsCd=1002&seq=20210419212409075&sso=ok",
            proximityEnabled=false,identityRadiusMeters=3000.0)
    )
    fun normalize(name: String) = name.lowercase().replace(Regex("[\\s\\p{Z}\\p{P}]"), "")
    fun match(name: String, point: GeoPoint, places: List<LocatedPlace> = all): LocatedPlace? {
        if(!point.valid()) return null
        val key=normalize(name)
        return places.singleOrNull { p -> p.aliases.any { normalize(it)==key } && p.anchor.metersTo(point)<=p.identityRadiusMeters }
    }
}
