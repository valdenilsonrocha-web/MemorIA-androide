package com.memoria.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.MedicalConsultation
import com.memoria.mobile.data.local.MedicationExtras
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlin.math.floor

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
        // Consultations live only on the phone, so they are re-armed even when the
        // medication request fails — an offline moment must not silently drop the
        // reminder for tomorrow's appointment.
        scheduleConsultations(repository.local.consultations())

        // The last list the server gave is kept on the phone, so a reboot with no
        // signal (BootReceiver) still re-arms every dose instead of none.
        val medications = (repository.medications() as? ApiResult.Ok)?.value
            ?.also { cache(KEY_CACHED_MEDS, medsAdapter, it) }
            ?: cached(KEY_CACHED_MEDS, medsAdapter)
            ?: return
        // Sends the doses answered offline, then treats any still queued as taken
        // so their alarms are not re-armed.
        DoseActions.flushPending(repository)
        val history = ((repository.history(limit = 200) as? ApiResult.Ok)?.value
            ?.also { cache(KEY_CACHED_HISTORY, historyAdapter, it) }
            ?: cached(KEY_CACHED_HISTORY, historyAdapter).orEmpty()) +
            DoseActions.pendingHistory(repository)
        val active = medications.filter { it.active }
        val extras = repository.local.medicationExtras()
        scheduleFrom(active, history, repository.snoozeMinutes(), repository.reminderSound(), extras)
        scheduleStockAlerts(
            medications = active,
            enabled = repository.lowStockAlertsEnabled(),
            extras = extras,
        )
    }

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val medsAdapter: JsonAdapter<List<Medication>> by lazy {
        moshi.adapter(Types.newParameterizedType(List::class.java, Medication::class.java))
    }
    private val historyAdapter: JsonAdapter<List<HistoryEntry>> by lazy {
        moshi.adapter(Types.newParameterizedType(List::class.java, HistoryEntry::class.java))
    }

    private fun <T> cache(key: String, adapter: JsonAdapter<List<T>>, items: List<T>) {
        runCatching { prefs().edit().putString(key, adapter.toJson(items)).apply() }
    }

    private fun <T> cached(key: String, adapter: JsonAdapter<List<T>>): List<T>? =
        prefs().getString(key, null)?.let { raw -> runCatching { adapter.fromJson(raw) }.getOrNull() }

    /**
     * `isMedicationActiveOnDate()` of the web: a medication with a set
     * treatment length stops reminding once it is over (continuous use never
     * does). The start is the creation date, the web's
     * `treatmentStartDate || createdAt`.
     */
    private fun inTreatment(med: Medication, date: LocalDate, extras: Map<String, MedicationExtras>): Boolean {
        val extra = med.id?.let { extras[it] } ?: return true
        if (extra.continuousUse) return true
        val duration = extra.treatmentDurationDays?.takeIf { it > 0 } ?: return true
        val start = Schedule.localDateOf(med.createdAt) ?: return true
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, date)
        return elapsed in 0 until duration
    }

    /** Same as [reschedule] but from data the caller already has in hand. */
    fun scheduleFrom(
        medications: List<Medication>,
        history: List<HistoryEntry>,
        snooze: Int = DEFAULT_SNOOZE_MINUTES,
        soundId: String = ReminderSound.SYSTEM.id,
        extras: List<MedicationExtras> = emptyList(),
    ) {
        val manager = alarmManager ?: return
        val now = LocalDateTime.now()
        val horizon = now.plusHours(WINDOW_HOURS)
        val extrasById = extras.associateBy { it.medicationId }

        cancelTracked()

        val alarms = mutableListOf<DoseAlarm>()
        var date = now.toLocalDate()
        while (date <= horizon.toLocalDate() && alarms.size < MAX_ALARMS) {
            val day = date
            val slots = Schedule.slotsFor(medications.filter { inTreatment(it, day, extrasById) }, date)
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
                    soundId = soundId,
                )
                if (alarms.size >= MAX_ALARMS) break
            }
            date = date.plusDays(1)
        }

        alarms.forEach { arm(manager, it) }
        remember(alarms)
        Log.i(TAG, "Armados ${alarms.size} lembretes até $horizon")
    }

    /**
     * Arms the reminders for every future consultation inside the window.
     *
     * Two per consultation (a day before, an hour before) — see
     * [ConsultationAlarm.Lead] for why "at the appointment time", which is what
     * the web does, is not useful on its own.
     */
    fun scheduleConsultations(consultations: List<MedicalConsultation>) {
        val manager = alarmManager ?: return
        val now = LocalDateTime.now()
        val horizon = now.plusHours(CONSULTATION_WINDOW_HOURS)

        cancelTrackedConsultations()

        val alarms = consultations.asSequence()
            .filter { it.dateTime.isNotBlank() && it.professional.isNotBlank() }
            .flatMap { consultation ->
                ConsultationAlarm.Lead.entries.asSequence().map { lead ->
                    ConsultationAlarm(
                        consultationId = consultation.id,
                        professional = consultation.professional,
                        location = consultation.location,
                        dateTime = consultation.dateTime,
                        lead = lead,
                    )
                }
            }
            .filter { alarm ->
                val at = alarm.firesAt ?: return@filter false
                at.isAfter(now) && !at.isAfter(horizon)
            }
            .take(MAX_CONSULTATION_ALARMS)
            .toList()

        alarms.forEach { alarm ->
            val at = alarm.triggerAtMillis ?: return@forEach
            setExact(
                manager,
                at,
                PendingIntent.getBroadcast(
                    context,
                    alarm.requestCode,
                    ReminderReceiver.consultationIntent(context, alarm),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        rememberConsultations(alarms)
        Log.i(TAG, "Armados ${alarms.size} lembretes de consulta até $horizon")
    }

    /**
     * Arms one "running out" reminder per medication whose stock will not last,
     * at the next [STOCK_ALERT_HOUR].
     *
     * Only one alarm per medication is armed (never a stream of them) so a chronic
     * low stock cannot turn into a notification the user learns to swipe away.
     * The threshold matches the backend's own low-stock rule, so the phone and the
     * caregiver's WhatsApp talk about the same medicines.
     */
    fun scheduleStockAlerts(
        medications: List<Medication>,
        enabled: Boolean,
        extras: List<MedicationExtras> = emptyList(),
    ) {
        val manager = alarmManager ?: return
        cancelTrackedStock()
        if (!enabled) {
            prefs().edit().remove(KEY_ARMED_STOCK).apply()
            return
        }

        val now = LocalDateTime.now()
        val at = now.toLocalDate().atTime(STOCK_ALERT_HOUR, 0).let {
            if (it.isAfter(now)) it else it.plusDays(1)
        }
        val date = at.toLocalDate().toString()

        val alarms = medications.mapNotNull { med ->
            val id = med.id ?: return@mapNotNull null
            val stock = med.stock.coerceAtLeast(0)
            val daily = Schedule.estimatedDailyDoses(med)
            val days = if (daily > 0) floor(stock / daily).toInt() else -1
            // `getMedicationsNeedingAttention()` of the web: fewer than 7 doses left.
            val low = stock < WEB_LOW_STOCK_DOSES
            if (!low) return@mapNotNull null
            StockAlarm(
                medicationId = id,
                medicationName = med.name,
                stock = stock,
                daysRemaining = days,
                // The Premium supplier is what the server actually messages; the
                // locally noted dispensing pharmacy is the fallback so the text is
                // still useful for a free account.
                pharmacy = med.supplier?.name?.takeIf { it.isNotBlank() }
                    ?: extras.firstOrNull { it.medicationId == id }?.dispensingPharmacy.orEmpty(),
                date = date,
            )
        }.take(MAX_STOCK_ALARMS)

        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.forEach { alarm ->
            setExact(
                manager,
                millis,
                PendingIntent.getBroadcast(
                    context,
                    alarm.requestCode,
                    ReminderReceiver.stockIntent(context, alarm),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        rememberStock(alarms)
        Log.i(TAG, "Armados ${alarms.size} avisos de estoque para $at")
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
        cancelTrackedConsultations()
        cancelTrackedStock()
        prefs().edit()
            .remove(KEY_ARMED)
            .remove(KEY_ARMED_CONSULTATIONS)
            .remove(KEY_ARMED_STOCK)
            .remove(KEY_CACHED_MEDS)
            .remove(KEY_CACHED_HISTORY)
            .apply()
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

    private fun rememberConsultations(alarms: List<ConsultationAlarm>) {
        val encoded = alarms.joinToString("\n") {
            listOf(it.consultationId, it.professional, it.location, it.dateTime, it.lead.name)
                .joinToString(FIELD_SEPARATOR)
        }
        prefs().edit().putString(KEY_ARMED_CONSULTATIONS, encoded).apply()
    }

    private fun cancelTrackedConsultations() {
        val manager = alarmManager ?: return
        val encoded = prefs().getString(KEY_ARMED_CONSULTATIONS, null) ?: return
        encoded.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 5) return@forEach
            val alarm = ConsultationAlarm(
                consultationId = parts[0],
                professional = parts[1],
                location = parts[2],
                dateTime = parts[3],
                lead = ConsultationAlarm.Lead.entries.firstOrNull { it.name == parts[4] }
                    ?: return@forEach,
            )
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    alarm.requestCode,
                    ReminderReceiver.consultationIntent(context, alarm),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
    }

    private fun rememberStock(alarms: List<StockAlarm>) {
        val encoded = alarms.joinToString("\n") {
            listOf(it.medicationId, it.medicationName, it.stock.toString(), it.date)
                .joinToString(FIELD_SEPARATOR)
        }
        prefs().edit().putString(KEY_ARMED_STOCK, encoded).apply()
    }

    private fun cancelTrackedStock() {
        val manager = alarmManager ?: return
        val encoded = prefs().getString(KEY_ARMED_STOCK, null) ?: return
        encoded.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 4) return@forEach
            val alarm = StockAlarm(
                medicationId = parts[0],
                medicationName = parts[1],
                stock = parts[2].toIntOrNull() ?: 0,
                daysRemaining = -1,
                pharmacy = "",
                date = parts[3],
            )
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    alarm.requestCode,
                    ReminderReceiver.stockIntent(context, alarm),
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
        private const val KEY_ARMED_CONSULTATIONS = "armed_consultation_alarms"
        private const val KEY_ARMED_STOCK = "armed_stock_alarms"
        private const val KEY_CACHED_MEDS = "cached_medications"
        private const val KEY_CACHED_HISTORY = "cached_history"

        /** Unit separator: cannot occur in a name, dosage, time or date. */
        private val FIELD_SEPARATOR = Char(0x1F).toString()

        /** How far ahead alarms are armed before the window is refreshed. */
        const val WINDOW_HOURS = 48L

        /** Ceiling so a large regimen cannot exhaust the system alarm table. */
        const val MAX_ALARMS = 60

        /**
         * Consultations are armed further out than doses because they are entered
         * once, weeks ahead — a 48-hour window would never reach the day-before
         * reminder for an appointment booked next month.
         */
        const val CONSULTATION_WINDOW_HOURS = 24L * 60
        const val MAX_CONSULTATION_ALARMS = 40

        /** Hour of day the stock warning arrives — late enough not to wake anyone. */
        const val STOCK_ALERT_HOUR = 9
        const val MAX_STOCK_ALARMS = 20

        /**
         * Same thresholds the backend's WhatsApp reminder service uses for a low
         * stock, so the phone and the caregiver's message never disagree about
         * which medicine is running out.
         */
        const val LOW_STOCK_UNITS = 5
        const val LOW_STOCK_DAYS = 3

        /** `reminderService.getMedicationsNeedingAttention()`: `stock < 7`. */
        const val WEB_LOW_STOCK_DOSES = 7

        const val DEFAULT_SNOOZE_MINUTES = 10

        val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun nowIso(): String =
            LocalDateTime.now().atZone(ZoneId.systemDefault()).toOffsetDateTime().format(ISO_INSTANT)

        fun today(): LocalDate = LocalDate.now()
    }
}
