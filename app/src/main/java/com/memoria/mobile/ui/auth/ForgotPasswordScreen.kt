package com.memoria.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.repoViewModel

/**
 * "Esqueci minha senha" — the web `forgotPasswordModal`, and the way out of what
 * was a dead end: the app saves the password, so people stop rehearsing it, and
 * before this there was nothing to do on the phone when they forgot.
 */
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val vm = repoViewModel { ForgotPasswordViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.done) { if (state.done) onFinished() }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.consumeNotice() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = { BackTopBar("Recuperar senha", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                RecoveryStep.REQUEST -> {
                    Text(
                        "Informe o CPF e o e-mail cadastrados. Enviaremos um código para você criar " +
                            "uma senha nova.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.cpf,
                        onValueChange = vm::onCpf,
                        label = { Text("CPF (somente números)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = vm::onEmail,
                        label = { Text("E-mail da conta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("Enviar código", state.working, vm::requestCode)
                    TextButton(
                        onClick = vm::goToReset,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Já tenho um código")
                    }
                }

                RecoveryStep.RESET -> {
                    Text(
                        "Cole abaixo o código que chegou no seu e-mail e escolha a senha nova.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.token,
                        onValueChange = vm::onToken,
                        label = { Text("Código de recuperação") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.newPassword,
                        onValueChange = vm::onNewPassword,
                        label = { Text("Nova senha (mín. ${ForgotPasswordViewModel.MIN_PASSWORD} caracteres)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = vm::onConfirmPassword,
                        label = { Text("Repita a nova senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("Salvar nova senha", state.working, vm::resetPassword)
                    TextButton(onClick = vm::backToRequest, modifier = Modifier.fillMaxWidth()) {
                        Text("Enviar o código de novo")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, working: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !working, modifier = Modifier.fillMaxWidth()) {
        if (working) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}
