package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One scheduled dose, derived from a medication and one of its times. */
data class DoseSlot(
    val medication: Medication,
    val time: String, // "HH:mm"
    val doneStatus: String? = null, // taken | missed | snoozed, once known
)

/**
 * Turns medications into the doses due on a given day, mirroring `app.js`.
 *
 * Shared by the dashboard, the medication list, the calendar and the
 * replenishment forecast — they disagreed about what "today" means when each
 * carried its own copy.
 */
object Schedule {

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    /** Does [med] have doses on [date]? */
    fun appliesOn(med: Medication, date: LocalDate): Boolean = when (med.frequency) {
        // The backend stores week days Sunday-first (0..6); java.time is Mon=1..Sun=7.
        "weekly" -> med.weekDays?.contains(date.dayOfWeek.value % 7) == true
        "alternate" -> alternateAppliesOn(med, date)
        else -> true // daily
    }

    /** Every dose due on [date], earliest first. */
    fun slotsFor(meds: List<Medication>, date: LocalDate): List<DoseSlot> =
        meds.filter { appliesOn(it, date) }
            .flatMap { med ->
                med.times.filter { it.isNotBlank() }
                    .map { DoseSlot(medication = med, time = normalizeTime(it)) }
            }
            .sortedBy { it.time }

    /**
     * Marks each slot with the status already recorded for it, so a dose logged
     * on another device (or in the web app) shows as done here too.
     */
    fun withHistory(slots: List<DoseSlot>, history: List<HistoryEntry>, date: LocalDate): List<DoseSlot> {
        if (history.isEmpty()) return slots
        val onDate = history.filter { localDateOf(it.scheduledFor ?: it.createdAt) == date }
        if (onDate.isEmpty()) return slots
        return slots.map { slot ->
            val match = onDate.firstOrNull {
                it.medicationId == slot.medication.id && normalizeTime(it.scheduleTime) == slot.time
            }
            if (match != null) slot.copy(doneStatus = match.status) else slot
        }
    }

    /** Doses consumed per day on average — drives the replenishment forecast. */
    fun estimatedDailyDoses(med: Medication): Double {
        val eventsPerDay = med.times.count { it.isNotBlank() }.coerceAtLeast(1).toDouble()
        return when (med.frequency) {
            "alternate" -> eventsPerDay / 2.0
            "weekly" -> {
                val days = med.weekDays?.size?.takeIf { it > 0 } ?: 1
                eventsPerDay * days / 7.0
            }
            else -> eventsPerDay
        }
    }

    fun normalizeTime(t: String): String = runCatching {
        LocalTime.parse(t.trim()).format(hhmm)
    }.getOrDefault(t.trim())

    /** ISO-8601 instant for [time] on [date], in the phone's zone. */
    fun isoFor(date: LocalDate, time: String): String {
        val t = runCatching { LocalTime.parse(normalizeTime(time)) }.getOrDefault(LocalTime.MIDNIGHT)
        return date.atTime(t).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /** Local date of an ISO-8601 instant, or null when it is absent/unparseable. */
    fun localDateOf(iso: String?): LocalDate? = iso?.let {
        runCatching {
            OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
    }

    // Alternate-day meds: parity of days since the medication was created.
    private fun alternateAppliesOn(med: Medication, date: LocalDate): Boolean {
        val created = localDateOf(med.createdAt) ?: return true
        return ChronoUnit.DAYS.between(created, date) % 2 == 0L
    }
}
