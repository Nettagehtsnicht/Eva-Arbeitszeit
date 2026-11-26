package de.eva.arbeitszeit

import android.content.Context

data class Station(val id: Int, val name: String, val shortName: String, val address: String, val color: Int)

object Stations {
    val ALL = listOf(
        Station(0, "Total Gütersloh", "Total", "Senner Straße 8, 33335 Gütersloh", 0xFFD7261E.toInt()),
        Station(1, "Elan Herzebrock", "Elan", "Clarholzer Straße 28, 33442 Herzebrock-Clarholz", 0xFF1565C0.toInt())
    )

    const val DEFAULT_RADIUS = 150

    private fun prefs(c: Context) = c.getSharedPreferences("stations", Context.MODE_PRIVATE)

    fun byId(id: Int): Station? = ALL.firstOrNull { it.id == id }

    fun getLatLng(c: Context, id: Int): Pair<Double, Double>? {
        val p = prefs(c)
        val lat = p.getString("lat_$id", null)?.toDoubleOrNull() ?: return null
        val lng = p.getString("lng_$id", null)?.toDoubleOrNull() ?: return null
        return Pair(lat, lng)
    }

    fun setLatLng(c: Context, id: Int, lat: Double, lng: Double, manual: Boolean) {
        prefs(c).edit()
            .putString("lat_$id", lat.toString())
            .putString("lng_$id", lng.toString())
            .putBoolean("manual_$id", manual)
            .apply()
    }

    /** true, wenn der Standort vor Ort mit "Ich bin hier" gesetzt wurde */
    fun isSetOnSite(c: Context, id: Int): Boolean = prefs(c).getBoolean("manual_$id", false)

    fun getRadius(c: Context, id: Int): Int = prefs(c).getInt("radius_$id", DEFAULT_RADIUS)

    fun setRadius(c: Context, id: Int, radius: Int) {
        prefs(c).edit().putInt("radius_$id", radius).apply()
    }
}
