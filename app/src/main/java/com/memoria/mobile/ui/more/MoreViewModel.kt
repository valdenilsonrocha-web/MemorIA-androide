package com.memoria.mobile.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoreUiState(
    val userName: String? = null,
    val isAdmin: Boolean = false,
    val isPremium: Boolean = false,
)

class MoreViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    /**
     * Only the account flags matter here. A failure is silent on purpose: the
     * menu must still open offline, it just hides the admin entry.
     */
    fun load() {
        viewModelScope.launch {
            val user = (repo.me() as? ApiResult.Ok)?.value ?: return@launch
            _state.value = MoreUiState(
                userName = user.name,
                isAdmin = user.isAdmin,
                isPremium = user.isPremium,
            )
        }
    }
}
