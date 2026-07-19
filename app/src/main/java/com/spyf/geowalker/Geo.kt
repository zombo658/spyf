package com.spyf.geowalker

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Небольшие геометрические помощники для интерполяции маршрута. */
object Geo {

    private const val EARTH_R = 6_371_000.0 // метры

    /** Расстояние по большому кругу, метры. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Азимут движения из точки 1 в точку 2, градусы 0..360. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    /** Линейная интерполяция координаты между двумя точками (на коротких отрезках достаточно точно). */
    fun lerp(lat1: Double, lon1: Double, lat2: Double, lon2: Double, t: Double): Pair<Double, Double> {
        val tt = t.coerceIn(0.0, 1.0)
        return Pair(lat1 + (lat2 - lat1) * tt, lon1 + (lon2 - lon1) * tt)
    }
}
