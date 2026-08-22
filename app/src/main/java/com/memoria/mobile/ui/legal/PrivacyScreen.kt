package com.memoria.mobile.ui.legal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.systemViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** The word the user must type to confirm erasure. */
private const val DELETE_CONFIRMATION = "APAGAR"

/**
 * "Privacidade e dados" — the LGPD rights the web exposes in Settings, gathered
 * into one screen: export everything, revoke consent, erase the account.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onOpenPolicy: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    val vm = systemViewModel { repo, app, _ -> PrivacyViewModel(repo, app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }

    // Storage Access Framework: the user picks the destination, so the app needs
    // no storage permission and the file lands somewhere they can actually find.
    val saveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) vm.writeExportTo(uri) else vm.discardExport()
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.pendingExport) {
        if (state.pendingExport != null) saveExport.launch("memoria-meus-dados.json")
    }
    LaunchedEffect(state.accountDeleted) { if (state.accountDeleted) onAccountDeleted() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = { BackTopBar("Privacidade e dados", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.loading && state.user == null) {
            LoadingBox(Modifier.padding(inner))
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "A LGPD dá a você o direito de saber o que guardamos, levar os seus dados " +
                    "embora, retirar o consentimento e apagar tudo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("Política de privacidade", icon = Icons.Filled.Policy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quais dados tratamos, para quê e com que base legal.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenPolicy, modifier = Modifier.fillMaxWidth()) {
                        Text("Ler a política")
                    }
                }
            }

            SectionCard("Exportar os meus dados", icon = Icons.Filled.Download) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Baixa um arquivo com o seu cadastro, medicamentos, histórico de doses e " +
                            "receitas — tudo o que o servidor guarda sobre você.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = vm::requestExport,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.working) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text("Exportar em JSON")
                    }
                }
            }

            SectionCard("Consentimento", icon = Icons.Filled.Gavel) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val granted = state.consentGranted
                    Text(
                        if (granted) {
                            val date = Schedule.localDateOf(state.user?.consentDate)?.format(DATE_BR)
                            "Consentimento concedido" + (date?.let { " em $it" } ?: "") + "."
                        } else {
                            "Consentimento revogado."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (granted) GreenOk else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Revogar não apaga a conta — para isso use a opção abaixo. Sem " +
                            "consentimento, recursos que dependem dele podem parar de funcionar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { if (granted) confirmRevoke = true else vm.setConsent(true) },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (granted) "Revogar consentimento" else "Conceder consentimento")
                    }
                }
            }

            SectionCard("Apagar a minha conta", icon = Icons.Filled.DeleteForever) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Remove definitivamente a conta, os medicamentos, o histórico e as receitas " +
                            "do servidor, e limpa o que ficou guardado neste celular. Não dá para desfazer.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Exporte os seus dados antes, se quiser guardá-los.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { confirmDelete = true },
                        enabled = !state.working,
                        colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Text("  Apagar conta e dados")
                    }
                }
            }
        }
    }

    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text("Revogar consentimento") },
            text = {
                Text(
                    "Vamos registrar que você retirou o consentimento. A sua conta e os seus " +
                        "dados continuam existindo — para apagá-los use “Apagar conta e dados”.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRevoke = false; vm.setConsent(false) }) {
                    Text("Revogar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) { Text("Cancelar") }
            },
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.deleteAccount() },
        )
    }
}

/**
 * Erasure is irreversible, so a stray tap must not be enough: the user types the
 * word before the button turns on.
 */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val armed = typed.trim().equals(DELETE_CONFIRMATION, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar a conta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Isto apaga a sua conta, os medicamentos, o histórico de doses e as receitas. " +
                        "Não há como recuperar depois.",
                )
                Text("Para confirmar, digite $DELETE_CONFIRMATION:")
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(DELETE_CONFIRMATION) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text("Apagar tudo", color = if (armed) RedMiss else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
