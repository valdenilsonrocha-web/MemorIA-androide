package com.memoria.mobile.reminders

import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * One reminder for one scheduled consultation, flattened into what an alarm can
 * carry across a process restart — same contract as [DoseAlarm].
 *
 * Consultations live only on the phone (see
 * [com.memoria.mobile.data.local.MedicalConsultation]), so nothing here needs the
 * network at fire time.
 */
data class ConsultationAlarm(
    val consultationId: String,
    val professional: String,
    val location: String,
    /** Local date-time of the appointment itself, `yyyy-MM-dd'T'HH:mm`. */
    val dateTime: String,
    /** Which of the [Lead] reminders this alarm is. */
    val lead: Lead,
) {
    /**
     * How far ahead of the appointment the reminder fires.
     *
     * The web app notifies AT the appointment time, which is already too late to
     * leave the house. The day-before reminder is the one that actually lets
     * someone arrange a ride; the hour-before is the nudge to get ready. Both are
     * armed, so a consultation entered today for tomorrow still gets warned about.
     */
    enum class Lead(val hoursBefore: Long, val phrase: String) {
        DAY_BEFORE(24, "amanhã"),
        HOUR_BEFORE(1, "em cerca de 1 hora"),

        /** What the web does (`checkConsultationSchedules()`): at the appointment minute. */
        AT_TIME(0, "agora"),
    }

    private val appointment: LocalDateTime?
        get() = runCatching { LocalDateTime.parse(dateTime) }.getOrNull()

    /** Null when the stored date-time no longer parses, so the caller can skip it. */
    val firesAt: LocalDateTime?
        get() = appointment?.minusHours(lead.hoursBefore)

    val triggerAtMillis: Long?
        get() = firesAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    /** Stable per consultation + lead, so re-arming replaces instead of stacking. */
    val requestCode: Int
        get() = "consulta|$consultationId|${lead.name}".hashCode().absoluteValue

    /** "Amanhã, 14/03 às 09:30" / "Em cerca de 1 hora, hoje às 09:30". */
    val whenLabel: String
        get() {
            val at = appointment ?: return dateTime
            val stamp = at.format(DISPLAY)
            return "${lead.phrase.replaceFirstChar { it.titlecase(Locale.getDefault()) }} · $stamp"
        }

    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_ID, consultationId)
        putExtra(EXTRA_PROFESSIONAL, professional)
        putExtra(EXTRA_LOCATION, location)
        putExtra(EXTRA_DATETIME, dateTime)
        putExtra(EXTRA_LEAD, lead.name)
    }

    companion object {
        private const val EXTRA_ID = "consultation_id"
        private const val EXTRA_PROFESSIONAL = "consultation_professional"
        private const val EXTRA_LOCATION = "consultation_location"
        private const val EXTRA_DATETIME = "consultation_datetime"
        private const val EXTRA_LEAD = "consultation_lead"

        private val DISPLAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale.getDefault())

        fun readFrom(intent: Intent): ConsultationAlarm? {
            val id = intent.getStringExtra(EXTRA_ID) ?: return null
            val dateTime = intent.getStringExtra(EXTRA_DATETIME) ?: return null
            val lead = intent.getStringExtra(EXTRA_LEAD)
                ?.let { name -> Lead.entries.firstOrNull { it.name == name } }
                ?: Lead.HOUR_BEFORE
            return ConsultationAlarm(
                consultationId = id,
                professional = intent.getStringExtra(EXTRA_PROFESSIONAL).orEmpty(),
                location = intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
                dateTime = dateTime,
                lead = lead,
            )
        }
    }
}
