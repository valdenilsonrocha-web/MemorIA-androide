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
    /** Backend address, editable right here — see [AuthViewModel.saveBaseUrl]. */
    val baseUrl: String = "",
    val checking: Boolean = false,
    val serverMessage: String? = null,
    val serverOk: Boolean? = null,
    /** CPF + password restored from the encrypted store, once read. */
    val savedCpf: String = "",
    val savedPassword: String = "",
    val credentialsRestored: Boolean = false,
    val rememberMe: Boolean = true,
)

class AuthViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(baseUrl = repo.currentBaseUrl()))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun serverBaseUrl(): String = repo.currentBaseUrl()

    /**
     * Reads the remembered credentials so the login form can start filled in.
     * [AuthUiState.credentialsRestored] flips exactly once, which is what the
     * screen keys its one-shot prefill on — without it a recomposition would
     * overwrite whatever the user had started typing.
     */
    fun restoreCredentials() {
        viewModelScope.launch {
            val remember = repo.credentials.rememberEnabled()
            val saved = if (remember) repo.credentials.load() else null
            _state.value = _state.value.copy(
                savedCpf = saved?.cpf.orEmpty(),
                savedPassword = saved?.password.orEmpty(),
                credentialsRestored = true,
                rememberMe = remember,
            )
        }
    }

    fun setRememberMe(enabled: Boolean) {
        _state.value = _state.value.copy(rememberMe = enabled)
        // Turning it off wipes what is already stored, so the switch is also the
        // "forget my password" control on this screen.
        viewModelScope.launch { repo.credentials.setRememberEnabled(enabled) }
    }

    fun login(cpf: String, password: String, onSuccess: () -> Unit) {
        if (!validate(cpf, password)) return
        _state.value = _state.value.copy(loading = true, error = null, serverMessage = null)
        viewModelScope.launch {
            when (val r = repo.login(cpf, password)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(loading = false, error = null)
                    onSuccess()
                }
                is ApiResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = r.message)
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
            name.isBlank() -> {
                _state.value = _state.value.copy(error = "Informe o nome completo.")
                return
            }
            !validate(cpf, password) -> return
        }
        _state.value = _state.value.copy(loading = true, error = null, serverMessage = null)
        viewModelScope.launch {
            when (val r = repo.register(cpf, name, password, email, phone)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(loading = false, error = null)
                    onSuccess()
                }
                is ApiResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---- Server address (reachable before login, on purpose) ----

    fun onBaseUrl(value: String) {
        _state.value = _state.value.copy(baseUrl = value, serverOk = null, serverMessage = null)
    }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                serverMessage = "Informe o endereço do servidor.",
                serverOk = false,
            )
            return
        }
        viewModelScope.launch {
            repo.setBaseUrl(url)
            _state.value = _state.value.copy(
                baseUrl = repo.currentBaseUrl(),
                serverMessage = "Servidor salvo.",
                serverOk = null,
                error = null,
            )
        }
    }

    /** Escape hatch for a saved address that turned out to be wrong. */
    fun restoreDefaultBaseUrl() {
        viewModelScope.launch {
            repo.resetBaseUrlToDefault()
            _state.value = _state.value.copy(
                baseUrl = repo.currentBaseUrl(),
                serverMessage = "Endereço padrão restaurado.",
                serverOk = null,
                error = null,
            )
        }
    }

    fun testConnection() {
        _state.value = _state.value.copy(checking = true, serverOk = null, serverMessage = null)
        viewModelScope.launch {
            when (val r = repo.checkServer()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    checking = false,
                    serverOk = true,
                    serverMessage = "Servidor acessível.",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(
                    checking = false,
                    serverOk = false,
                    serverMessage = r.message,
                )
            }
        }
    }

    private fun validate(cpf: String, password: String): Boolean {
        val digits = cpf.filter { it.isDigit() }
        if (digits.length != 11) {
            _state.value = _state.value.copy(error = "CPF deve ter 11 dígitos.")
            return false
        }
        if (password.length < 6) {
            _state.value = _state.value.copy(error = "A senha deve ter ao menos 6 caracteres.")
            return false
        }
        return true
    }
}
