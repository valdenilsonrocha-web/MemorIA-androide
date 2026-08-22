package com.memoria.mobile.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MedicationDetailsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val medication: Medication? = null,
    val history: List<HistoryEntry> = emptyList(),
    val deleted: Boolean = false,
) {
    val total: Int get() = history.size
    val taken: Int get() = history.count { it.status == "taken" }
    val missed: Int get() = total - taken

    /** Percentage of logged doses that were actually taken, 0 when none logged. */
    val adherence: Int
        get() = if (total == 0) 0 else ((taken.toDouble() / total) * 100).roundToInt()
}

class MedicationDetailsViewModel(
    private val repo: MemoriaRepository,
    private val medicationId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationDetailsUiState())
    val state: StateFlow<MedicationDetailsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val med = r.value.firstOrNull { it.id == medicationId }
                    if (med == null) {
                        _state.value = _state.value.copy(
                            loading = false,
                            error = "Medicamento não encontrado.",
                        )
                        return@launch
                    }
                    val history = (repo.history(limit = 200) as? ApiResult.Ok)?.value
                        .orEmpty()
                        .filter { it.medicationId == medicationId }
                    _state.value = MedicationDetailsUiState(
                        loading = false,
                        medication = med,
                        history = history,
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            when (val r = repo.deleteMedication(medicationId)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(deleted = true)
                is ApiResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
