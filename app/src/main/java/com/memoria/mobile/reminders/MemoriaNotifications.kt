package com.memoria.mobile.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.memoria.mobile.MainActivity
import com.memoria.mobile.R

/**
 * The notification channels and the notifications themselves — doses,
 * consultations and low stock.
 *
 * The dose channels are IMPORTANCE_HIGH with sound and vibration on purpose:
 * this is a medication alert for an elderly user, so it has to break through — a
 * silent entry in the drawer is a missed dose. Android will not let the app
 * change a channel's sound or importance after creation, so every channel id
 * carries a version suffix and the user's sound choice selects between channels
 * ([ReminderSound]); a future change means a new id, not an edit that silently
 * does nothing.
 */
object MemoriaNotifications {

    const val CHANNEL_CONSULTATIONS = "memoria_consultas_v2"
    const val CHANNEL_STOCK = "memoria_estoque_v1"

    private val LEGACY_CHANNELS = listOf(
        "memoria_doses_v1",
        "memoria_doses_suave_v1",
        "memoria_doses_alto_v1",
        "memoria_consultas_v1",
    )

    /** `showNotification()`'s default `vibrate: [400, 200, 400, 200, 400]`. */
    private val WEB_NOTIFICATION_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 400)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val sound = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Channels of earlier builds, whose sound can no longer be changed:
        // removed so Android's settings do not list options that do nothing.
        LEGACY_CHANNELS.forEach { runCatching { manager.deleteNotificationChannel(it) } }

        // One channel per sound option: a channel's sound and importance cannot
        // be changed after creation, so "Som de Notificação" switches channels
        // rather than editing one. All are created up front so the Android
        // notification settings show every option the app can use. Name and
        // description are the web's native channel ("Lembretes de medicamentos").
        ReminderSound.entries.forEach { profile ->
            manager.createNotificationChannel(
                NotificationChannel(
                    profile.channelId,
                    "Lembretes de medicamentos — ${profile.label}",
                    profile.importance,
                ).apply {
                    description = profile.description
                    val pattern = profile.vibrationPattern
                    enableVibration(pattern != null)
                    if (pattern != null) vibrationPattern = pattern
                    setSound(profile.soundUri, profile.audioAttributes)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        // The web posts consultation reminders on the same max-importance,
        // vibrating channel as the doses.
        val consultations = NotificationChannel(
            CHANNEL_CONSULTATIONS,
            "Consultas médicas",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa antes de uma consulta marcada."
            setSound(sound, audio)
            enableVibration(true)
            vibrationPattern = WEB_NOTIFICATION_VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val stock = NotificationChannel(
            CHANNEL_STOCK,
            "Estoque de medicamentos",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisa quando um remédio está a acabar."
            setSound(sound, audio)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannel(consultations)
        manager.createNotificationChannel(stock)
    }

    /** False on Android 13+ until the user grants the runtime permission. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Builds the dose reminder, with the same three answers the app itself
     * offers so the user never has to open it to record a dose.
     */
    fun buildDoseNotification(
        context: Context,
        dose: DoseAlarm,
        sound: ReminderSound = ReminderSound.SYSTEM,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            dose.requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )

        // Same wording as the web reminder: one of the rotating
        // `getMedicationReminderVariants()` texts, then "Nome - Dosagem" and the
        // instructions, as the "Hora do Medicamento!" modal shows them.
        val reminder = ReminderTexts.randomMedication(dose.time)
        val text = "${reminder.title} ${reminder.body}"
        return NotificationCompat.Builder(context, sound.channelId)
            .setSmallIcon(R.drawable.ic_stat_memoria)
            // `getMedicationNotificationTitle()` is "MemorIA" on the web.
            .setContentTitle("MemorIA")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${doseBody(dose)}"))
            // Pre-O phones have no channels, so the profile has to be applied to
            // the notification itself for the setting to mean anything there.
            // The web's native channel is importance 5 — the maximum.
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(sound.soundUri)
            .also { builder ->
                sound.vibrationPattern?.let { builder.setVibrate(it) }
            }
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // The dose stays on screen until answered: auto-dismissing a
            // medication alert after a few seconds defeats the whole point.
            .setOngoing(false)
            .setContentIntent(open)
            // The web modal's two buttons, in its order: "Soneca" then "Tomei".
            .addAction(
                0,
                "Soneca",
                actionIntent(context, ReminderReceiver.ACTION_SNOOZE, dose),
            )
            .addAction(
                0,
                "Tomei",
                actionIntent(context, ReminderReceiver.ACTION_TAKEN, dose),
            )
            .also { builder -> applyFullScreen(context, builder, dose) }
            .build()
    }

    /**
     * `alertDisplayType` (onboarding step "Tipo de alerta"): "fullscreen" — the
     * web's default — opens the "Hora do Medicamento!" modal over the whole
     * screen, lock screen included ([com.memoria.mobile.ReminderAlertActivity]);
     * "normal" leaves the ordinary heads-up notification. Where the system does
     * not allow full-screen intents (Android 14+ without the permission) the
     * notification simply stays a heads-up.
     */
    private fun applyFullScreen(context: Context, builder: NotificationCompat.Builder, dose: DoseAlarm) {
        val type = runCatching {
            com.memoria.mobile.ui.auth.OnboardingPrefs.alertDisplayType(context)
        }.getOrDefault("fullscreen")
        if (type != "fullscreen") return
        val fullScreen = PendingIntent.getActivity(
            context,
            dose.requestCode + FULL_SCREEN_REQUEST_OFFSET,
            com.memoria.mobile.ReminderAlertActivity.intentFor(context, dose),
            pendingFlags(),
        )
        builder.setFullScreenIntent(fullScreen, true)
    }

    private const val FULL_SCREEN_REQUEST_OFFSET = 7_919

    fun buildConsultationNotification(
        context: Context,
        professional: String,
        whenLabel: String,
        location: String,
        requestCode: Int,
        /** Appointment `yyyy-MM-dd'T'HH:mm`, for the web's reminder wording. */
        dateTime: String = "",
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )
        // `getRandomConsultationReminderMessage()` — the web's rotating texts.
        val reminder = ReminderTexts.randomConsultation(professional, dateTime)
        val text = "${reminder.title} ${reminder.body}"
        val body = listOfNotNull(
            text,
            whenLabel.takeIf { it.isNotBlank() },
            location.takeIf { it.isNotBlank() }?.let { "Local: $it" },
        ).joinToString("\n")
        return NotificationCompat.Builder(context, CHANNEL_CONSULTATIONS)
            .setSmallIcon(R.drawable.ic_stat_memoria)
            .setContentTitle("MemorIA")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(WEB_NOTIFICATION_VIBRATION)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    /**
     * "This medicine is running out" — the phone-side half of the stock alert.
     *
     * The server already messages the caregiver (and the preferred pharmacy) over
     * WhatsApp when the stock goes critical; this is what the patient themselves
     * sees, so buying more does not depend on someone else reading a message.
     */
    fun buildLowStockNotification(context: Context, alarm: StockAlarm): Notification {
        val open = PendingIntent.getActivity(
            context,
            alarm.requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )
        // The web's `getRandomReplenishmentReminderMessage()` texts.
        val message = ReminderTexts.randomReplenishment(alarm.medicationName, alarm.stock)
        val body = listOfNotNull(
            message.body,
            alarm.pharmacy.takeIf { it.isNotBlank() }?.let { "Farmácia: $it" },
        ).joinToString("\n")
        return NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_stat_memoria)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    /** `.modal-medication-dosage` ("Nome - Dosagem") plus the instructions. */
    private fun doseBody(dose: DoseAlarm): String = listOfNotNull(
        "${dose.medicationName} - ${dose.dosage}",
        dose.instructions?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

    private fun actionIntent(context: Context, action: String, dose: DoseAlarm): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Distinct request codes per action, otherwise the three buttons
            // would collapse onto one PendingIntent and all do the same thing.
            dose.requestCode + action.hashCode(),
            ReminderReceiver.intentFor(context, action, dose),
            pendingFlags(),
        )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
