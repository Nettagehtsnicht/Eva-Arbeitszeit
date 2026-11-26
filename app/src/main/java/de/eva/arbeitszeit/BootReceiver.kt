package de.eva.arbeitszeit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Nach Neustart oder App-Update müssen die Stationen neu angemeldet werden */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            GeofenceHelper.register(context) { _, _ -> pending.finish() }
        }
    }
}
