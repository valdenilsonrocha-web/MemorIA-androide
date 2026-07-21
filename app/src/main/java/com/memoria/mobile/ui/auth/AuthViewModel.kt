package com.memoria.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun serverBaseUrl(): String = repo.currentBaseUrl()

    fun login(cpf: String, password: String, onSuccess: () -> Unit) {
        if (!validate(cpf, password)) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val r = repo.login(cpf, password)) {
                is ApiResult.Ok -> {
                    _state.value = AuthUiState()
                    onSuccess()
                }
                is ApiResult.Err -> _state.value = AuthUiState(error = r.message)
            }
        }
    }

    fun register(
        cpf: String,
        name: String,
        password: String,
        email: String,
        phone: String,
        onSuccess: () -> Unit,
    ) {
        when {
            name.isBlank() -> { _state.value = AuthUiState(error = "Informe o nome completo."); return }
            !validate(cpf, password) -> return
        }
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val r = repo.register(cpf, name, password, email, phone)) {
                is ApiResult.Ok -> {
                    _state.value = AuthUiState()
                    onSuccess()
                }
                is ApiResult.Err -> _state.value = AuthUiState(error = r.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun validate(cpf: String, password: String): Boolean {
        val digits = cpf.filter { it.isDigit() }
        if (digits.length != 11) {
            _state.value = AuthUiState(error = "CPF deve ter 11 dígitos.")
            return false
        }
        if (password.length < 6) {
            _state.value = AuthUiState(error = "A senha deve ter ao menos 6 caracteres.")
            return false
        }
        return true
    }
}
