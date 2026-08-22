package com.memoria.mobile.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memoria.mobile.MemoriaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-arms the reminder window after events that wipe or invalidate pending
 * alarms: a reboot, an app update, and a change to the clock or time zone.
 *
 * Android drops every alarm on reboot. Without this the user would silently stop
 * receiving reminders until they happened to open the app — which, for someone
 * who relies on the app to remember, could be days.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MemoriaApp ?: return
        Log.i(TAG, "A rearmar lembretes após ${intent.action}")

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.graph.repository.bootstrap()
                withTimeoutOrNull(WORK_TIMEOUT_MS) { app.graph.reminderScheduler.reschedule() }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao rearmar lembretes", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MemoriaReminders"
        const val WORK_TIMEOUT_MS = 8_000L
    }
}
