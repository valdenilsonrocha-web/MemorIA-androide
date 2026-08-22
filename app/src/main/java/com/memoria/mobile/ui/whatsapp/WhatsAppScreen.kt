package com.memoria.mobile.ui.whatsapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Caregiver
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.repoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val vm = repoViewModel { WhatsAppViewModel(it) }
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

    var cgName by remember { mutableStateOf("") }
    var cgPhone by remember { mutableStateOf("") }
    var cgRelation by remember { mutableStateOf("") }

    Scaffold(
        topBar = { BackTopBar("Avisos por WhatsApp", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.loading) {
            LoadingBox(Modifier.padding(inner))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "  Como funcionam os avisos",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(
                        "Os lembretes de cada dose chegam no WhatsApp do paciente. " +
                            "Se uma dose não for confirmada dentro da tolerância, o servidor " +
                            "avisa automaticamente os cuidadores — mesmo com o celular desligado.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Text("WhatsApp do paciente", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.patientPhone,
                onValueChange = vm::onPatientPhone,
                label = { Text("DDD + número (ex.: 11987654321)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.patientPhone.filter { it.isDigit() }.length >= 10) {
                OutlinedButton(
                    onClick = { openWhatsApp(context, state.patientPhone) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Text("  Abrir conversa no WhatsApp")
                }
            }

            Text("Cuidadores (recebem o alerta de dose perdida)", style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.isPremium) "Plano Premium: até 3 cuidadores."
                else "Plano gratuito: 1 cuidador. Premium permite até 3.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.caregivers.forEach { cg ->
                CaregiverCard(cg, onRemove = { vm.removeCaregiver(cg) })
            }

            if (state.canAddCaregiver) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Adicionar cuidador", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = cgName,
                            onValueChange = { cgName = it },
                            label = { Text("Nome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = cgPhone,
                            onValueChange = { cgPhone = it },
                            label = { Text("WhatsApp (DDD + número)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = cgRelation,
                            onValueChange = { cgRelation = it },
                            label = { Text("Parentesco (ex.: filho, esposa)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                vm.addCaregiver(cgName, cgPhone, cgRelation)
                                cgName = ""; cgPhone = ""; cgRelation = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Adicionar")
                        }
                    }
                }
            }

            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text("Salvar configuração")
            }
        }
    }
}

@Composable
private fun CaregiverCard(caregiver: Caregiver, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(caregiver.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(caregiver.phone)
                        if (caregiver.relation.isNotBlank()) append(" · ${caregiver.relation}")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover cuidador")
            }
        }
    }
}

private fun openWhatsApp(context: android.content.Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    val normalized = if (digits.startsWith("55")) digits else "55$digits"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized"))
    runCatching { context.startActivity(intent) }
}
