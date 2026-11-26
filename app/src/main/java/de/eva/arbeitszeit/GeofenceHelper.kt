package de.eva.arbeitszeit

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceHelper {

    fun hasForeground(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(c: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(c: Context): PendingIntent {
        val i = Intent(c, GeofenceReceiver::class.java).setAction("de.eva.arbeitszeit.GEOFENCE")
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(c, 0, i, flags)
    }

    /** Meldet beide Stationen beim System an. onResult(ok, Fehlertext) */
    @SuppressLint("MissingPermission")
    fun register(c: Context, onResult: ((Boolean, String?) -> Unit)? = null) {
        val ctx = c.applicationContext
        if (!hasForeground(ctx) || !hasBackground(ctx)) {
            onResult?.invoke(false, null)
            return
        }
        val fences = Stations.ALL.mapNotNull { st ->
            val ll = Stations.getLatLng(ctx, st.id) ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId("station_${st.id}")
                .setCircularRegion(ll.first, ll.second, Stations.getRadius(ctx, st.id).toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(60_000)
                .build()
        }
        if (fences.isEmpty()) {
            onResult?.invoke(false, "Die Standorte der Stationen fehlen noch.")
            return
        }
        val client = LocationServices.getGeofencingClient(ctx)
        val pi = pendingIntent(ctx)
        client.removeGeofences(pi).addOnCompleteListener {
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            client.addGeofences(req, pi)
                .addOnSuccessListener { onResult?.invoke(true, null) }
                .addOnFailureListener { e ->
                    val code = (e as? ApiException)?.statusCode
                    val msg = if (code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
                        "Standort ist am Handy ausgeschaltet oder „Google-Standortgenauigkeit“ ist aus."
                    else
                        "Automatische Erkennung konnte nicht gestartet werden (${e.message})."
                    onResult?.invoke(false, msg)
                }
        }
    }
}
