package com.memoria.mobile.ui.auth

import kotlinx.coroutines.flow.update

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
    /** CPF + password + name restored from the encrypted store, once read. */
    val savedCpf: String = "",
    val savedPassword: String = "",
    val savedName: String = "",
    val credentialsRestored: Boolean = false,
    val rememberMe: Boolean = true,
) {
    val hasSavedCredentials: Boolean
        get() = savedCpf.isNotBlank() && savedPassword.isNotBlank()
}

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
            _state.update { it.copy(
                savedCpf = saved?.cpf.orEmpty(),
                savedPassword = saved?.password.orEmpty(),
                savedName = saved?.name.orEmpty(),
                credentialsRestored = true,
                rememberMe = remember,
            ) }
        }
    }

    fun setRememberMe(enabled: Boolean) {
        _state.update { it.copy(rememberMe = enabled) }
        // Turning it off wipes what is already stored, so the switch is also the
        // "forget my password" control on this screen.
        viewModelScope.launch { repo.credentials.setRememberEnabled(enabled) }
    }

    /** 1-click login for elderly users using Keystore-saved credentials. */
    fun loginWithSaved(onSuccess: () -> Unit) {
        val cpf = _state.value.savedCpf
        val password = _state.value.savedPassword
        if (cpf.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Nenhuma senha salva encontrada. Digite sua senha.") }
            return
        }
        login(cpf, password, onSuccess)
    }

    fun login(cpf: String, password: String, onSuccess: () -> Unit) {
        val cleanCpf = cpf.filter { it.isDigit() }
        val savedCpfClean = _state.value.savedCpf.filter { it.isDigit() }
        // If password was empty but we have saved credentials for this CPF, auto-fill it!
        val effectivePassword = if (password.isBlank() && cleanCpf.isNotBlank() && cleanCpf == savedCpfClean) {
            _state.value.savedPassword
        } else {
            password
        }
        if (!validate(cpf, effectivePassword)) return
        _state.update { it.copy(loading = true, error = null, serverMessage = null) }
        viewModelScope.launch {
            when (val r = repo.login(cpf, effectivePassword)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(loading = false, error = null) }
                    onSuccess()
                }
                is ApiResult.Err ->
                    _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun register(
        cpf: String,
        name: String,
        password: String,
        confirmPassword: String,
        email: String,
        phone: String,
        lgpdConsent: Boolean,
        onSuccess: () -> Unit,
        susCard: String = "",
        gender: String = "not_informed",
    ) {
        when {
            name.isBlank() -> {
                _state.update { it.copy(error = "Preencha todos os campos obrigatórios.") }
                return
            }
            name.trim().length < 3 -> {
                _state.update { it.copy(error = "O nome deve ter pelo menos 3 caracteres.") }
                return
            }
            !lgpdConsent -> {
                _state.update { it.copy(
                    error = "É necessário aceitar a Política de Privacidade e autorizar o tratamento dos dados.",
                ) }
                return
            }
            !validate(cpf, password, minPassword = REGISTER_MIN_PASSWORD) -> return
            confirmPassword.isEmpty() -> {
                _state.update { it.copy(error = "Digite a senha novamente para confirmar.") }
                return
            }
            password != confirmPassword -> {
                _state.update { it.copy(error = "As duas senhas não são iguais.") }
                return
            }
        }
        _state.update { it.copy(loading = true, error = null, serverMessage = null) }
        viewModelScope.launch {
            when (val r = repo.register(cpf, name, password, email, phone, lgpdConsent, susCard, gender)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(loading = false, error = null) }
                    onSuccess()
                }
                is ApiResult.Err ->
                    _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ---- Server address (reachable before login, on purpose) ----

    fun onBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value, serverOk = null, serverMessage = null) }
    }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(
                serverMessage = "Informe o endereço do servidor.",
                serverOk = false,
            ) }
            return
        }
        viewModelScope.launch {
            repo.setBaseUrl(url)
            _state.update { it.copy(
                baseUrl = repo.currentBaseUrl(),
                serverMessage = "Servidor salvo.",
                serverOk = null,
                error = null,
            ) }
        }
    }

    /** Escape hatch for a saved address that turned out to be wrong. */
    fun restoreDefaultBaseUrl() {
        viewModelScope.launch {
            repo.resetBaseUrlToDefault()
            _state.update { it.copy(
                baseUrl = repo.currentBaseUrl(),
                serverMessage = "Endereço padrão restaurado.",
                serverOk = null,
                error = null,
            ) }
        }
    }

    fun testConnection() {
        _state.update { it.copy(checking = true, serverOk = null, serverMessage = null) }
        viewModelScope.launch {
            when (val r = repo.checkServer()) {
                is ApiResult.Ok -> _state.update { it.copy(
                    checking = false,
                    serverOk = true,
                    serverMessage = "Servidor acessível.",
                ) }
                is ApiResult.Err -> _state.update { it.copy(
                    checking = false,
                    serverOk = false,
                    serverMessage = r.message,
                ) }
            }
        }
    }

    /**
     * [minPassword] differs by flow on purpose: the server demands 8 characters
     * to REGISTER, but login only requires a non-empty password. Validating
     * login against 8 would lock out anyone whose account predates that rule.
     */
    private fun validate(cpf: String, password: String, minPassword: Int = 1): Boolean {
        val digits = cpf.filter { it.isDigit() }
        if (digits.length != 11) {
            _state.update { it.copy(error = "CPF deve conter 11 dígitos.") }
            return false
        }
        if (password.length < minPassword) {
            _state.update { it.copy(
                error = if (minPassword > 1) {
                    "A senha deve ter pelo menos $minPassword caracteres."
                } else {
                    "Informe a sua senha."
                },
            ) }
            return false
        }
        return true
    }

    companion object {
        /** Mirrors the backend rule (`min: 8` on POST /api/auth/register). */
        const val REGISTER_MIN_PASSWORD = 8
    }
}
