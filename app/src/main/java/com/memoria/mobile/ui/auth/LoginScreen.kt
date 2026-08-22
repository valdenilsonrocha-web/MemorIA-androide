package com.memoria.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.repoViewModel

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
) {
    val vm = repoViewModel { AuthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    var cpf by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.restoreCredentials() }

    // Fills the form once, the moment the encrypted store answers — and only
    // over empty fields, so a rotation mid-typing never clobbers the input.
    LaunchedEffect(state.credentialsRestored) {
        if (state.credentialsRestored) {
            if (cpf.isBlank()) cpf = state.savedCpf
            if (password.isBlank()) password = state.savedPassword
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("MemorIA 💊", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Lembretes de medicamentos com aviso ao cuidador pelo WhatsApp.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = cpf,
            onValueChange = { cpf = it },
            label = { Text("CPF") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Checked by default: the users this app is built for should not have to
        // retype a password to see today's doses.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setRememberMe(!state.rememberMe) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.rememberMe,
                onCheckedChange = vm::setRememberMe,
            )
            Text(
                "Salvar meu CPF e senha neste celular",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Button(
            onClick = { vm.login(cpf, password, onLoggedIn) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Entrar")
            }
        }

        TextButton(onClick = onRegister, enabled = !state.loading) {
            Text("Criar conta")
        }

        // The server address is editable HERE, not only in Settings: Settings sits
        // behind the login wall, so a wrong address used to be unrecoverable
        // without clearing app data.
        TextButton(onClick = { showServer = !showServer }) {
            Text(
                if (showServer) "Ocultar servidor" else "Servidor: ${state.baseUrl}",
                textAlign = TextAlign.Center,
            )
        }

        if (showServer) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::onBaseUrl,
                label = { Text("Endereço do servidor") },
                singleLine = true,
                enabled = !state.loading && !state.checking,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = vm::saveBaseUrl,
                    enabled = !state.loading && !state.checking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Salvar")
                }
                OutlinedButton(
                    onClick = vm::testConnection,
                    enabled = !state.loading && !state.checking,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Testar")
                    }
                }
            }
            TextButton(
                onClick = vm::restoreDefaultBaseUrl,
                enabled = !state.loading && !state.checking,
            ) {
                Text("Restaurar endereço padrão")
            }
            state.serverMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (state.serverOk == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
