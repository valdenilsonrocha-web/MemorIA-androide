package com.memoria.mobile.ui.calendar

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.MedicalConsultation
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Indicator dot under a calendar day: `.calendar-day-indicator.<colour>`. */
enum class DayIndicator { GREEN, RED }

data class CalendarUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val month: YearMonth = YearMonth.now(),
    /** Null until a day is tapped — the web's `#dayDetails` starts empty. */
    val selectedDay: LocalDate? = null,
    val medications: List<Medication> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val consultations: List<MedicalConsultation> = emptyList(),
    val looks: Map<String, MedicationLook> = emptyMap(),
)

class CalendarViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val consultations = runCatching { repo.local.consultations() }.getOrDefault(emptyList())
            val looks = repo.local.medicationLooks().associateBy { it.medicationId }
            when (val r = repo.history(limit = 400)) {
                is ApiResult.Ok -> {
                    val meds = (repo.medications() as? ApiResult.Ok)?.value.orEmpty()
                    _state.update { it.copy(
                        loading = false,
                        medications = meds,
                        history = r.value + com.memoria.mobile.reminders.DoseActions.pendingHistory(repo),
                        consultations = consultations,
                        looks = looks,
                    ) }
                }
                is ApiResult.Err -> _state.update { it.copy(
                    loading = false,
                    error = r.message,
                    consultations = consultations,
                    looks = looks,
                ) }
            }
        }
    }

    fun previousMonth() {
        _state.update { it.copy(month = _state.value.month.minusMonths(1)) }
    }

    fun nextMonth() {
        _state.update { it.copy(month = _state.value.month.plusMonths(1)) }
    }

    fun selectDay(day: LocalDate) {
        _state.update { it.copy(selectedDay = day) }
    }

    /** History entries recorded on [day] (the web's `entry.timestamp`). */
    fun historyOn(day: LocalDate): List<HistoryEntry> =
        _state.value.history.filter { recordedOn(it) == day }

    /** Consultations on [day], earliest first. */
    fun consultationsOn(day: LocalDate): List<MedicalConsultation> =
        _state.value.consultations
            .mapNotNull { c -> parseLocal(c.dateTime)?.let { it to c } }
            .filter { it.first.toLocalDate() == day }
            .sortedBy { it.first }
            .map { it.second }

    /** Green when only taken doses, red when any dose was not taken — as `loadCalendar()`. */
    fun indicator(day: LocalDate): DayIndicator? {
        val entries = historyOn(day)
        val taken = entries.count { it.status == "taken" }
        val missed = entries.size - taken
        return when {
            taken > 0 && missed == 0 -> DayIndicator.GREEN
            missed > 0 -> DayIndicator.RED
            else -> null
        }
    }

    fun hasConsultation(day: LocalDate): Boolean = consultationsOn(day).isNotEmpty()

    fun lookFor(entry: HistoryEntry): MedicationLook? {
        val id = entry.medicationId
            ?: _state.value.medications.firstOrNull { it.name == entry.medicationName }?.id
        return id?.let { _state.value.looks[it] }
    }

    private fun recordedOn(e: HistoryEntry): LocalDate? =
        listOfNotNull(e.takenAt, e.createdAt, e.scheduledFor).firstNotNullOfOrNull { iso ->
            runCatching {
                OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
        }

    private fun parseLocal(raw: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(raw.trim()) }.getOrNull()
}
