package com.spyf.geowalker

import android.Manifest
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.spyf.geowalker.databinding.ActivityMainBinding
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.runtime.image.ImageProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: RouteStore
    private lateinit var route: Route
    private lateinit var adapter: WaypointAdapter

    private lateinit var map: Map
    private var objects: MapObjectCollection? = null
    private var addMode = true
    private var nextId = 1L

    // ВАЖНО: MapKit держит слушатели по слабой ссылке, поэтому его нужно
    // хранить в поле, иначе GC его удалит и тапы перестанут ловиться.
    private val inputListener = object : InputListener {
        override fun onMapTap(map: Map, point: Point) {
            if (addMode) addWaypoint(point.latitude, point.longitude)
        }
        override fun onMapLongTap(map: Map, point: Point) {
            addWaypoint(point.latitude, point.longitude)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.getStringExtra(MockLocationService.EXTRA_STATE)) {
                MockLocationService.STATE_RUNNING -> setRunningUi(true)
                MockLocationService.STATE_DONE -> setRunningUi(false)
                MockLocationService.STATE_ERROR_NO_MOCK -> {
                    setRunningUi(false)
                    showMockHint()
                }
            }
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* результат не блокирует работу карты */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MapKit нужно инициализировать до inflate карты.
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
        MapKitFactory.initialize(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = RouteStore(this)
        route = store.load()
        nextId = (route.waypoints.maxOfOrNull { it.id } ?: 0L) + 1

        map = binding.mapview.mapWindow.map
        objects = map.mapObjects.addCollection()

        // Стартовая камера — Москва, если своих точек ещё нет.
        val start = route.waypoints.firstOrNull()
        val center = if (start != null) Point(start.lat, start.lon) else Point(55.751244, 37.618423)
        map.move(CameraPosition(center, 15f, 0f, 0f))

        map.addInputListener(inputListener)

        setupUi()
        requestPermissions()
        redraw()
    }

    private fun setupUi() {
        adapter = WaypointAdapter(route.waypoints) { idx ->
            route.waypoints.removeAt(idx)
            recomputeTiming()
            persistAndRefresh()
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.intervalInput.setText(route.intervalMin.toString())
        binding.dwellInput.setText(defaultDwell().toString())
        binding.loopSwitch.isChecked = route.loop
        updateStartLabel()
        updateAddModeLabel()

        binding.addModeBtn.setOnClickListener {
            addMode = !addMode
            updateAddModeLabel()
            Toast.makeText(this, if (addMode) "Тапай по карте, чтобы ставить подъезды" else "Добавление выключено", Toast.LENGTH_SHORT).show()
        }

        binding.clearBtn.setOnClickListener {
            route.waypoints.clear()
            persistAndRefresh()
        }

        binding.startTimeBtn.setOnClickListener { pickStartTime() }

        binding.loopSwitch.setOnCheckedChangeListener { _, checked ->
            route.loop = checked
            store.save(route)
        }

        binding.startBtn.setOnClickListener { startRun() }
        binding.stopBtn.setOnClickListener { stopRun() }
        binding.devSettingsBtn.setOnClickListener { openDevSettings() }
    }

    private fun updateAddModeLabel() {
        binding.addModeBtn.text = if (addMode) "Добавление ВКЛ (тапай по карте)" else "Добавление ВЫКЛ"
    }

    private fun defaultDwell(): Int = route.waypoints.firstOrNull()?.dwellSec ?: 20

    private fun addWaypoint(lat: Double, lon: Double) {
        applyInputs()
        val wp = Waypoint(
            id = nextId++,
            name = "Подъезд ${route.waypoints.size + 1}",
            lat = lat,
            lon = lon,
            dwellSec = binding.dwellInput.text.toString().toIntOrNull() ?: 20
        )
        route.waypoints.add(wp)
        recomputeTiming()
        persistAndRefresh()
    }

    /** Раскидывает atMinute равномерно по интервалу. */
    private fun recomputeTiming() {
        val interval = binding.intervalInput.text.toString().toDoubleOrNull() ?: route.intervalMin
        route.intervalMin = interval
        route.waypoints.forEachIndexed { i, w -> w.atMinute = i * interval }
    }

    private fun applyInputs() {
        binding.intervalInput.text.toString().toDoubleOrNull()?.let { route.intervalMin = it }
        route.loop = binding.loopSwitch.isChecked
    }

    private fun persistAndRefresh() {
        store.save(route)
        adapter.notifyDataSetChanged()
        redraw()
    }

    private fun redraw() {
        val col = objects ?: return
        col.clear()
        val pin = ImageProvider.fromResource(this, R.drawable.ic_pin)
        val pts = route.waypoints.map { Point(it.lat, it.lon) }
        pts.forEach { col.addPlacemark(it, pin) }
        if (pts.size >= 2) col.addPolyline(Polyline(pts))
        binding.countLabel.text = "Подъездов: ${route.waypoints.size}"
    }

    private fun pickStartTime() {
        val h = route.startHour ?: 9
        val m = route.startMinute ?: 0
        TimePickerDialog(this, { _, hh, mm ->
            route.startHour = hh
            route.startMinute = mm
            store.save(route)
            updateStartLabel()
        }, h, m, true).show()
    }

    private fun updateStartLabel() {
        val h = route.startHour
        binding.startTimeBtn.text = if (h != null)
            "Старт в %02d:%02d".format(h, route.startMinute ?: 0)
        else "Старт: сразу"
    }

    private fun startRun() {
        applyInputs()
        recomputeTiming()
        store.save(route)
        if (route.waypoints.isEmpty()) {
            Toast.makeText(this, "Сначала добавь подъезды", Toast.LENGTH_SHORT).show()
            return
        }
        val svc = Intent(this, MockLocationService::class.java)
        ContextCompat.startForegroundService(this, svc)
    }

    private fun stopRun() {
        val svc = Intent(this, MockLocationService::class.java)
            .setAction(MockLocationService.ACTION_STOP)
        startService(svc)
        setRunningUi(false)
    }

    private fun setRunningUi(running: Boolean) {
        binding.startBtn.isEnabled = !running
        binding.stopBtn.isEnabled = running
        binding.statusLabel.text = if (running) "Прогон идёт…" else "Остановлено"
    }

    private fun showMockHint() {
        Toast.makeText(
            this,
            "Выбери GeoWalker в «Приложение для фиктивных местоположений» (Настройки разработчика).",
            Toast.LENGTH_LONG
        ).show()
        openDevSettings()
    }

    private fun openDevSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        binding.mapview.onStart()
        registerReceiverCompat()
    }

    override fun onStop() {
        binding.mapview.onStop()
        MapKitFactory.getInstance().onStop()
        runCatching { unregisterReceiver(stateReceiver) }
        super.onStop()
    }

    private fun registerReceiverCompat() {
        val filter = IntentFilter(MockLocationService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
    }
}
