package com.memoria.mobile.ui.reports

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.VitalSignsRecord
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.health.VitalSignsPeriod
import com.memoria.mobile.ui.health.formatFullDateTime
import com.memoria.mobile.ui.health.inPeriod
import com.memoria.mobile.ui.health.instantOf
import com.memoria.mobile.ui.health.jsNumber
import com.memoria.mobile.ui.health.newestFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Adherence for one medication, as shown in "Por Medicamento". */
data class MedicationAdherence(
    val look: com.memoria.mobile.data.local.MedicationLook? = null,
    val name: String,
    val total: Int,
    val taken: Int,
) {
    val rate: Int get() = if (total == 0) 0 else ((taken.toDouble() / total) * 100).roundToInt()
}

/** One point of the 30-day chart; [rate] is null on a day with no records. */
data class DayAdherence(
    val date: LocalDate,
    val rate: Int?,
)

/** O filtro de período do "Histórico Completo de Tomadas", na ordem do `<select>`. */
enum class HistoryPeriod(val label: String) {
    ALL("Todo o período"),
    TODAY("Hoje"),
    LAST_7("Últimos 7 dias"),
    THIS_MONTH("Este mês"),
    LAST_30("Últimos 30 dias"),
}

data class ReportsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: User? = null,
    val history: List<HistoryEntry> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val vitalSigns: List<VitalSignsRecord> = emptyList(),
    /** O "Ícone do Medicamento" de cada um, por id — para as linhas de "Por Medicamento". */
    val looks: Map<String, com.memoria.mobile.data.local.MedicationLook> = emptyMap(),
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val search: String = "",
    val vitalPeriod: VitalSignsPeriod = VitalSignsPeriod.ALL,
    val message: String? = null,
    /**
     * True while the file picker is open. A flag rather than the bytes themselves:
     * the text is rebuilt from the state when the destination comes back, which
     * keeps this state comparable without a hand-written equals for a ByteArray.
     */
    val exporting: Boolean = false,
) {
    val isPremium: Boolean get() = user?.isPremium == true

    // ---- Adesão Geral (`loadAdherenceStats`) ----

    val taken: Int get() = history.count { it.status == "taken" }
    val missed: Int get() = history.count { it.status == "missed" }
    val overallRate: Int get() = pct(taken, history.size)

    val last7Rate: Int
        get() {
            val weekAgo = ZonedDateTime.now().minusDays(7).toInstant()
            val recent = history.filter { entryInstant(it)?.let { t -> !t.isBefore(weekAgo) } == true }
            return pct(recent.count { it.status == "taken" }, recent.size)
        }

    // ---- Por Medicamento (`loadMedicationStats`) ----

    val perMedication: List<MedicationAdherence>
        get() = medications.map { med ->
            val entries = history.filter { it.medicationId != null && it.medicationId == med.id }
            MedicationAdherence(
                look = med.id?.let { looks[it] },
                name = med.name,
                total = entries.size,
                taken = entries.count { it.status == "taken" },
            )
        }

    // ---- Adesão — Últimos 30 Dias (`loadReports30Days`) ----

    val last30Days: List<DayAdherence>
        get() {
            val today = LocalDate.now()
            val byDate = history.groupBy { entryDate(it) }
            return (29 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                val entries = byDate[date].orEmpty()
                DayAdherence(
                    date = date,
                    rate = if (entries.isEmpty()) null else pct(entries.count { it.status == "taken" }, entries.size),
                )
            }
        }

    // ---- Sequências e Tendência Semanal (`loadAdvancedStats`) ----

    /** Dias seguidos, a contar de hoje, em que tudo foi tomado; um dia vazio corta. */
    val streak: Int
        get() {
            val byDate = history.groupBy { entryDate(it) }
            val today = LocalDate.now()
            var count = 0
            for (i in 0 until 365) {
                val entries = byDate[today.minusDays(i.toLong())].orEmpty()
                if (entries.isEmpty()) break
                if (entries.all { it.status == "taken" }) count++ else break
            }
            return count
        }

    /** taken/total por dia da semana, índice 0 = domingo como no `getDay()`. */
    private val byWeekday: List<Pair<Int, Int>>
        get() {
            val taken = IntArray(7)
            val total = IntArray(7)
            history.forEach { entry ->
                val date = entryDate(entry) ?: return@forEach
                val day = date.dayOfWeek.value % 7
                total[day]++
                if (entry.status == "taken") taken[day]++
            }
            return (0 until 7).map { taken[it] to total[it] }
        }

    val bestWeekday: String
        get() {
            val days = byWeekday
            var best = 0
            days.forEachIndexed { i, (t, n) ->
                val p = if (n > 0) t.toDouble() / n else 0.0
                val bestP = if (days[best].second > 0) days[best].first.toDouble() / days[best].second else 0.0
                if (p > bestP) best = i
            }
            return DAY_NAMES[best]
        }

    val worstWeekday: String
        get() {
            val days = byWeekday
            var worst = 0
            days.forEachIndexed { i, (t, n) ->
                val p = if (n > 0) t.toDouble() / n else 1.0
                val worstP = if (days[worst].second > 0) days[worst].first.toDouble() / days[worst].second else 1.0
                if (p < worstP) worst = i
            }
            return DAY_NAMES[worst]
        }

    /** Adesão das últimas 4 semanas, da mais antiga (Sem. 1) para a actual (Sem. 4). */
    val weeklyRates: List<Int?>
        get() {
            val now = ZonedDateTime.now()
            return (0 until 4).map { w ->
                val start = now.minusDays(((w + 1) * 7).toLong()).toInstant()
                val end = now.minusDays((w * 7).toLong()).toInstant()
                val entries = history.filter { e ->
                    entryInstant(e)?.let { !it.isBefore(start) && it.isBefore(end) } == true
                }
                if (entries.isEmpty()) null else pct(entries.count { it.status == "taken" }, entries.size)
            }.reversed()
        }

    val trend: String
        get() {
            val weeks = weeklyRates
            val first = weeks[0]
            val last = weeks[3]
            return if (first != null && last != null) {
                when {
                    last > first -> "📈 Melhorando"
                    last < first -> "📉 Queda"
                    else -> "➡️ Estável"
                }
            } else {
                "— Sem dados suficientes"
            }
        }

    // ---- Histórico Completo de Tomadas (`loadTakenHistoryReport`) ----

    val allTaken: List<HistoryEntry>
        get() = history.filter { it.status == "taken" }
            .sortedByDescending { entryInstant(it)?.toEpochMilli() ?: 0L }

    /** O histórico de tomadas depois dos filtros de período e de nome. */
    val filteredHistory: List<HistoryEntry>
        get() {
            val zone = ZoneId.systemDefault()
            val start: Instant? = when (period) {
                HistoryPeriod.ALL -> null
                HistoryPeriod.TODAY -> LocalDate.now(zone).atStartOfDay(zone).toInstant()
                HistoryPeriod.LAST_7 -> ZonedDateTime.now(zone).minusDays(7).toInstant()
                HistoryPeriod.LAST_30 -> ZonedDateTime.now(zone).minusDays(30).toInstant()
                HistoryPeriod.THIS_MONTH -> LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant()
            }
            val term = search.trim().lowercase()
            return allTaken
                .filter { e -> start == null || entryInstant(e)?.let { !it.isBefore(start) } == true }
                .filter { term.isBlank() || it.medicationName.lowercase().contains(term) }
        }

    // ---- Relatório de Sinais Vitais (`loadVitalSignsReport`) ----

    val filteredVitalSigns: List<VitalSignsRecord>
        get() = vitalSigns.newestFirst().inPeriod(vitalPeriod)

    val vitalSignsSummary: String
        get() {
            val filtered = filteredVitalSigns
            val pieces = mutableListOf("${filtered.size} registro(s) no período")
            val sys = average(filtered.mapNotNull { it.systolic?.toDouble() })
            val dia = average(filtered.mapNotNull { it.diastolic?.toDouble() })
            val hr = average(filtered.mapNotNull { it.heartRate?.toDouble() })
            val glucose = average(filtered.mapNotNull { it.glucose })
            if (sys != null && dia != null) pieces += "PA média ${jsRound(sys)}/${jsRound(dia)} mmHg"
            if (hr != null) pieces += "FC média ${jsRound(hr)} bpm"
            if (glucose != null) pieces += "Glicose média ${jsRound(glucose)} mg/dL"
            return pieces.joinToString(" • ")
        }

    private companion object {
        val DAY_NAMES = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    }
}

/** O instante de um registo — o `timestamp` do histórico da web. */
fun entryInstant(entry: HistoryEntry): Instant? =
    instantOf(entry.takenAt) ?: instantOf(entry.createdAt) ?: instantOf(entry.scheduledFor)

private fun entryDate(entry: HistoryEntry): LocalDate? =
    entryInstant(entry)?.atZone(ZoneId.systemDefault())?.toLocalDate()

private fun pct(part: Int, total: Int): Int =
    if (total > 0) jsRound(part.toDouble() / total * 100) else 0

/** `Math.round` do JavaScript: meio arredonda sempre para cima. */
private fun jsRound(value: Double): Int = kotlin.math.floor(value + 0.5).toInt()

private fun average(values: List<Double>): Double? =
    if (values.isEmpty()) null else values.sum() / values.size

class ReportsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val historyResult = repo.history(limit = 500)
            val medications = (repo.medications() as? ApiResult.Ok)?.value.orEmpty()
            val user = (repo.me() as? ApiResult.Ok)?.value
            val vitalSigns = repo.local.vitalSigns()
            val looks = repo.local.medicationLooks().associateBy { it.medicationId }

            when (historyResult) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    loading = false,
                    user = user,
                    history = historyResult.value,
                    medications = medications,
                    vitalSigns = vitalSigns,
                    looks = looks,
                )
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    user = user,
                    medications = medications,
                    vitalSigns = vitalSigns,
                    looks = looks,
                    error = historyResult.message,
                )
            }
        }
    }

    fun setPeriod(period: HistoryPeriod) { _state.value = _state.value.copy(period = period) }

    fun setSearch(term: String) { _state.value = _state.value.copy(search = term) }

    fun clearFilters() {
        _state.value = _state.value.copy(period = HistoryPeriod.ALL, search = "")
    }

    fun setVitalPeriod(period: VitalSignsPeriod) { _state.value = _state.value.copy(vitalPeriod = period) }

    fun clearVitalFilter() { _state.value = _state.value.copy(vitalPeriod = VitalSignsPeriod.ALL) }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    /**
     * Hands the report text to the screen so it can be written to a file the user
     * picks, mirroring the web's "Exportar PDF" button — which, despite the label,
     * also writes plain text (see `exportReportPdf()` in `app.js`). Keeping the
     * same format means a report exported on either side reads identically.
     */
    fun requestExport() { _state.value = _state.value.copy(exporting = true) }

    /** Writes the report to the destination the user picked in the file picker. */
    fun writeExportTo(destination: Uri, resolver: ContentResolver) {
        val bytes = exportText().toByteArray()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(destination)?.use { it.write(bytes) }
                        ?: error("sem acesso de escrita")
                }.isSuccess
            }
            _state.value = _state.value.copy(
                exporting = false,
                message = if (ok) "✅ Relatório exportado!" else null,
                error = if (ok) null else "Não foi possível gravar o arquivo escolhido.",
            )
        }
    }

    fun discardExport() { _state.value = _state.value.copy(exporting = false) }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    /** O ficheiro do "Exportar PDF" — `exportReportPdf()` do `app.js`, linha a linha. */
    fun exportText(): String {
        val s = _state.value
        val total = s.history.size
        val taken = s.taken
        val adherence = pct(taken, total)
        val lines = mutableListOf(
            "Relatório MemorIA — ${LocalDate.now().format(DATE_BR)}",
            "Adesão geral: $adherence%",
            "Total de doses registradas: $total",
            "Doses tomadas: $taken | Perdidas: ${total - taken}",
            "",
            "Por medicamento:",
        )
        s.perMedication.forEach { lines += "  • ${it.name}: ${it.rate}% (${it.taken}/${it.total})" }

        val thirtyDaysAgo = ZonedDateTime.now().minusDays(30).toInstant()
        val vitalsInPeriod = s.vitalSigns
            .filter { instantOf(it.date)?.let { t -> !t.isBefore(thirtyDaysAgo) } == true }
            .newestFirst()
        lines += ""
        lines += "Sinais vitais (últimos 30 dias): ${vitalsInPeriod.size} registro(s)"
        if (vitalsInPeriod.isEmpty()) {
            lines += "  • Nenhum sinal vital registrado no período."
        } else {
            vitalsInPeriod.take(30).forEach { item ->
                val dateText = formatFullDateTime(item.date) ?: "Data inválida"
                val details = buildList {
                    if (item.systolic.nonZero() && item.diastolic.nonZero()) {
                        add("PA ${item.systolic}/${item.diastolic} mmHg")
                    }
                    item.heartRate?.takeIf { it != 0 }?.let { add("FC $it bpm") }
                    item.spo2?.takeIf { it != 0.0 }?.let { add("SpO₂ ${jsNumber(it)}%") }
                    item.glucose?.takeIf { it != 0.0 }?.let { add("Glicose ${jsNumber(it)} mg/dL") }
                    item.hba1c?.takeIf { it != 0.0 }?.let { add("HbA1c ${jsNumber(it)}%") }
                    item.temperature?.takeIf { it != 0.0 }?.let { add("Temp ${jsNumber(it)}°C") }
                }
                lines += "  • $dateText: ${details.joinToString(" | ").ifEmpty { "Sem dados numéricos" }}"
            }
        }
        return lines.joinToString("\n")
    }

    /** O texto do "Compartilhar" — `shareReport()` do `app.js`. */
    fun shareText(): String {
        val s = _state.value
        val sevenDaysAgo = ZonedDateTime.now().minusDays(7).toInstant()
        val recentVitalCount = s.vitalSigns.count {
            instantOf(it.date)?.let { t -> !t.isBefore(sevenDaysAgo) } == true
        }
        val total = s.history.size
        val taken = s.taken
        return "Minha adesão ao tratamento via MemorIA: ${pct(taken, total)}% ($taken/$total doses tomadas) " +
            "e $recentVitalCount registro(s) de sinais vitais na última semana. 💊"
    }

    private fun Int?.nonZero(): Boolean = this != null && this != 0

    private companion object {
        val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
