package com.memoria.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.memoria.mobile.MemoriaApp
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.remote.HistoryRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Shows the dose reminder when its alarm fires, and records the answer when the
 * user taps one of the notification's buttons.
 *
 * The notification is posted BEFORE anything touches the network. A reminder
 * that waits on a request would be late — or never arrive at all on a phone
 * with no signal — and being late is the one failure this app cannot have.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MemoriaApp ?: return

        // Consultation and stock alarms carry no DoseAlarm, so they are handled
        // before the dose extras are demanded.
        when (intent.action) {
            ACTION_CONSULTATION -> {
                ConsultationAlarm.readFrom(intent)?.let { fireConsultation(context, it) }
                return
            }
            ACTION_STOCK -> {
                StockAlarm.readFrom(intent)?.let { fireStock(context, it) }
                return
            }
        }

        val dose = DoseAlarm.readFrom(intent) ?: return
        when (intent.action) {
            ACTION_FIRE -> fire(context, app, dose)
            ACTION_TAKEN -> answer(context, app, dose, status = "taken")
            ACTION_MISSED -> answer(context, app, dose, status = "missed")
            ACTION_SNOOZE -> snooze(context, app, dose)
            ACTION_SILENCE -> MemoriaNotifications.silenceDose(context, dose)
        }
    }

    /**
     * Posts the consultation reminder. Nothing is written anywhere: consultations
     * are phone-side records and the reminder is purely informational, so there is
     * no network work to keep the receiver alive for.
     */
    private fun fireConsultation(context: Context, alarm: ConsultationAlarm) {
        MemoriaNotifications.ensureChannels(context)
        if (!MemoriaNotifications.canPost(context)) {
            Log.w(TAG, "Sem permissão de notificação; consulta de ${alarm.professional} não mostrada")
            return
        }
        MemoriaNotifications.post(
            context,
            alarm.requestCode,
            MemoriaNotifications.buildConsultationNotification(
                context = context,
                professional = alarm.professional,
                whenLabel = alarm.whenLabel,
                location = alarm.location,
                requestCode = alarm.requestCode,
                dateTime = alarm.dateTime,
            ),
        )
    }

    private fun fireStock(context: Context, alarm: StockAlarm) {
        MemoriaNotifications.ensureChannels(context)
        if (!MemoriaNotifications.canPost(context)) {
            Log.w(TAG, "Sem permissão de notificação; estoque de ${alarm.medicationName} não avisado")
            return
        }
        MemoriaNotifications.post(
            context,
            alarm.requestCode,
            MemoriaNotifications.buildLowStockNotification(context, alarm),
        )
    }

    private fun fire(context: Context, app: MemoriaApp, dose: DoseAlarm) {
        MemoriaNotifications.ensureChannels(context)
        if (MemoriaNotifications.post(
                context,
                dose.requestCode,
                MemoriaNotifications.buildDoseNotification(context, dose, dose.sound),
            )
        ) {
            scheduleSilence(context, dose)
            // Phone in use: the full-screen intent would only be a small heads-up.
            com.memoria.mobile.ReminderAlertActivity.launchNow(context, dose)
        } else {
            Log.w(TAG, "Sem permissão de notificação; lembrete de ${dose.medicationName} não mostrado")
        }
        // Firing consumes one alarm from the window, so top the window back up.
        background(this, app) { app.graph.reminderScheduler.reschedule() }
    }

    /**
     * The web rings for one minute (`medicationAlarmDurationMs`), then stops.
     * The notification itself is insistent, so an alarm at +60 s swaps it for a
     * silent copy if nobody has answered by then.
     */
    private fun scheduleSilence(context: Context, dose: DoseAlarm) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + ALARM_DURATION_MS
        val pending = silencePendingIntent(context, dose)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /**
     * Ends everything still ringing for this dose: the pending one-minute
     * cut-off, and the full-screen alert if it is open behind the notification
     * the user just answered (it plays its own looping sound).
     */
    private fun stopRinging(context: Context, dose: DoseAlarm) {
        context.getSystemService(AlarmManager::class.java)?.cancel(silencePendingIntent(context, dose))
        context.sendBroadcast(
            Intent(ACTION_DOSE_ANSWERED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_REQUEST_CODE, dose.requestCode),
        )
    }

    private fun silencePendingIntent(context: Context, dose: DoseAlarm): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            dose.requestCode,
            intentFor(context, ACTION_SILENCE, dose),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun answer(context: Context, app: MemoriaApp, dose: DoseAlarm, status: String) {
        NotificationManagerCompat.from(context).cancel(dose.requestCode)
        stopRinging(context, dose)
        val request = HistoryRequest(
            medicationId = dose.medicationId,
            medicationName = dose.medicationName,
            dosage = dose.dosage,
            scheduleTime = dose.time,
            status = status,
            scheduledFor = dose.scheduledForIso,
            takenAt = if (status == "taken") {
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } else {
                null
            },
        )
        background(this, app) {
            val repo = app.graph.repository
            // "Tomei" also takes one unit off the stock, like the web's markAsTaken().
            val result = if (status == "taken") {
                DoseActions.markTaken(
                    repo = repo,
                    medicationId = dose.medicationId,
                    medicationName = dose.medicationName,
                    dosage = dose.dosage,
                    time = dose.time,
                    scheduledFor = dose.scheduledForIso,
                )
            } else {
                repo.addHistory(request)
            }
            when (val r = result) {
                is ApiResult.Ok -> app.graph.reminderScheduler.reschedule()
                // Offline is the common case here. The dose is not lost — the
                // alarm stays cancelled but the window rebuilds from history on
                // the next open, so an unrecorded dose reappears as pending.
                is ApiResult.Err -> Log.w(TAG, "Dose não registrada: ${r.message}")
            }
            // Open lists (Início, Atualizações, the badge) reload and drop this dose.
            DoseEvents.notifyChanged()
        }
    }

    private fun snooze(context: Context, app: MemoriaApp, dose: DoseAlarm) {
        NotificationManagerCompat.from(context).cancel(dose.requestCode)
        stopRinging(context, dose)
        app.graph.reminderScheduler.snooze(dose)
        Log.i(TAG, "Dose de ${dose.medicationName} adiada ${dose.snoozeMinutes} min")
    }

    /**
     * Keeps the receiver alive while the coroutine runs. A BroadcastReceiver is
     * killable the instant [onReceive] returns, so without goAsync the network
     * call would be torn down mid-flight.
     *
     * An alarm usually wakes a fresh process in which nothing has loaded the
     * session yet. Without `bootstrap()` the scheduler sees no token and cancels
     * every pending alarm, and the dose answer goes out unauthenticated.
     */
    private fun background(receiver: BroadcastReceiver, app: MemoriaApp, block: suspend () -> Unit) {
        val pending = receiver.goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // The OS gives a receiver ~10s; bail out before it kills us.
                withTimeoutOrNull(WORK_TIMEOUT_MS) {
                    app.graph.repository.bootstrap()
                    block()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao processar o lembrete", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MemoriaReminders"
        private const val WORK_TIMEOUT_MS = 8_000L

        const val ACTION_FIRE = "com.memoria.mobile.REMINDER_FIRE"
        const val ACTION_TAKEN = "com.memoria.mobile.REMINDER_TAKEN"
        const val ACTION_MISSED = "com.memoria.mobile.REMINDER_MISSED"
        const val ACTION_SNOOZE = "com.memoria.mobile.REMINDER_SNOOZE"
        const val ACTION_CONSULTATION = "com.memoria.mobile.CONSULTATION_FIRE"
        const val ACTION_STOCK = "com.memoria.mobile.STOCK_FIRE"
        const val ACTION_SILENCE = "com.memoria.mobile.REMINDER_SILENCE"

        /** Sent (package-local) when a dose is answered, so an open full-screen alert closes. */
        const val ACTION_DOSE_ANSWERED = "com.memoria.mobile.DOSE_ANSWERED"
        const val EXTRA_REQUEST_CODE = "com.memoria.mobile.extra.REQUEST_CODE"

        /** `medicationAlarmDurationMs` on the web: the alarm rings for one minute. */
        const val ALARM_DURATION_MS = 60_000L

        fun intentFor(context: Context, action: String, dose: DoseAlarm): Intent =
            dose.writeTo(Intent(context, ReminderReceiver::class.java).setAction(action))

        fun consultationIntent(context: Context, alarm: ConsultationAlarm): Intent =
            alarm.writeTo(
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_CONSULTATION)
            )

        fun stockIntent(context: Context, alarm: StockAlarm): Intent =
            alarm.writeTo(
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_STOCK)
            )
    }
}
