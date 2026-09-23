package com.memoria.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.memoria.mobile.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.repoViewModel

private fun formatCpfDisplay(cpf: String): String {
    val d = cpf.filter { it.isDigit() }
    return if (d.length == 11) {
        "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9, 11)}"
    } else {
        cpf
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    /** True when the user was brought here by an expired token, not by logging out. */
    expiredNotice: Boolean = false,
) {
    val vm = repoViewModel { AuthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    var cpf by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showManualForm by rememberSaveable { mutableStateOf(false) }

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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        // Long-pressing the title is the way back to the server field. It has to
        // live on this screen — Settings is behind the login wall, so an address
        // saved wrong would otherwise be unrecoverable without clearing app data
        // — but a patient reading "Servidor: https://..." on the sign-in screen
        // only learns that something can break.
        // A logomarca MemórIA (`logo-memoria.png`) — o nome já vem no desenho.
        Image(
            painter = painterResource(R.drawable.logo_memoria),
            contentDescription = "MemórIA",
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showServer = !showServer },
                ),
        )
        Spacer(Modifier.height(4.dp))

        // Says why the user is here. A login screen with no explanation reads as
        // the app having lost the account, which it has not — only the token, and
        // the CPF and senha below are already filled in.
        if (expiredNotice) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Sua sessão expirou por segurança. Entre novamente para continuar — " +
                        "seus medicamentos e horários estão salvos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        // =========================================================================
        // MODO 1 CLIQUE PARA IDOSOS ("Clicou, entrou!")
        // Quando os dados já estão salvos e o usuário não pediu formulário manual
        // =========================================================================
        if (state.hasSavedCredentials && !showManualForm) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (state.savedName.isNotBlank()) "Olá, ${state.savedName}!" else "Bem-vindo(a) de volta!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "CPF: ${formatCpfDisplay(state.savedCpf)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Senha salva com segurança neste celular",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            // BOTÃO PRINCIPAL GIGANTE: 1 CLIQUE
            Button(
                onClick = { vm.loginWithSaved(onLoggedIn) },
                enabled = !state.loading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            "ENTRAR NO MEMÓRIA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            TextButton(
                onClick = { showManualForm = true },
                enabled = !state.loading,
            ) {
                Text("Entrar com outro CPF ou alterar senha", style = MaterialTheme.typography.bodyLarge)
            }

            TextButton(
                onClick = onRegister,
                enabled = !state.loading,
            ) {
                SignUpPrompt()
            }
        } else {
            // =========================================================================
            // FORMULÁRIO COMPLETO MANUAL (Primeiro acesso ou troca de conta)
            // =========================================================================
            if (state.hasSavedCredentials && showManualForm) {
                TextButton(
                    onClick = { showManualForm = false },
                    enabled = !state.loading,
                ) {
                    Text("← Voltar para entrar com conta salva")
                }
            }

            // O cabeçalho do `authModal` da web: ícone de utilizador + "Login".
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                // `.modal-header h2`: 32px (phone), bold, `--primary-color`.
                Text("Login", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = cpf,
                onValueChange = { cpf = it },
                label = { Text("CPF *") },
                placeholder = { Text("000.000.000-00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha *") },
                placeholder = { Text("Sua senha") },
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Entrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(onClick = onRegister, enabled = !state.loading) {
                SignUpPrompt()
            }

            TextButton(onClick = onForgotPassword, enabled = !state.loading) {
                Text(
                    "Esqueci minha senha",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showServer) {
            Text(
                "Endereço do servidor",
                style = MaterialTheme.typography.titleLarge,
            )
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

/** "Não tem conta? **Cadastre-se**" — o link do `authModal` da web. */
@Composable
private fun SignUpPrompt() {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append("Não tem conta? ")
            }
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            ) { append("Cadastre-se") }
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}
