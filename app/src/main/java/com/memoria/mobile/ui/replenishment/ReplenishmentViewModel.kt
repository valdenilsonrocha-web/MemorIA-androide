package com.memoria.mobile.ui.replenishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil

enum class Urgency(val label: String) {
    HIGH("Alta"),
    MEDIUM("Média"),
    LOW("Baixa"),
}

/**
 * Forecast for one medication, mirroring `loadReplenishmentPage()` in `app.js`:
 * stock ÷ estimated daily consumption gives the days of coverage, and the
 * suggested purchase date is three days before the stock runs out.
 */
data class ReplenishmentItem(
    val medication: Medication,
    val stock: Int,
    val dailyDoses: Double,
    val daysRemaining: Double,
    val stockEndDate: LocalDate,
    val purchaseDate: LocalDate,
    val urgency: Urgency,
) {
    val buyToday: Boolean get() = stock <= 0 || purchaseDate == LocalDate.now()
}

data class ReplenishmentUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<ReplenishmentItem> = emptyList(),
)

class ReplenishmentViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReplenishmentUiState())
    val state: StateFlow<ReplenishmentUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> _state.value = ReplenishmentUiState(
                    loading = false,
                    items = r.value.filter { it.active }
                        .map(::forecast)
                        // Most urgent first: what runs out soonest needs buying first.
                        .sortedBy { it.daysRemaining },
                )
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    private fun forecast(med: Medication): ReplenishmentItem {
        val today = LocalDate.now()
        val stock = med.stock.coerceAtLeast(0)
        val daily = Schedule.estimatedDailyDoses(med)
        val daysRemaining = if (daily > 0) stock / daily else 0.0

        // Coverage is whole days already paid for: the last dose is taken on the
        // day the stock ends, so the ceiling is reduced by one.
        val coverageDays = if (daily > 0) (ceil(daysRemaining).toLong() - 1).coerceAtLeast(0) else 0L
        val stockEnd = today.plusDays(coverageDays)
        val purchase = stockEnd.minusDays(3).let { if (it.isBefore(today)) today else it }

        val urgency = when {
            stock <= 0 || daysRemaining <= 3 -> Urgency.HIGH
            daysRemaining <= 7 -> Urgency.MEDIUM
            else -> Urgency.LOW
        }

        return ReplenishmentItem(
            medication = med,
            stock = stock,
            dailyDoses = daily,
            daysRemaining = daysRemaining,
            stockEndDate = stockEnd,
            purchaseDate = purchase,
            urgency = urgency,
        )
    }
}
