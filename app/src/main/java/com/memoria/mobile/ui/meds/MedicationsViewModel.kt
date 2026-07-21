package com.memoria.mobile.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryRequest
import com.memoria.mobile.data.remote.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One scheduled dose for today, derived from a medication + one of its times. */
data class DoseSlot(
    val medication: Medication,
    val time: String, // "HH:mm"
    val doneStatus: String? = null, // set locally after marking (taken/missed/snoozed)
)

data class MedsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val medications: List<Medication> = emptyList(),
    val todaySlots: List<DoseSlot> = emptyList(),
    val message: String? = null,
)

class MedicationsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MedsUiState())
    val state: StateFlow<MedsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val active = r.value.filter { it.active }
                    _state.value = _state.value.copy(
                        loading = false,
                        medications = active,
                        todaySlots = buildTodaySchedule(active),
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun markDose(slot: DoseSlot, status: String) {
        val med = slot.medication
        val id = med.id ?: return
        val scheduledFor = isoForToday(slot.time)
        val request = HistoryRequest(
            medicationId = id,
            medicationName = med.name,
            dosage = med.dosage,
            scheduleTime = slot.time,
            status = status,
            scheduledFor = scheduledFor,
            takenAt = if (status == "taken") OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) else null,
        )
        viewModelScope.launch {
            when (val r = repo.addHistory(request)) {
                is ApiResult.Ok -> {
                    val updated = _state.value.todaySlots.map {
                        if (it.medication.id == id && it.time == slot.time) it.copy(doneStatus = status) else it
                    }
                    _state.value = _state.value.copy(
                        todaySlots = updated,
                        message = confirmationText(status),
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun delete(medication: Medication) {
        val id = medication.id ?: return
        viewModelScope.launch {
            when (val r = repo.deleteMedication(id)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(message = "Medicamento removido.")
                    load()
                }
                is ApiResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun confirmationText(status: String): String = when (status) {
        "taken" -> "Dose registrada como tomada. ✅"
        "snoozed" -> "Dose adiada."
        else -> "Dose registrada como não tomada."
    }

    private fun buildTodaySchedule(meds: List<Medication>): List<DoseSlot> {
        val today = LocalDate.now()
        val weekDay = today.dayOfWeek.value % 7 // Mon=1..Sun=7 → Sun=0..Sat=6
        val slots = mutableListOf<DoseSlot>()
        for (med in meds) {
            val applies = when (med.frequency) {
                "weekly" -> med.weekDays?.contains(weekDay) == true
                "alternate" -> alternateAppliesToday(med, today)
                else -> true // daily
            }
            if (!applies) continue
            med.times.filter { it.isNotBlank() }.forEach { t ->
                slots.add(DoseSlot(medication = med, time = normalizeTime(t)))
            }
        }
        return slots.sortedBy { it.time }
    }

    // Alternate-day meds: parity of days since the medication was created.
    private fun alternateAppliesToday(med: Medication, today: LocalDate): Boolean {
        val created = med.createdAt?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        } ?: return true
        val days = java.time.temporal.ChronoUnit.DAYS.between(created, today)
        return days % 2 == 0L
    }

    private fun normalizeTime(t: String): String = runCatching {
        LocalTime.parse(t.trim()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(t.trim())

    private fun isoForToday(time: String): String {
        val t = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.MIDNIGHT)
        val dt = LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toOffsetDateTime()
        return dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
