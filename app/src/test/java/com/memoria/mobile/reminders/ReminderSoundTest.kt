package com.memoria.mobile.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Each sound profile MUST own a distinct channel id: Android freezes a channel's
 * sound and importance at creation, so two profiles sharing an id would make the
 * setting silently do nothing — the user would pick "Alerta" and keep hearing the
 * quiet tone, with no error anywhere to explain it.
 */
class ReminderSoundTest {

    @Test
    fun `every profile has its own channel`() {
        val ids = ReminderSound.entries.map { it.channelId }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `an unknown or missing id falls back to the web default`() {
        // Stale preferences (an id written by another build) must not crash a screen.
        // The web's default is "system" (Padrão do sistema).
        assertEquals(ReminderSound.SYSTEM, ReminderSound.from(null))
        assertEquals(ReminderSound.SYSTEM, ReminderSound.from(""))
        assertEquals(ReminderSound.SYSTEM, ReminderSound.from("ultra-alto"))
    }

    @Test
    fun `ids round trip`() {
        ReminderSound.entries.forEach { assertEquals(it, ReminderSound.from(it.id)) }
    }

    @Test
    fun `silent is the only option without vibration`() {
        assertEquals(null, ReminderSound.SILENT.vibrationPattern)
        ReminderSound.entries.filter { it != ReminderSound.SILENT }
            .forEach { assertNotEquals(null, it.vibrationPattern) }
    }

    @Test
    fun `the settings list is the web nine options in order`() {
        assertEquals(
            listOf("system", "chime", "bell", "ping", "ding", "alert", "gentle", "vibrate", "silent"),
            ReminderSound.webOptions.map { it.id },
        )
    }
}
