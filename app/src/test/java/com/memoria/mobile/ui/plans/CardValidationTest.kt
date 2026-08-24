package com.memoria.mobile.ui.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Guards on the native card form — the only checks before a real charge. */
class CardValidationTest {

    @Test
    fun `accepts the Mercado Pago test cards`() {
        assertTrue(CardValidation.isCardNumberPlausible("5031433215406351"))
        assertTrue(CardValidation.isCardNumberPlausible("5031 4332 1540 6351"))
        assertTrue(CardValidation.isCardNumberPlausible("4235647728025682"))
    }

    /** Luhn exists to catch exactly this: one digit typed wrong. */
    @Test
    fun `rejects a single mistyped digit`() {
        assertFalse(CardValidation.isCardNumberPlausible("5031433215406352"))
        assertFalse(CardValidation.isCardNumberPlausible("123"))
        assertFalse(CardValidation.isCardNumberPlausible(""))
    }

    @Test
    fun `expiry accepts two and four digit years`() {
        val future = YearMonth.now().plusYears(2)
        val short = "%02d%02d".format(future.monthValue, future.year % 100)
        val long = "%02d%d".format(future.monthValue, future.year)
        assertEquals(future.monthValue to future.year, CardValidation.parseExpiry(short))
        assertEquals(future.monthValue to future.year, CardValidation.parseExpiry(long))
    }

    @Test
    fun `expiry rejects past dates and impossible months`() {
        val past = YearMonth.now().minusMonths(1)
        assertNull(CardValidation.parseExpiry("%02d%02d".format(past.monthValue, past.year % 100)))
        assertNull(CardValidation.parseExpiry("1330"))
        assertNull(CardValidation.parseExpiry("0030"))
        assertNull(CardValidation.parseExpiry("11"))
    }

    /** A card is valid through the LAST day of its expiry month. */
    @Test
    fun `expiry accepts the current month`() {
        val now = YearMonth.now()
        assertEquals(
            now.monthValue to now.year,
            CardValidation.parseExpiry("%02d%02d".format(now.monthValue, now.year % 100)),
        )
    }

    @Test
    fun `cpf checks the verifier digits`() {
        assertTrue(CardValidation.isCpfValid("12345678909"))
        assertTrue(CardValidation.isCpfValid("123.456.789-09"))
        // Right length, wrong check digit.
        assertFalse(CardValidation.isCpfValid("12345678900"))
        // Repeated digits pass the arithmetic but are never real documents.
        assertFalse(CardValidation.isCpfValid("11111111111"))
        assertFalse(CardValidation.isCpfValid("1234567890"))
    }

    @Test
    fun `security code is three or four digits`() {
        assertTrue(CardValidation.isSecurityCodePlausible("123"))
        assertTrue(CardValidation.isSecurityCodePlausible("1234"))
        assertFalse(CardValidation.isSecurityCodePlausible("12"))
        assertFalse(CardValidation.isSecurityCodePlausible(""))
    }

    @Test
    fun `holder name needs a surname`() {
        assertTrue(CardValidation.isHolderNamePlausible("Maria Silva"))
        assertFalse(CardValidation.isHolderNamePlausible("Maria"))
        assertFalse(CardValidation.isHolderNamePlausible(" "))
    }
}
