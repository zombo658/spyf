package com.spyf.geowalker

import kotlin.math.max

/**
 * Детерминированно вычисляет, где должна находиться фейковая локация
 * в момент [elapsedSec] секунд от старта прогона.
 *
 * Модель: к точке i приходим в момент atMinute[i], стоим dwellSec
 * (заходим в «подъезд»), потом едем к следующей точке так, чтобы
 * успеть к её atMinute.
 */
class RouteEngine(private val route: Route) {

    data class Fix(
        val lat: Double,
        val lon: Double,
        val bearing: Float,
        val speed: Float,
        val currentIndex: Int,
        val finished: Boolean
    )

    // type: 0 = стоим на точке a; 1 = едем a -> b
    private data class Seg(val start: Double, val end: Double, val type: Int, val a: Int, val b: Int)

    private val segs: List<Seg>
    val cycleLenSec: Double

    init {
        val wps = route.waypoints
        val list = ArrayList<Seg>()
        if (wps.isNotEmpty()) {
            for (i in wps.indices) {
                val arrive = wps[i].atMinute * 60.0
                val dwellEnd = arrive + wps[i].dwellSec
                list.add(Seg(arrive, dwellEnd, 0, i, i))
                if (i < wps.lastIndex) {
                    val nextArrive = wps[i + 1].atMinute * 60.0
                    list.add(Seg(dwellEnd, max(dwellEnd, nextArrive), 1, i, i + 1))
                }
            }
            if (route.loop && wps.size > 1) {
                val lastDwellEnd = wps.last().atMinute * 60.0 + wps.last().dwellSec
                val ret = lastDwellEnd + route.intervalMin * 60.0
                list.add(Seg(lastDwellEnd, ret, 1, wps.lastIndex, 0))
            }
        }
        segs = list
        cycleLenSec = if (segs.isEmpty()) 0.0 else segs.last().end
    }

    val isEmpty: Boolean get() = segs.isEmpty()

    fun positionAt(elapsedSec: Double): Fix? {
        val wps = route.waypoints
        if (segs.isEmpty()) return null

        var t = elapsedSec
        val looped = route.loop && cycleLenSec > 0
        if (looped) t %= cycleLenSec

        // До прихода на первую точку — стоим на ней.
        val first = segs.first()
        if (t < first.start) {
            val w = wps[first.a]
            return Fix(w.lat, w.lon, 0f, 0f, first.a, false)
        }

        // За пределами маршрута (без цикла) — финиш на последней точке.
        if (!looped && t >= segs.last().end) {
            val w = wps.last()
            return Fix(w.lat, w.lon, 0f, 0f, wps.lastIndex, true)
        }

        val seg = segs.firstOrNull { t >= it.start && t < it.end } ?: segs.last()
        return if (seg.type == 0) {
            val w = wps[seg.a]
            Fix(w.lat, w.lon, 0f, 0f, seg.a, false)
        } else {
            val a = wps[seg.a]
            val b = wps[seg.b]
            val dur = (seg.end - seg.start).coerceAtLeast(0.001)
            val f = (t - seg.start) / dur
            val (lat, lon) = Geo.lerp(a.lat, a.lon, b.lat, b.lon, f)
            val dist = Geo.distanceMeters(a.lat, a.lon, b.lat, b.lon)
            val speed = (dist / dur).toFloat()
            val brg = Geo.bearing(a.lat, a.lon, b.lat, b.lon)
            Fix(lat, lon, brg, speed, seg.b, false)
        }
    }
}
