package com.memoria.mobile.ui.replenishment

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.MedicationExtras
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.meds.treatmentEndDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.min

enum class Urgency(val label: String) {
    HIGH("Alta"),
    MEDIUM("Média"),
    LOW("Baixa"),
    NONE("Encerrado"),
}

/**
 * Forecast for one medication, mirroring `loadReplenishmentPage()` in `app.js`:
 * stock ÷ estimated daily consumption gives the days of coverage (capped by the
 * remaining treatment days), and the suggested purchase date is three days
 * before the stock runs out.
 */
data class ReplenishmentItem(
    val medication: Medication,
    val look: MedicationLook?,
    val stock: Int,
    val dailyDoses: Double,
    val daysRemaining: Double,
    val continuousUse: Boolean,
    val treatmentEnd: LocalDate?,
    val stockEndDate: LocalDate,
    val purchaseDate: LocalDate,
    val urgency: Urgency,
) {
    /** `urgencyText`. */
    val purchaseText: String
        get() = when {
            urgency == Urgency.NONE -> "Tratamento encerrado"
            stock <= 0 || purchaseDate == LocalDate.now() -> "Comprar hoje"
            else -> "Comprar até ${com.memoria.mobile.ui.meds.formatDateBr(purchaseDate)}"
        }
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val extras = repo.local.medicationExtras().associateBy { it.medicationId }
                    val looks = repo.local.medicationLooks().associateBy { it.medicationId }
                    _state.value = ReplenishmentUiState(
                        loading = false,
                        items = r.value.filter { it.active }
                            .map { m -> forecast(m, m.id?.let { extras[it] }, m.id?.let { looks[it] }) }
                            // The web lists continuous-use medications first (stable sort).
                            .sortedByDescending { it.continuousUse },
                    )
                }
                is ApiResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun forecast(med: Medication, extras: MedicationExtras?, look: MedicationLook?): ReplenishmentItem {
        val today = LocalDate.now()
        val stock = med.stock.coerceAtLeast(0)
        val daily = Schedule.estimatedDailyDoses(med)
        val daysRemaining = if (daily > 0) stock / daily else 0.0
        val continuous = extras?.continuousUse == true
        val treatmentEnd = if (continuous) null else treatmentEndDate(med, extras?.treatmentDurationDays)
        val remainingTreatmentDays = treatmentEnd?.let {
            (ChronoUnit.DAYS.between(today, it) + 1).coerceAtLeast(0)
        }
        val treatmentEnded = !continuous && remainingTreatmentDays == 0L
        val effective = remainingTreatmentDays?.let { min(daysRemaining, it.toDouble()) } ?: daysRemaining

        // Coverage is whole days already paid for: the last dose is taken on the
        // day the stock ends, so the ceiling is reduced by one.
        val coverageDays = if (daily > 0) (ceil(effective).toLong() - 1).coerceAtLeast(0) else 0L
        val stockEnd = today.plusDays(coverageDays)
        val purchase = stockEnd.minusDays(3).let { if (it.isBefore(today)) today else it }

        val urgency = when {
            treatmentEnded -> Urgency.NONE
            stock <= 0 || effective <= 3 -> Urgency.HIGH
            effective <= 7 -> Urgency.MEDIUM
            else -> Urgency.LOW
        }

        return ReplenishmentItem(
            medication = med,
            look = look,
            stock = stock,
            dailyDoses = daily,
            daysRemaining = effective,
            continuousUse = continuous,
            treatmentEnd = treatmentEnd,
            stockEndDate = stockEnd,
            purchaseDate = purchase,
            urgency = urgency,
        )
    }
}
