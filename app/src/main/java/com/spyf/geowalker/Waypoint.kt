package com.spyf.geowalker

/**
 * Одна точка маршрута — «подъезд». По ней фейковая локация должна пройти
 * в момент времени [atMinute] минут от старта прогона.
 */
data class Waypoint(
    val id: Long,
    var name: String,
    val lat: Double,
    val lon: Double,
    /** Через сколько минут от старта нужно оказаться в этой точке. */
    var atMinute: Double = 0.0,
    /** Сколько секунд «постоять» на точке (имитация захода в подъезд). */
    var dwellSec: Int = 20
)

/**
 * Маршрут целиком плюс настройки тайминга.
 */
data class Route(
    val waypoints: MutableList<Waypoint> = mutableListOf(),
    /** Стандартный интервал между соседними подъездами, минут. */
    var intervalMin: Double = 3.0,
    /** Час запуска (24ч). null — стартовать сразу. */
    var startHour: Int? = null,
    var startMinute: Int? = null,
    /** Повторять маршрут по кругу. */
    var loop: Boolean = false
)
