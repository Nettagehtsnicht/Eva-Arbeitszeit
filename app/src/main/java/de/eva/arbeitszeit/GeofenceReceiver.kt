package de.eva.arbeitszeit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val now = System.currentTimeMillis()
        val ids = event.triggeringGeofences
            ?.mapNotNull { it.requestId.removePrefix("station_").toIntOrNull() }
            ?: return
        val db = Db.get(context)
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach { id ->
                if (db.arrive(id, now)) Notify.arrived(context, id, now)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { id ->
                val v = db.leave(id, now)
                if (v != null && now - v.start >= Db.MIN_MS) Notify.left(context, id, now - v.start)
            }
        }
    }
}
