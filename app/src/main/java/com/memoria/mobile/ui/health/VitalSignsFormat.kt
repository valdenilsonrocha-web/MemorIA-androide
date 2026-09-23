package com.memoria.mobile.ui.health

import com.memoria.mobile.data.local.VitalSignsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatação partilhada pela página Saúde e pelo "Relatório de Sinais Vitais"
 * dos Relatórios, para os dois mostrarem os números exactamente como a web.
 */

/** Teto do histórico de sinais vitais — `VITAL_SIGNS_MAX_RECORDS` do `app.js`. */
const val VITAL_SIGNS_MAX_RECORDS = 5000

/** `toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })`. */
private val PT_BR_SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")

/** `toLocaleString('pt-BR')` sem opções — inclui os segundos. */
private val PT_BR_FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")

/** Instante de uma data ISO-8601 (com ou sem fuso), ou null se não for legível. */
fun instantOf(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(iso) }.getOrNull()
}

/** "22/09/2026, 14:30" — ou "" quando a data não é legível. */
fun formatShortDateTime(iso: String?): String =
    instantOf(iso)?.atZone(ZoneId.systemDefault())?.format(PT_BR_SHORT).orEmpty()

/** "22/09/2026, 14:30:05" — ou null quando a data não é legível. */
fun formatFullDateTime(iso: String?): String? =
    instantOf(iso)?.atZone(ZoneId.systemDefault())?.format(PT_BR_FULL)

/**
 * Um número como o JavaScript o escreve num template string: `98`, `36.5`,
 * `5.7` — sem casas decimais inúteis e com ponto, não vírgula.
 */
fun jsNumber(value: Double): String =
    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

/** Mais recente primeiro, com as datas ilegíveis no fim — o `sort` da web. */
fun List<VitalSignsRecord>.newestFirst(): List<VitalSignsRecord> =
    sortedByDescending { instantOf(it.date)?.toEpochMilli() ?: 0L }

/** Os períodos do filtro "Relatório de Sinais Vitais", na ordem do `<select>`. */
enum class VitalSignsPeriod(val value: String, val label: String) {
    ALL("all", "Todo o período"),
    TODAY("today", "Hoje"),
    LAST_7("7days", "Últimos 7 dias"),
    THIS_MONTH("month", "Este mês"),
    LAST_30("30days", "Últimos 30 dias"),
    LAST_90("90days", "Últimos 90 dias"),
}

/** `filterVitalSignsByPeriod()` do `app.js`. */
fun List<VitalSignsRecord>.inPeriod(period: VitalSignsPeriod): List<VitalSignsRecord> {
    val zone = ZoneId.systemDefault()
    val now = java.time.ZonedDateTime.now(zone)
    val start: Instant = when (period) {
        VitalSignsPeriod.ALL -> return this
        VitalSignsPeriod.TODAY -> LocalDate.now(zone).atStartOfDay(zone).toInstant()
        VitalSignsPeriod.LAST_7 -> now.minusDays(7).toInstant()
        VitalSignsPeriod.LAST_30 -> now.minusDays(30).toInstant()
        VitalSignsPeriod.LAST_90 -> now.minusDays(90).toInstant()
        VitalSignsPeriod.THIS_MONTH -> LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant()
    }
    return filter { record -> instantOf(record.date)?.let { !it.isBefore(start) } == true }
}

/** "jejum", "pós-refeição" ou "aleatória" — o `contextMap` do relatório. */
fun glucoseContextReportLabel(context: String): String = when (context) {
    "fasting" -> "jejum"
    "postmeal" -> "pós-refeição"
    else -> "aleatória"
}
