package com.memoria.mobile.reminders

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.memoria.mobile.BuildConfig
import com.memoria.mobile.MemoriaApp
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.remote.DeviceReportRequest
import java.util.UUID

/**
 * Tells the server whether this phone can actually ring, and which local
 * reminders it showed or was refused, so the admin panel can see why a patient
 * says the notifications stopped. Never blocks a reminder: callers run it after
 * the notification is already posted, and every failure is swallowed.
 */
object DeviceReporter {

    private const val TAG = "MemoriaDeviceReport"
    private const val PREFS = "memoria_device_report"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_SIGNATURE = "last_signature"
    private const val KEY_LAST_SENT_AT = "last_sent_at"

    /** A healthy phone still checks in a few times a day, so "app parou de falar" is visible too. */
    private const val HEARTBEAT_MS = 6 * 60 * 60 * 1000L

    suspend fun report(app: MemoriaApp, force: Boolean = false) {
        val repo = app.graph.repository
        if (!repo.isLoggedIn()) return
        val request = runCatching { snapshot(app, repo.reminderSound()) }.getOrElse {
            Log.w(TAG, "Estado das notificações não lido", it)
            return
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = signature(request)
        val now = System.currentTimeMillis()
        if (!force && !isDue(prefs.getString(KEY_LAST_SIGNATURE, null), signature, prefs.getLong(KEY_LAST_SENT_AT, 0L), now)) return
        when (val result = repo.sendDeviceReport(request)) {
            is ApiResult.Ok -> prefs.edit().putString(KEY_LAST_SIGNATURE, signature).putLong(KEY_LAST_SENT_AT, now).apply()
            is ApiResult.Err -> Log.w(TAG, "Estado das notificações não enviado: ${result.message}")
        }
    }

    internal fun isDue(lastSignature: String?, signature: String, lastSentAt: Long, now: Long): Boolean =
        lastSignature != signature || now - lastSentAt >= HEARTBEAT_MS

    /** Changes whenever a permission flips or a new reminder is logged. */
    internal fun signature(request: DeviceReportRequest): String = listOf(
        request.appVersion, request.notificationsEnabled, request.doseChannelEnabled, request.exactAlarmAllowed,
        request.batteryUnrestricted, request.fullScreenAllowed, request.scheduledAlarms, request.events.firstOrNull()?.id,
    ).joinToString("|")

    private fun snapshot(context: Context, soundId: String): DeviceReportRequest {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val app = context.applicationContext as MemoriaApp
        return DeviceReportRequest(
            deviceId = deviceId(context),
            appVersion = BuildConfig.VERSION_NAME,
            osVersion = Build.VERSION.RELEASE,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120),
            notificationsEnabled = MemoriaNotifications.canPost(context),
            doseChannelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notifications?.getNotificationChannel(ReminderSound.from(soundId).channelId)
                    ?.let { it.importance != NotificationManager.IMPORTANCE_NONE }
            } else {
                null
            },
            exactAlarmAllowed = app.graph.reminderScheduler.canScheduleExact(),
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName),
            fullScreenAllowed = if (Build.VERSION.SDK_INT >= 34) notifications?.canUseFullScreenIntent() else true,
            scheduledAlarms = app.graph.reminderScheduler.armedCount(),
            events = NotificationLog.entries(context),
        )
    }

    /** A random id per install — never a hardware identifier. */
    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }
}
