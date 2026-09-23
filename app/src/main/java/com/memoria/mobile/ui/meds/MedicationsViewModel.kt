package com.memoria.mobile.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.reminders.DoseActions
import com.memoria.mobile.reminders.DoseAlarm
import com.memoria.mobile.reminders.ReminderScheduler
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MedsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val medications: List<Medication> = emptyList(),
    val todaySlots: List<DoseSlot> = emptyList(),
    val local: Map<String, MedLocal> = emptyMap(),
    val message: String? = null,
) {
    /** Today's doses still open (upcoming / pending / missed), as "Próximos Horários". */
    val upcoming: List<Pair<DoseSlot, String>>
        get() = todaySlots.map { it to doseStatus(it) }.filter { it.second != "taken" }

    fun localFor(med: Medication): MedLocal = med.id?.let { local[it] } ?: MedLocal()
}

class MedicationsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MedsUiState())
    val state: StateFlow<MedsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val today = LocalDate.now()
                    val active = r.value.filter { it.active }
                    // Doses already logged today (here, on the web, or on another
                    // phone) come back marked, so they are not offered twice.
                    val history = (repo.history(limit = 200) as? ApiResult.Ok)?.value.orEmpty()
                    val looks = repo.local.medicationLooks().associateBy { it.medicationId }
                    val extras = repo.local.medicationExtras().associateBy { it.medicationId }
                    _state.value = _state.value.copy(
                        loading = false,
                        medications = active,
                        local = active.mapNotNull { m -> m.id?.let { it to MedLocal(looks[it], extras[it]) } }.toMap(),
                        todaySlots = Schedule.withHistory(
                            Schedule.slotsFor(active, today),
                            history,
                            today,
                        ),
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    /** "Tomei" — `takeMedication()`. */
    fun take(slot: DoseSlot) {
        val id = slot.medication.id ?: return
        viewModelScope.launch {
            val r = DoseActions.markTaken(repo, id, slot.medication.name, slot.medication.dosage, slot.time)
            _state.value = _state.value.copy(
                message = if (r is ApiResult.Ok) "✅ Medicamento registrado como tomado!" else "❌ Erro ao registrar medicamento.",
            )
            if (r is ApiResult.Ok) load()
        }
    }

    /** "Soneca" — `snoozeMedication()`: nothing goes to the history. */
    fun snooze(slot: DoseSlot, scheduler: ReminderScheduler) {
        val id = slot.medication.id ?: return
        viewModelScope.launch {
            val minutes = repo.snoozeMinutes()
            runCatching {
                scheduler.snooze(
                    DoseAlarm(
                        medicationId = id,
                        medicationName = slot.medication.name,
                        dosage = slot.medication.dosage,
                        instructions = slot.medication.instructions,
                        time = slot.time,
                        date = LocalDate.now().toString(),
                        snoozeMinutes = minutes,
                        soundId = repo.reminderSound(),
                    )
                )
            }
            _state.value = _state.value.copy(message = "⏰ Alarme adiado por $minutes minutos")
        }
    }

    fun delete(medication: Medication) {
        val id = medication.id ?: return
        viewModelScope.launch {
            when (repo.deleteMedication(id)) {
                is ApiResult.Ok -> {
                    repo.local.deleteMedicationLook(id)
                    _state.value = _state.value.copy(message = "🗑️ Medicamento excluído")
                    load()
                }
                is ApiResult.Err -> _state.value = _state.value.copy(message = "❌ Erro ao excluir medicamento")
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
