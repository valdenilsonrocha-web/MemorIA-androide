package com.memoria.mobile.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Adherence for one medication, as shown in "Por Medicamento". */
data class MedicationAdherence(
    val name: String,
    val total: Int,
    val taken: Int,
) {
    val rate: Int get() = if (total == 0) 0 else ((taken.toDouble() / total) * 100).roundToInt()
}

/** One bar of the 30-day chart. */
data class DayAdherence(
    val date: LocalDate,
    val total: Int,
    val taken: Int,
) {
    val rate: Float get() = if (total == 0) 0f else taken.toFloat() / total
}

enum class HistoryPeriod(val label: String, val days: Int?) {
    ALL("Todo o período", null),
    TODAY("Hoje", 0),
    LAST_7("Últimos 7 dias", 7),
    THIS_MONTH("Este mês", null),
    LAST_30("Últimos 30 dias", 30),
}

data class ReportsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: User? = null,
    val adherence: Adherence? = null,
    val history: List<HistoryEntry> = emptyList(),
    val perMedication: List<MedicationAdherence> = emptyList(),
    val last30Days: List<DayAdherence> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val weeklyTrend: List<DayAdherence> = emptyList(),
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val search: String = "",
) {
    val isPremium: Boolean get() = user?.isPremium == true

    /** The "Histórico Completo de Tomadas" list, after period + name filters. */
    val filteredHistory: List<HistoryEntry>
        get() {
            val today = LocalDate.now()
            val term = search.trim().lowercase()
            return history.asSequence()
                .filter { entry ->
                    val date = Schedule.localDateOf(entry.scheduledFor ?: entry.createdAt)
                    when (period) {
                        HistoryPeriod.ALL -> true
                        HistoryPeriod.TODAY -> date == today
                        HistoryPeriod.THIS_MONTH ->
                            date != null && date.year == today.year && date.month == today.month
                        else -> {
                            val days = period.days ?: return@filter true
                            date != null && !date.isBefore(today.minusDays(days.toLong() - 1))
                        }
                    }
                }
                .filter { term.isBlank() || it.medicationName.lowercase().contains(term) }
                .sortedByDescending { it.scheduledFor ?: it.createdAt ?: "" }
                .toList()
        }
}

class ReportsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val historyResult = repo.history(limit = 500)
            val adherence = (repo.adherence(days = 30) as? ApiResult.Ok)?.value
            val user = (repo.me() as? ApiResult.Ok)?.value

            when (historyResult) {
                is ApiResult.Ok -> {
                    val history = historyResult.value
                    val daily = dailyAdherence(history, days = 30)
                    _state.value = _state.value.copy(
                        loading = false,
                        user = user,
                        adherence = adherence,
                        history = history,
                        perMedication = perMedication(history),
                        last30Days = daily,
                        currentStreak = currentStreak(daily),
                        bestStreak = bestStreak(daily),
                        weeklyTrend = daily.takeLast(7),
                    )
                }
                is ApiResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = historyResult.message)
            }
        }
    }

    fun setPeriod(period: HistoryPeriod) { _state.value = _state.value.copy(period = period) }

    fun setSearch(term: String) { _state.value = _state.value.copy(search = term) }

    fun clearFilters() {
        _state.value = _state.value.copy(period = HistoryPeriod.ALL, search = "")
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    /** Plain-text report, for the share sheet. */
    fun shareText(): String {
        val s = _state.value
        val lines = mutableListOf(
            "MemorIA — Relatório de adesão",
            "Período: últimos 30 dias",
            "Adesão geral: ${s.adherence?.adherenceRate ?: "—"}",
            "Doses tomadas: ${s.adherence?.taken ?: 0}",
            "Doses perdidas: ${s.adherence?.missed ?: 0}",
            "Sequência atual: ${s.currentStreak} dia(s)",
            "",
            "Por medicamento:",
        )
        s.perMedication.forEach { lines += "- ${it.name}: ${it.rate}% (${it.taken}/${it.total})" }
        lines += ""
        lines += "Este relatório não substitui orientação médica."
        return lines.joinToString("\n")
    }

    private fun perMedication(history: List<HistoryEntry>): List<MedicationAdherence> =
        history.groupBy { it.medicationName.ifBlank { "Sem nome" } }
            .map { (name, entries) ->
                MedicationAdherence(
                    name = name,
                    total = entries.size,
                    taken = entries.count { it.status == "taken" },
                )
            }
            .sortedByDescending { it.total }

    /**
     * One bucket per calendar day, oldest first. Days with no entry are kept as
     * empty buckets so the chart keeps a constant width and the streak logic can
     * tell "nothing scheduled" apart from "scheduled and missed".
     */
    private fun dailyAdherence(history: List<HistoryEntry>, days: Int): List<DayAdherence> {
        val today = LocalDate.now()
        val byDate = history.groupBy { Schedule.localDateOf(it.scheduledFor ?: it.createdAt) }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val entries = byDate[date].orEmpty()
            DayAdherence(
                date = date,
                total = entries.size,
                taken = entries.count { it.status == "taken" },
            )
        }
    }

    /** Consecutive perfect days ending today; days with no doses do not break it. */
    private fun currentStreak(daily: List<DayAdherence>): Int {
        var streak = 0
        for (day in daily.reversed()) {
            if (day.total == 0) continue
            if (day.taken == day.total) streak++ else break
        }
        return streak
    }

    private fun bestStreak(daily: List<DayAdherence>): Int {
        var best = 0
        var run = 0
        for (day in daily) {
            if (day.total == 0) continue
            if (day.taken == day.total) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }
}
