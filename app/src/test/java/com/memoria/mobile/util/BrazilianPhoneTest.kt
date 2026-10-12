package com.memoria.mobile.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BrazilianPhoneTest {

    @Test
    fun elevenDigitMobileGetsCountryCode() {
        assertEquals("5511987654321", BrazilianPhone.toWhatsAppId("11987654321"))
    }

    @Test
    fun tenDigitLandlineGetsCountryCode() {
        assertEquals("551133334444", BrazilianPhone.toWhatsAppId("1133334444"))
    }

    @Test
    fun ddd55IsNotMistakenForCountryCode() {
        // RS (DDD 55) stored as DDD+number: 11 digits starting with 55.
        assertEquals("5555991234567", BrazilianPhone.toWhatsAppId("55991234567"))
        assertEquals("https://wa.me/5555991234567", BrazilianPhone.waMeUrl("(55) 99123-4567"))
    }

    @Test
    fun alreadyInternationalStaysUntouched() {
        assertEquals("5511987654321", BrazilianPhone.toWhatsAppId("5511987654321"))
        assertEquals("5555991234567", BrazilianPhone.toWhatsAppId("5555991234567"))
    }

    @Test
    fun leadingZeroIsStripped() {
        assertEquals("5511987654321", BrazilianPhone.toWhatsAppId("011987654321"))
    }

    @Test
    fun shortOrEmptyIsNotInvented() {
        assertEquals("", BrazilianPhone.toWhatsAppId(""))
        assertEquals("123", BrazilianPhone.toWhatsAppId("123"))
    }
}
