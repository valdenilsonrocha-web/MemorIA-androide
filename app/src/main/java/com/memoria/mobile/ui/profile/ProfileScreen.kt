package com.memoria.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.EmergencyContactRecord
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.RedMiss

/** "Meu Perfil" — the web `profilePage`: personal data and emergency contacts. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = repoViewModel { ProfileViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        // With no profile loaded the error owns the screen (below); reporting it
        // here too would clear it and leave an empty form that silently
        // overwrites the real data on save.
        if (state.user != null) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Meu Perfil", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.loading && state.user == null) {
            LoadingBox(Modifier.padding(inner))
            return@Scaffold
        }
        if (state.user == null) {
            ErrorState(
                state.error ?: "Não foi possível carregar o seu perfil.",
                onRetry = vm::load,
                modifier = Modifier.padding(inner),
            )
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
                "Informações pessoais e contatos",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("Informações Pessoais", icon = Icons.Filled.AccountCircle) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(84.dp),
                        )
                    }
                    state.user?.cpfMasked?.takeIf { it.isNotBlank() }?.let {
                        DetailRow("CPF", it)
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::onName,
                        label = { Text("Nome completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = vm::onPhone,
                        label = { Text("Telefone") },
                        placeholder = { Text("(00) 00000-0000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = vm::onEmail,
                        label = { Text("E-mail") },
                        placeholder = { Text("seu@email.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.city,
                            onValueChange = vm::onCity,
                            label = { Text("Cidade") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = state.state,
                            onValueChange = vm::onState,
                            label = { Text("UF") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = vm::saveProfile,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                            )
                        }
                        Text("Salvar Informações")
                    }
                }
            }

            SectionCard("Contatos de Emergência", icon = Icons.Filled.Phone) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.contacts.isEmpty()) {
                        Text(
                            "Nenhum contato cadastrado.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.contacts.forEach { contact ->
                            ContactRow(
                                contact = contact,
                                onEdit = { vm.openContactDialog(contact) },
                                onDelete = { vm.deleteContact(contact) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.openContactDialog() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  Adicionar Contato")
                    }
                }
            }
        }
    }

    if (state.contactDialogOpen) {
        ContactDialog(
            existing = state.editingContact,
            onDismiss = vm::closeContactDialog,
            onSave = vm::saveContact,
        )
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContactRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        contact.relation.takeIf { it.isNotBlank() },
                        contact.phone.takeIf { it.isNotBlank() },
                        contact.email.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar contato") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover contato", tint = RedMiss)
            }
        }
    }
}

@Composable
private fun ContactDialog(
    existing: EmergencyContactRecord?,
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, phone: String, email: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var relation by remember { mutableStateOf(existing?.relation.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Editar contato" else "Novo contato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Parentesco") },
                    placeholder = { Text("Ex: Filha, Cuidador") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone / WhatsApp *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, relation, phone, email) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
