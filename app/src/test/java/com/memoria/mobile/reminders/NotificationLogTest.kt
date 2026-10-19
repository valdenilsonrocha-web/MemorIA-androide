package com.memoria.mobile.reminders

import com.memoria.mobile.data.remote.DeviceReportRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogTest {

    private val planned = 1_760_000_000_000L

    @Test
    fun `an alarm that rang 47 minutes late is recorded with its delay`() {
        val entry = NotificationLog.entry("dose", "Losartana 08:00", planned, shown = true, nowMillis = planned + 47 * 60_000L)
        assertEquals("shown", entry.outcome)
        assertEquals(47, entry.delayMinutes)
        assertEquals("2025-10-09T08:53:20Z", entry.scheduledFor)
    }

    @Test
    fun `a reminder Android refused is recorded as blocked, never as shown`() {
        val entry = NotificationLog.entry("dose", "Losartana 08:00", planned, shown = false, nowMillis = planned)
        assertEquals("blocked", entry.outcome)
        assertEquals(0, entry.delayMinutes)
    }

    @Test
    fun `a stock warning has no planned time and therefore no delay`() {
        val entry = NotificationLog.entry("stock", "Losartana", null, shown = true, nowMillis = planned)
        assertNull(entry.scheduledFor)
        assertNull(entry.delayMinutes)
    }

    @Test
    fun `the log keeps the newest entries first and stops growing`() {
        var log = emptyList<com.memoria.mobile.data.remote.DeviceReportEvent>()
        repeat(NotificationLog.MAX_ENTRIES + 5) { i ->
            log = NotificationLog.append(log, NotificationLog.entry("dose", "Dose $i", null, true, planned + i * 1000L))
        }
        assertEquals(NotificationLog.MAX_ENTRIES, log.size)
        assertEquals("Dose ${NotificationLog.MAX_ENTRIES + 4}", log.first().label)
    }

    @Test
    fun `the report is resent when a permission flips or a new reminder is logged, and at least every six hours`() {
        val base = DeviceReportRequest(deviceId = "install-1", notificationsEnabled = true, exactAlarmAllowed = true)
        val sent = DeviceReporter.signature(base)
        val now = planned
        assertFalse(DeviceReporter.isDue(sent, sent, now - 60_000L, now))
        assertTrue(DeviceReporter.isDue(sent, DeviceReporter.signature(base.copy(notificationsEnabled = false)), now - 60_000L, now))
        val withEvent = base.copy(events = listOf(NotificationLog.entry("dose", "Dose", null, true, now)))
        assertTrue(DeviceReporter.isDue(sent, DeviceReporter.signature(withEvent), now - 60_000L, now))
        assertTrue(DeviceReporter.isDue(sent, sent, now - 6 * 60 * 60 * 1000L, now))
    }
}
