package com.memoria.mobile.reminders

import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

/**
 * One scheduled dose, flattened to what an alarm can carry across a process
 * restart. Everything the notification and the history entry need travels in
 * the Intent, so the receiver never has to hit the network before it can show
 * the reminder — a dose alert that waits on a slow connection is a late alert.
 */
data class DoseAlarm(
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val instructions: String?,
    /** "HH:mm" of the scheduled dose. */
    val time: String,
    /** Local date the dose belongs to, ISO `yyyy-MM-dd`. */
    val date: String,
    val snoozeMinutes: Int,
) {
    /**
     * Stable per medication+slot so re-arming the window replaces alarms instead
     * of piling duplicates up, and so the notification for a dose is replaced
     * rather than stacked when it is snoozed.
     */
    val requestCode: Int
        get() = "$medicationId|$date|$time".hashCode().absoluteValue

    val triggerAtMillis: Long
        get() = LocalDateTime.of(localDate, localTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** ISO-8601 instant for the history entry's `scheduledFor`. */
    val scheduledForIso: String
        get() = LocalDateTime.of(localDate, localTime)
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private val localDate: LocalDate
        get() = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())

    private val localTime: LocalTime
        get() = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.MIDNIGHT)

    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_MED_ID, medicationId)
        putExtra(EXTRA_MED_NAME, medicationName)
        putExtra(EXTRA_DOSAGE, dosage)
        putExtra(EXTRA_INSTRUCTIONS, instructions)
        putExtra(EXTRA_TIME, time)
        putExtra(EXTRA_DATE, date)
        putExtra(EXTRA_SNOOZE, snoozeMinutes)
    }

    companion object {
        private const val EXTRA_MED_ID = "med_id"
        private const val EXTRA_MED_NAME = "med_name"
        private const val EXTRA_DOSAGE = "dosage"
        private const val EXTRA_INSTRUCTIONS = "instructions"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_SNOOZE = "snooze"

        /** Null when the Intent is missing the parts a reminder cannot do without. */
        fun readFrom(intent: Intent): DoseAlarm? {
            val id = intent.getStringExtra(EXTRA_MED_ID) ?: return null
            val time = intent.getStringExtra(EXTRA_TIME) ?: return null
            val date = intent.getStringExtra(EXTRA_DATE) ?: return null
            return DoseAlarm(
                medicationId = id,
                medicationName = intent.getStringExtra(EXTRA_MED_NAME).orEmpty(),
                dosage = intent.getStringExtra(EXTRA_DOSAGE).orEmpty(),
                instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS),
                time = time,
                date = date,
                snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
            )
        }
    }
}
