package com.memoria.mobile.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.OwnerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: OwnerStats? = null,
)

class AdminViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.ownerStats()) {
                is ApiResult.Ok -> _state.value = AdminUiState(loading = false, stats = r.value)
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }
}

/** Gender codes the backend stores, mapped to their Portuguese labels. */
fun genderLabel(code: String?): String = when (code) {
    "female" -> "Feminino"
    "male" -> "Masculino"
    "other" -> "Outro"
    "not_informed", null, "" -> "Não informado"
    else -> code
}
