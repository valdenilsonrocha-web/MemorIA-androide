package com.memoria.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryRequest
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val medications: List<Medication> = emptyList(),
    val todaySlots: List<DoseSlot> = emptyList(),
) {
    val todayCount: Int get() = todaySlots.size
    val completedCount: Int get() = todaySlots.count { it.doneStatus == "taken" }
    val pendingCount: Int get() = todaySlots.count { it.doneStatus == null }

    /**
     * "Próximos Horários": what is still open today, from the current hour on,
     * with anything already overdue kept at the top so it is not buried.
     */
    val upcoming: List<DoseSlot>
        get() {
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val open = todaySlots.filter { it.doneStatus == null }
            val (late, next) = open.partition { it.time < now }
            return late + next
        }
}

class DashboardViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val medsResult = repo.medications()
            val history = (repo.history(limit = 200) as? ApiResult.Ok)?.value.orEmpty()
            val user = (repo.me() as? ApiResult.Ok)?.value

            when (medsResult) {
                is ApiResult.Ok -> {
                    val today = LocalDate.now()
                    val active = medsResult.value.filter { it.active }
                    _state.value = DashboardUiState(
                        loading = false,
                        user = user,
                        medications = active,
                        todaySlots = Schedule.withHistory(
                            Schedule.slotsFor(active, today),
                            history,
                            today,
                        ),
                    )
                }
                is ApiResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = medsResult.message)
            }
        }
    }

    fun markDose(slot: DoseSlot, status: String) {
        val id = slot.medication.id ?: return
        val request = HistoryRequest(
            medicationId = id,
            medicationName = slot.medication.name,
            dosage = slot.medication.dosage,
            scheduleTime = slot.time,
            status = status,
            scheduledFor = Schedule.isoFor(LocalDate.now(), slot.time),
            takenAt = if (status == "taken") {
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } else {
                null
            },
        )
        viewModelScope.launch {
            when (val r = repo.addHistory(request)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    todaySlots = _state.value.todaySlots.map {
                        if (it.medication.id == id && it.time == slot.time) it.copy(doneStatus = status) else it
                    },
                    message = when (status) {
                        "taken" -> "Dose registrada como tomada. ✅"
                        "snoozed" -> "Dose adiada."
                        else -> "Dose registrada como não tomada."
                    },
                )
                is ApiResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
