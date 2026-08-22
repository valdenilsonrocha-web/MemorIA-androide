package com.memoria.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Arms the OS alarms that make the app a reminder rather than a logbook.
 *
 * Alarms are armed as a rolling window instead of one per dose forever: a user
 * with six daily medications would otherwise need thousands of pending intents,
 * which AlarmManager will not hold. The window is re-armed whenever the app
 * opens, whenever a medication changes, when an alarm fires, and after a reboot
 * — so it always runs at least [WINDOW_HOURS] ahead of the user.
 *
 * Doses already recorded are skipped, so answering on the web does not leave the
 * phone buzzing about a dose that is already taken.
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: MemoriaRepository,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    /**
     * Reads the current medications and re-arms the window. Safe to call often;
     * identical alarms replace each other because [DoseAlarm.requestCode] is
     * derived from medication + slot.
     */
    suspend fun reschedule() {
        if (!repository.isLoggedIn()) {
            cancelAll()
            return
        }
        val medications = (repository.medications() as? ApiResult.Ok)?.value ?: return
        val history = (repository.history(limit = 200) as? ApiResult.Ok)?.value.orEmpty()
        scheduleFrom(medications.filter { it.active }, history, repository.snoozeMinutes())
    }

    /** Same as [reschedule] but from data the caller already has in hand. */
    fun scheduleFrom(
        medications: List<Medication>,
        history: List<HistoryEntry>,
        snooze: Int = DEFAULT_SNOOZE_MINUTES,
    ) {
        val manager = alarmManager ?: return
        val now = LocalDateTime.now()
        val horizon = now.plusHours(WINDOW_HOURS)

        cancelTracked()

        val alarms = mutableListOf<DoseAlarm>()
        var date = now.toLocalDate()
        while (date <= horizon.toLocalDate() && alarms.size < MAX_ALARMS) {
            val slots = Schedule.slotsFor(medications, date)
            val answered = Schedule.withHistory(slots, history, date)
            for (slot in answered) {
                if (slot.doneStatus != null) continue
                val at = LocalDateTime.of(date, parseTime(slot.time))
                if (at.isBefore(now) || at.isAfter(horizon)) continue
                val id = slot.medication.id ?: continue
                alarms += DoseAlarm(
                    medicationId = id,
                    medicationName = slot.medication.name,
                    dosage = slot.medication.dosage,
                    instructions = slot.medication.instructions,
                    time = slot.time,
                    date = date.toString(),
                    snoozeMinutes = snooze,
                )
                if (alarms.size >= MAX_ALARMS) break
            }
            date = date.plusDays(1)
        }

        alarms.forEach { arm(manager, it) }
        remember(alarms)
        Log.i(TAG, "Armados ${alarms.size} lembretes até $horizon")
    }

    /** Re-arms a single dose a few minutes out, for the "Adiar" action. */
    fun snooze(dose: DoseAlarm) {
        val manager = alarmManager ?: return
        val at = System.currentTimeMillis() + dose.snoozeMinutes * 60_000L
        val pending = PendingIntent.getBroadcast(
            context,
            dose.requestCode,
            ReminderReceiver.intentFor(context, ReminderReceiver.ACTION_FIRE, dose),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(manager, at, pending)
    }

    fun cancel(dose: DoseAlarm) {
        val manager = alarmManager ?: return
        manager.cancel(
            PendingIntent.getBroadcast(
                context,
                dose.requestCode,
                ReminderReceiver.intentFor(context, ReminderReceiver.ACTION_FIRE, dose),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
    }

    fun cancelAll() {
        cancelTracked()
        prefs().edit().remove(KEY_ARMED).apply()
    }

    /**
     * Whether exact alarms are actually available. On Android 12/13 the user can
     * revoke them; the app still schedules, just inexactly, and Settings uses
     * this to explain why a reminder may arrive late.
     */
    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun arm(manager: AlarmManager, dose: DoseAlarm) {
        val pending = PendingIntent.getBroadcast(
            context,
            dose.requestCode,
            ReminderReceiver.intentFor(context, ReminderReceiver.ACTION_FIRE, dose),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(manager, dose.triggerAtMillis, pending)
    }

    private fun setExact(manager: AlarmManager, atMillis: Long, pending: PendingIntent) {
        // setExactAndAllowWhileIdle is the only variant that survives Doze, which
        // is exactly when an overnight dose would otherwise be swallowed.
        // A revoked exact-alarm permission throws, so it falls back rather than
        // crashing the app the user depends on.
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de alarme exato; a usar alarme aproximado", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    /**
     * The armed set is persisted because cancelling a PendingIntent requires
     * rebuilding it with the same request code — and after a process restart the
     * scheduler no longer knows what it armed. Without this, editing a
     * medication left the old alarms firing forever.
     */
    private fun remember(alarms: List<DoseAlarm>) {
        val encoded = alarms.joinToString("\n") {
            listOf(it.medicationId, it.medicationName, it.dosage, it.time, it.date).joinToString(FIELD_SEPARATOR)
        }
        prefs().edit().putString(KEY_ARMED, encoded).apply()
    }

    private fun cancelTracked() {
        val manager = alarmManager ?: return
        val encoded = prefs().getString(KEY_ARMED, null) ?: return
        encoded.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 5) return@forEach
            val dose = DoseAlarm(
                medicationId = parts[0],
                medicationName = parts[1],
                dosage = parts[2],
                instructions = null,
                time = parts[3],
                date = parts[4],
                snoozeMinutes = DEFAULT_SNOOZE_MINUTES,
            )
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    dose.requestCode,
                    ReminderReceiver.intentFor(context, ReminderReceiver.ACTION_FIRE, dose),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parseTime(raw: String): LocalTime =
        runCatching { LocalTime.parse(Schedule.normalizeTime(raw)) }.getOrDefault(LocalTime.MIDNIGHT)

    companion object {
        private const val TAG = "MemoriaReminders"
        private const val PREFS = "memoria_reminders"
        private const val KEY_ARMED = "armed_alarms"

        /** Unit separator: cannot occur in a name, dosage, time or date. */
        private val FIELD_SEPARATOR = Char(0x1F).toString()

        /** How far ahead alarms are armed before the window is refreshed. */
        const val WINDOW_HOURS = 48L

        /** Ceiling so a large regimen cannot exhaust the system alarm table. */
        const val MAX_ALARMS = 60

        const val DEFAULT_SNOOZE_MINUTES = 10

        val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun nowIso(): String =
            LocalDateTime.now().atZone(ZoneId.systemDefault()).toOffsetDateTime().format(ISO_INSTANT)

        fun today(): LocalDate = LocalDate.now()
    }
}
