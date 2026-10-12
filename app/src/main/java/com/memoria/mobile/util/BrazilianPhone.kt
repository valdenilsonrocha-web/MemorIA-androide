package com.memoria.mobile.util

/**
 * Brazilian WhatsApp numbers as stored by the app: DDD + local number, 10 or 11
 * digits, no country code. Prepending 55 by "does it already start with 55?"
 * treats Rio Grande do Sul (DDD 55) as if the country code were already there,
 * and `wa.me` then opens a number with no DDD.
 *
 * Same rule the backend uses in `formatPhone`: only a 10- or 11-digit value is a
 * local number; anything longer is left alone.
 */
object BrazilianPhone {

    fun digits(raw: String): String =
        raw.filter { it.isDigit() }.trimStart('0')

    /** Digits with country code 55 when the value is a local Brazilian number. */
    fun toWhatsAppId(raw: String): String {
        val d = digits(raw)
        return if (d.length == 10 || d.length == 11) "55$d" else d
    }

    fun waMeUrl(raw: String): String = "https://wa.me/${toWhatsAppId(raw)}"
}
