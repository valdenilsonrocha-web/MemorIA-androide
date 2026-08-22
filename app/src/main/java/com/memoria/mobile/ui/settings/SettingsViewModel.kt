package com.memoria.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val baseUrl: String = "",
    val error: String? = null,
    val message: String? = null,
    val checking: Boolean = false,
    val reachable: Boolean? = null,
    val hasSavedCredentials: Boolean = false,
)

class SettingsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(baseUrl = repo.currentBaseUrl()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, baseUrl = repo.currentBaseUrl())
        viewModelScope.launch {
            val hasSaved = repo.credentials.hasSaved()
            when (val r = repo.me()) {
                is ApiResult.Ok -> _state.value =
                    _state.value.copy(loading = false, user = r.value, hasSavedCredentials = hasSaved)
                is ApiResult.Err -> _state.value =
                    _state.value.copy(loading = false, error = r.message, hasSavedCredentials = hasSaved)
            }
        }
    }

    /** Wipes the remembered CPF + password; the login form goes back to empty. */
    fun forgetCredentials() {
        viewModelScope.launch {
            repo.forgetCredentials()
            _state.value = _state.value.copy(
                hasSavedCredentials = false,
                message = "CPF e senha esquecidos neste celular.",
            )
        }
    }

    fun onBaseUrl(v: String) { _state.value = _state.value.copy(baseUrl = v) }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isBlank()) { _state.value = _state.value.copy(error = "Informe a URL do servidor."); return }
        viewModelScope.launch {
            repo.setBaseUrl(url)
            _state.value = _state.value.copy(
                baseUrl = repo.currentBaseUrl(),
                message = "Servidor atualizado.",
                reachable = null,
            )
        }
    }

    fun testConnection() {
        _state.value = _state.value.copy(checking = true, reachable = null)
        viewModelScope.launch {
            // checkServer() carries the reason, so a failure names its cause
            // instead of only turning the indicator red.
            when (val r = repo.checkServer()) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(checking = false, reachable = true)
                is ApiResult.Err ->
                    _state.value = _state.value.copy(checking = false, reachable = false, error = r.message)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onDone()
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
