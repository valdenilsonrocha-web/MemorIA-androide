package com.memoria.mobile.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val medications: List<Medication> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
)

class CalendarViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    // 400 entries covers a dense month of doses without paging.
                    val history = (repo.history(limit = 400) as? ApiResult.Ok)?.value.orEmpty()
                    _state.value = _state.value.copy(
                        loading = false,
                        medications = r.value.filter { it.active },
                        history = history,
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun previousMonth() {
        _state.value = _state.value.copy(month = _state.value.month.minusMonths(1))
    }

    fun nextMonth() {
        _state.value = _state.value.copy(month = _state.value.month.plusMonths(1))
    }

    fun selectDay(day: LocalDate) {
        _state.value = _state.value.copy(selectedDay = day)
    }

    /** Doses due on [day], each carrying whatever status was already recorded. */
    fun slotsFor(day: LocalDate): List<DoseSlot> = Schedule.withHistory(
        Schedule.slotsFor(_state.value.medications, day),
        _state.value.history,
        day,
    )

    /** How many doses a day holds — drives the dot under each calendar cell. */
    fun doseCount(day: LocalDate): Int =
        Schedule.slotsFor(_state.value.medications, day).size
}
