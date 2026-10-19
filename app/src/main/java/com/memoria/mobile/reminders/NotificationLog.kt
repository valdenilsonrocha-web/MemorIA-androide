package com.memoria.mobile.reminders

import android.content.Context
import com.memoria.mobile.data.remote.DeviceReportEvent
import com.memoria.mobile.di.MoshiProvider
import com.squareup.moshi.Types
import java.time.Instant

/**
 * The phone's own record of every local reminder it tried to show.
 *
 * When a patient says "não recebi o aviso", the server alone cannot tell an alarm
 * that never rang from one Android refused to show or one Doze held back for an
 * hour. This log is that evidence; [DeviceReporter] sends it to the admin panel.
 * It is written before any network work, so it survives a phone with no signal.
 */
object NotificationLog {

    const val KIND_DOSE = "dose"
    const val KIND_STOCK = "stock"
    const val KIND_CONSULTATION = "consultation"

    /** Enough for several days of doses; the server keeps its own longer copy. */
    const val MAX_ENTRIES = 60

    private const val PREFS = "memoria_notification_log"
    private const val KEY_ENTRIES = "entries"

    private val adapter by lazy {
        MoshiProvider.instance.adapter<List<DeviceReportEvent>>(
            Types.newParameterizedType(List::class.java, DeviceReportEvent::class.java)
        )
    }

    fun record(
        context: Context,
        kind: String,
        label: String,
        plannedAtMillis: Long?,
        shown: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val event = entry(kind, label, plannedAtMillis, shown, nowMillis)
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val updated = append(decode(prefs.getString(KEY_ENTRIES, null)), event)
            // commit, not apply: the receiver's process may die right after onReceive.
            prefs.edit().putString(KEY_ENTRIES, adapter.toJson(updated)).commit()
        }
    }

    fun entries(context: Context): List<DeviceReportEvent> = synchronized(this) {
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null))
    }

    internal fun entry(
        kind: String,
        label: String,
        plannedAtMillis: Long?,
        shown: Boolean,
        nowMillis: Long,
    ): DeviceReportEvent = DeviceReportEvent(
        id = "$kind-$nowMillis-${label.hashCode().toUInt().toString(16)}",
        kind = kind,
        label = label.take(120),
        scheduledFor = plannedAtMillis?.let { Instant.ofEpochMilli(it).toString() },
        firedAt = Instant.ofEpochMilli(nowMillis).toString(),
        outcome = if (shown) "shown" else "blocked",
        delayMinutes = plannedAtMillis?.let { ((nowMillis - it) / 60_000L).coerceIn(0L, 1440L).toInt() },
    )

    internal fun append(current: List<DeviceReportEvent>, event: DeviceReportEvent): List<DeviceReportEvent> =
        (listOf(event) + current.filter { it.id != event.id }).take(MAX_ENTRIES)

    private fun decode(raw: String?): List<DeviceReportEvent> =
        raw?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()
}
