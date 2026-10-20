package com.memoria.mobile.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPhoneTest {

    @Test
    fun `the doctor WhatsApp goes to the server with the country code`() {
        assertEquals("5585999998888", SettingsViewModel.reportPhoneForServer("(85) 99999-8888"))
        assertEquals("5585999998888", SettingsViewModel.reportPhoneForServer("5585999998888"))
        assertEquals("351912345678", SettingsViewModel.reportPhoneForServer("+351 912 345 678"))
    }

    @Test
    fun `an empty field clears the number and a short one is refused`() {
        assertEquals("", SettingsViewModel.reportPhoneForServer(""))
        assertEquals("", SettingsViewModel.reportPhoneForServer("99999-8888"))
    }
}
