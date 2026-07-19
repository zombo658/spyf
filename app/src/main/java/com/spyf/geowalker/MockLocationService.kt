package com.spyf.geowalker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/**
 * Крутит цикл, каждую секунду проталкивая фейковую локацию по маршруту.
 * Работает как foreground-сервис, чтобы система не убивала прогон в фоне.
 */
class MockLocationService : LifecycleService() {

    private lateinit var mock: MockLocationProvider
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        mock = MockLocationProvider(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification("Готовлюсь к прогону…"))
        startRun()
        return START_STICKY
    }

    private fun startRun() {
        if (job?.isActive == true) return

        val route = RouteStore(this).load()
        val engine = RouteEngine(route)
        if (engine.isEmpty) {
            updateNotification("Маршрут пуст — добавь подъезды")
            stopEverything()
            return
        }

        if (!mock.start()) {
            updateNotification("Нет прав mock location. Выбери приложение в настройках разработчика.")
            broadcast(STATE_ERROR_NO_MOCK)
            stopEverything()
            return
        }

        broadcast(STATE_RUNNING)

        job = lifecycleScope.launch {
            // Ждём запланированное время старта, если задано.
            waitForScheduledStart(route)

            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                val fix = engine.positionAt(elapsed)
                if (fix != null) {
                    // Небольшой шум ~1.5 м, чтобы трек не был идеально прямым.
                    val jLat = (Random.nextDouble() - 0.5) * 0.00003
                    val jLon = (Random.nextDouble() - 0.5) * 0.00003
                    val acc = 3f + Random.nextFloat() * 4f
                    mock.push(fix.lat + jLat, fix.lon + jLon, fix.bearing, fix.speed, acc)

                    val total = route.waypoints.size
                    val human = if (fix.speed > 0.2f) "иду к подъезду ${fix.currentIndex + 1}/$total"
                        else "подъезд ${fix.currentIndex + 1}/$total"
                    updateNotification(human)

                    if (fix.finished && !route.loop) {
                        updateNotification("Маршрут пройден ✓")
                        break
                    }
                }
                delay(1000)
            }
            broadcast(STATE_DONE)
            stopEverything()
        }
    }

    private suspend fun waitForScheduledStart(route: Route) {
        val h = route.startHour ?: return
        val m = route.startMinute ?: 0
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        var waitMs = target.timeInMillis - now.timeInMillis
        while (waitMs > 0 && lifecycleScope.isActive) {
            val mins = waitMs / 60000
            updateNotification("Старт в %02d:%02d (через %d мин)".format(h, m, mins))
            val step = minOf(waitMs, 30_000L)
            delay(step)
            waitMs -= step
        }
    }

    private fun stopEverything() {
        job?.cancel()
        job = null
        mock.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        mock.stop()
        super.onDestroy()
    }

    // --- Уведомление ---

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoWalker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Прогон маршрута", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun broadcast(state: String) {
        sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_STATE, state).setPackage(packageName))
    }

    companion object {
        const val CHANNEL_ID = "geowalker_run"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "com.spyf.geowalker.STOP"
        const val ACTION_STATE = "com.spyf.geowalker.STATE"
        const val EXTRA_STATE = "state"
        const val STATE_RUNNING = "running"
        const val STATE_DONE = "done"
        const val STATE_ERROR_NO_MOCK = "no_mock"
    }
}
