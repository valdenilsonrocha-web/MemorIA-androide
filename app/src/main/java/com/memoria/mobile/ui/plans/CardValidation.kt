package com.memoria.mobile.ui.plans

import java.time.YearMonth

/**
 * Local checks run before the card is sent anywhere.
 *
 * They exist to catch a typo without a network round trip — Mercado Pago is the
 * authority on whether a card is real, and its rejection is what the screen
 * ultimately reports.
 */
object CardValidation {

    /** Luhn checksum: catches a single mistyped digit in the card number. */
    fun isCardNumberPlausible(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** Accepts "MM/AA" or "MM/AAAA" and rejects anything already past. */
    fun parseExpiry(raw: String): Pair<Int, Int>? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 4 && digits.length != 6) return null
        val month = digits.take(2).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        val yearPart = digits.drop(2)
        val year = when (yearPart.length) {
            2 -> 2000 + (yearPart.toIntOrNull() ?: return null)
            4 -> yearPart.toIntOrNull() ?: return null
            else -> return null
        }
        // A card is valid through the LAST day of its expiry month.
        if (YearMonth.of(year, month).isBefore(YearMonth.now())) return null
        return month to year
    }

    fun isSecurityCodePlausible(raw: String): Boolean =
        raw.filter { it.isDigit() }.length in 3..4

    /** Full CPF check, including the two verifier digits. */
    fun isCpfValid(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 11) return false
        // Repeated digits pass the checksum but are never real documents.
        if (digits.all { it == digits[0] }) return false

        fun verifier(upTo: Int): Int {
            var sum = 0
            var weight = upTo + 1
            for (i in 0 until upTo) {
                sum += (digits[i] - '0') * weight
                weight--
            }
            val rest = (sum * 10) % 11
            return if (rest == 10) 0 else rest
        }

        return verifier(9) == digits[9] - '0' && verifier(10) == digits[10] - '0'
    }

    fun isHolderNamePlausible(raw: String): Boolean =
        raw.trim().length >= 3 && raw.trim().contains(" ")


}
