package com.memoria.mobile.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.systemViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = systemViewModel { repo, app, scheduler -> SettingsViewModel(repo, app, scheduler) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = { BackTopBar("Configurações", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Conta", style = MaterialTheme.typography.titleLarge)
                    val user = state.user
                    if (user != null) {
                        Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (user.cpfMasked.isNotBlank()) Text("CPF: ${user.cpfMasked}", style = MaterialTheme.typography.bodyLarge)
                        if (user.email.isNotBlank()) Text(user.email, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (user.isPremium) "Plano: Premium" else "Plano: Gratuito",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (state.loading) {
                        Text("Carregando...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Servidor (backend)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "URL pública da API MemorIA. O app adiciona /api automaticamente.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = vm::onBaseUrl,
                        label = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = vm::saveBaseUrl, modifier = Modifier.fillMaxWidth()) {
                        Text("Salvar servidor")
                    }
                    OutlinedButton(
                        onClick = vm::testConnection,
                        enabled = !state.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        Text("Testar conexão")
                    }
                    when (state.reachable) {
                        true -> Text("Servidor online ✅", color = GreenOk, style = MaterialTheme.typography.bodyLarge)
                        false -> Text("Sem resposta do servidor ❌", color = RedMiss, style = MaterialTheme.typography.bodyLarge)
                        null -> {}
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lembretes", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            !state.notificationsAllowed ->
                                "As notificações estão desligadas — nenhum lembrete de dose vai aparecer."
                            !state.exactAlarmsAllowed ->
                                "Os lembretes estão ligados, mas sem permissão de alarme exato podem " +
                                    "atrasar alguns minutos."
                            else ->
                                "Lembretes ligados. O app avisa na hora de cada dose, mesmo com a tela bloqueada."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.notificationsAllowed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        "Soneca do botão “Adiar”: ${state.snoozeMinutes} minutos",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            FilterChip(
                                selected = state.snoozeMinutes == minutes,
                                onClick = { vm.setSnoozeMinutes(minutes) },
                                label = { Text("$minutes min") },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = vm::sendTestNotification,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Text("  Testar lembrete agora")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Abrir notificações do sistema")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Acesso", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.hasSavedCredentials) {
                            "O CPF e a senha estão salvos neste celular, protegidos pelo cofre do " +
                                "Android. A tela de login já vem preenchida."
                        } else {
                            "O CPF e a senha não estão salvos. Marque \"Salvar meu CPF e senha\" " +
                                "na tela de login para não precisar digitar de novo."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Sits next to logout on purpose: handing the phone to someone
                    // else is exactly when the saved password should go.
                    OutlinedButton(
                        onClick = vm::forgetCredentials,
                        enabled = state.hasSavedCredentials,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.LockReset, contentDescription = null)
                        Text("  Esquecer CPF e senha salvos")
                    }
                }
            }

            Button(
                onClick = { vm.logout(onLoggedOut) },
                colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Sair da conta")
            }

            Text(
                "MemorIA Mobile 1.0.0 · não substitui orientação médica.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
