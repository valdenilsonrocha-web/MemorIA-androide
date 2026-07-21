package com.memoria.mobile.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.MedicationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedEditState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val isNew: Boolean = true,
    val name: String = "",
    val dosage: String = "",
    val frequency: String = "daily",
    val times: List<String> = emptyList(),
    val weekDays: List<Int> = emptyList(),
    val stock: String = "0",
    val instructions: String = "",
    val saved: Boolean = false,
)

class MedicationEditViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MedEditState())
    val state: StateFlow<MedEditState> = _state.asStateFlow()

    private var editingId: String? = null

    fun start(medicationId: String?) {
        editingId = medicationId
        if (medicationId == null) {
            _state.value = MedEditState(isNew = true)
            return
        }
        _state.value = _state.value.copy(loading = true, isNew = false)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val med = r.value.firstOrNull { it.id == medicationId }
                    if (med == null) {
                        _state.value = _state.value.copy(loading = false, error = "Medicamento não encontrado.")
                    } else {
                        _state.value = MedEditState(
                            loading = false,
                            isNew = false,
                            name = med.name,
                            dosage = med.dosage,
                            frequency = med.frequency,
                            times = med.times,
                            weekDays = med.weekDays ?: emptyList(),
                            stock = med.stock.toString(),
                            instructions = med.instructions ?: "",
                        )
                    }
                }
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onDosage(v: String) { _state.value = _state.value.copy(dosage = v) }
    fun onStock(v: String) { _state.value = _state.value.copy(stock = v.filter { it.isDigit() }) }
    fun onInstructions(v: String) { _state.value = _state.value.copy(instructions = v) }
    fun onFrequency(v: String) { _state.value = _state.value.copy(frequency = v) }

    fun addTime(time: String) {
        val t = time.trim()
        if (t.isBlank() || _state.value.times.contains(t)) return
        _state.value = _state.value.copy(times = (_state.value.times + t).sorted())
    }

    fun removeTime(time: String) {
        _state.value = _state.value.copy(times = _state.value.times - time)
    }

    fun toggleWeekDay(day: Int) {
        val current = _state.value.weekDays
        _state.value = _state.value.copy(
            weekDays = if (current.contains(day)) current - day else (current + day).sorted(),
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun save() {
        val s = _state.value
        when {
            s.name.isBlank() -> { _state.value = s.copy(error = "Informe o nome do medicamento."); return }
            s.dosage.isBlank() -> { _state.value = s.copy(error = "Informe a dosagem."); return }
            s.times.isEmpty() -> { _state.value = s.copy(error = "Adicione ao menos um horário."); return }
            s.frequency == "weekly" && s.weekDays.isEmpty() ->
                { _state.value = s.copy(error = "Escolha os dias da semana."); return }
        }
        val request = MedicationRequest(
            name = s.name.trim(),
            dosage = s.dosage.trim(),
            frequency = s.frequency,
            times = s.times,
            weekDays = if (s.frequency == "weekly") s.weekDays else null,
            instructions = s.instructions.trim().ifBlank { null },
            stock = s.stock.toIntOrNull() ?: 0,
            active = true,
        )
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = editingId?.let { repo.updateMedication(it, request) }
                ?: repo.createMedication(request)
            when (result) {
                is ApiResult.Ok -> _state.value = _state.value.copy(saving = false, saved = true)
                is ApiResult.Err -> _state.value = _state.value.copy(saving = false, error = result.message)
            }
        }
    }
}
