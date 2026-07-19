package com.spyf.geowalker

import android.content.Context
import com.google.gson.Gson

/** Простое сохранение маршрута в SharedPreferences как JSON. */
class RouteStore(context: Context) {

    private val prefs = context.getSharedPreferences("geowalker", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): Route {
        val json = prefs.getString(KEY, null) ?: return Route()
        return try {
            gson.fromJson(json, Route::class.java) ?: Route()
        } catch (e: Exception) {
            Route()
        }
    }

    fun save(route: Route) {
        prefs.edit().putString(KEY, gson.toJson(route)).apply()
    }

    companion object {
        private const val KEY = "route_json"
    }
}
