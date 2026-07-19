package com.spyf.geowalker

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * Тонкая обёртка над штатным механизмом Android mock location
 * (LocationManager.addTestProvider / setTestProviderLocation).
 *
 * Чтобы это работало, приложение должно быть выбрано в
 * «Настройки → Для разработчиков → Приложение для фиктивных местоположений».
 * Root не требуется.
 */
class MockLocationProvider(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var started = false

    /** @return true если провайдеры успешно зарегистрированы. */
    fun start(): Boolean {
        var ok = true
        for (p in providers) {
            try {
                runCatching { lm.removeTestProvider(p) }
                @Suppress("DEPRECATION")
                lm.addTestProvider(
                    p,
                    /* requiresNetwork = */ false,
                    /* requiresSatellite = */ false,
                    /* requiresCell = */ false,
                    /* hasMonetaryCost = */ false,
                    /* supportsAltitude = */ true,
                    /* supportsSpeed = */ true,
                    /* supportsBearing = */ true,
                    /* powerRequirement = */ 1, // Criteria.POWER_LOW
                    /* accuracy = */ 1          // Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(p, true)
            } catch (e: SecurityException) {
                // Приложение не выбрано как mock location app.
                ok = false
            } catch (e: IllegalArgumentException) {
                // Провайдер недоступен на устройстве — не критично.
            }
        }
        started = ok
        return ok
    }

    fun push(lat: Double, lon: Double, bearing: Float, speed: Float, accuracy: Float = 3f) {
        if (!started) return
        for (p in providers) {
            val loc = Location(p).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                this.accuracy = accuracy
                this.bearing = bearing
                this.speed = speed
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 1f
                    speedAccuracyMetersPerSecond = 0.5f
                    verticalAccuracyMeters = 1f
                }
            }
            runCatching { lm.setTestProviderLocation(p, loc) }
        }
    }

    fun stop() {
        for (p in providers) {
            runCatching { lm.setTestProviderEnabled(p, false) }
            runCatching { lm.removeTestProvider(p) }
        }
        started = false
    }
}
