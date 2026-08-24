package com.memoria.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Recovery runs in two steps on one screen: ask for the code, then use it. */
enum class RecoveryStep { REQUEST, RESET }

data class ForgotPasswordUiState(
    val step: RecoveryStep = RecoveryStep.REQUEST,
    val working: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val cpf: String = "",
    val email: String = "",
    val token: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val done: Boolean = false,
)

class ForgotPasswordViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onCpf(v: String) { _state.value = _state.value.copy(cpf = v.filter { it.isDigit() }.take(11), error = null) }
    fun onEmail(v: String) { _state.value = _state.value.copy(email = v, error = null) }
    fun onToken(v: String) { _state.value = _state.value.copy(token = v.trim(), error = null) }
    fun onNewPassword(v: String) { _state.value = _state.value.copy(newPassword = v, error = null) }
    fun onConfirmPassword(v: String) { _state.value = _state.value.copy(confirmPassword = v, error = null) }

    fun backToRequest() { _state.value = _state.value.copy(step = RecoveryStep.REQUEST, error = null) }

    /** For someone who already has a code from a previous attempt or the web. */
    fun goToReset() { _state.value = _state.value.copy(step = RecoveryStep.RESET, error = null) }

    /**
     * The server answers identically whether or not the account exists, so it
     * cannot be used to discover which CPFs are registered. The screen has to
     * repeat that ambiguity instead of promising an e-mail is on its way.
     */
    fun requestCode() {
        val s = _state.value
        if (s.cpf.length != 11) {
            _state.value = s.copy(error = "CPF deve ter 11 dígitos.")
            return
        }
        if (!s.email.contains("@") || !s.email.contains(".")) {
            _state.value = s.copy(error = "Informe o e-mail cadastrado na conta.")
            return
        }
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.forgotPassword(s.cpf, s.email)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    working = false,
                    step = RecoveryStep.RESET,
                    notice = "Se houver uma conta com esse CPF e e-mail, enviamos o código de " +
                        "recuperação. Confira a sua caixa de entrada e o spam.",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    fun resetPassword() {
        val s = _state.value
        when {
            s.token.isBlank() -> {
                _state.value = s.copy(error = "Cole o código que chegou no seu e-mail.")
                return
            }
            // Same floor the server enforces on reset-password.
            s.newPassword.length < MIN_PASSWORD -> {
                _state.value = s.copy(error = "A nova senha deve ter ao menos $MIN_PASSWORD caracteres.")
                return
            }
            s.newPassword != s.confirmPassword -> {
                _state.value = s.copy(error = "As duas senhas não são iguais.")
                return
            }
        }
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.resetPassword(s.token, s.newPassword)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(working = false, done = true)
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    fun consumeNotice() { _state.value = _state.value.copy(notice = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        /** Mirrors `min: 8` on POST /api/auth/reset-password. */
        const val MIN_PASSWORD = 8
    }
}
