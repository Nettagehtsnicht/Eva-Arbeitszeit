package de.eva.arbeitszeit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notify {
    private const val CHANNEL = "besuche"

    fun arrived(c: Context, station: Int, t: Long) {
        val name = Stations.byId(station)?.name ?: return
        val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(t))
        show(c, station, "Angekommen: $name", "Erfasst ab $time Uhr")
    }

    fun left(c: Context, station: Int, durationMs: Long) {
        val name = Stations.byId(station)?.name ?: return
        show(c, station, "Gefahren: $name", "Heute dort: ${Stats.fmt(durationMs)}")
    }

    private fun show(c: Context, station: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ankommen & Gehen", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(100 + station, n)
    }
}
