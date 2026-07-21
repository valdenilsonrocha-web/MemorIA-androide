package com.memoria.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<HistoryEntry> = emptyList(),
    val adherence: Adherence? = null,
)

class HistoryViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val historyResult = repo.history(limit = 100)
            val adherenceResult = repo.adherence(days = 30)

            val entries = (historyResult as? ApiResult.Ok)?.value ?: emptyList()
            val adherence = (adherenceResult as? ApiResult.Ok)?.value

            if (historyResult is ApiResult.Err) {
                _state.value = _state.value.copy(loading = false, error = historyResult.message)
            } else {
                _state.value = HistoryUiState(
                    loading = false,
                    entries = entries,
                    adherence = adherence,
                )
            }
        }
    }
}
