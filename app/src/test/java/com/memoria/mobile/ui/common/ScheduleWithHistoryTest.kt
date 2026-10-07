package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * A dose the user confirmed must leave "Próximos Horários". The history can hold
 * an earlier "missed" (or old "snoozed") entry for the same dose with the same
 * scheduled time, and the server's order between them is not guaranteed.
 */
class ScheduleWithHistoryTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val med = Medication(id = "m1", name = "neosaldina", times = listOf("10:00", "17:00"))
    private val slots = listOf(DoseSlot(med, "10:00"), DoseSlot(med, "17:00"))

    private fun entry(status: String, time: String = "10:00") = HistoryEntry(
        medicationId = "m1",
        scheduleTime = time,
        status = status,
        scheduledFor = Schedule.isoFor(today, time),
    )

    @Test
    fun takenWinsOverAnEarlierMissedEntryForTheSameDose() {
        for (history in listOf(listOf(entry("missed"), entry("taken")), listOf(entry("taken"), entry("missed")))) {
            val result = Schedule.withHistory(slots, history, today)
            assertEquals("taken", result[0].doneStatus)
            assertNull(result[1].doneStatus)
        }
    }

    @Test
    fun missedAloneStaysMissed() {
        val result = Schedule.withHistory(slots, listOf(entry("missed")), today)
        assertEquals("missed", result[0].doneStatus)
    }

    @Test
    fun timeWithSecondsStillMatches() {
        val result = Schedule.withHistory(slots, listOf(entry("taken", "17:00:00")), today)
        assertEquals("taken", result[1].doneStatus)
    }
}
