package com.memoria.mobile.ui.doctors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.CareContact
import com.memoria.mobile.data.local.MedicalConsultation
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.DateTimeField
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.formatLocalDateTime
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.RedMiss

/**
 * "Meus Médicos" — the web `myDoctorsPage`: the care network (doctor, hospital,
 * clinic, pharmacy, emergency contact) and scheduled consultations. Both live on
 * the phone; the backend has no table for either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDoctorsScreen(onBack: () -> Unit) {
    val vm = repoViewModel { MyDoctorsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = { BackTopBar("Meus Médicos", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Cadastre nome e contato de médico, hospital, clínica, farmácia e contato de emergência.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(
                if (state.editingContactId != null) "Editar Contato" else "Novo Contato",
                icon = Icons.Filled.ContactPhone,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tipo", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CARE_CONTACT_TYPES.forEach { (value, label) ->
                            FilterChip(
                                selected = state.contactType == value,
                                onClick = { vm.onContactType(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.contactName,
                        onValueChange = vm::onContactName,
                        label = { Text("Nome *") },
                        placeholder = { Text("Ex: Dr. João Silva") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.contactInfo,
                        onValueChange = vm::onContactInfo,
                        label = { Text("Contato *") },
                        placeholder = { Text("Telefone, WhatsApp ou e-mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.contactNotes,
                        onValueChange = vm::onContactNotes,
                        label = { Text("Observações") },
                        placeholder = { Text("Ex: CRM, especialidade, horário de atendimento") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = vm::clearContactForm, modifier = Modifier.weight(1f)) {
                            Text("Limpar")
                        }
                        Button(onClick = vm::saveContact, modifier = Modifier.weight(1f)) {
                            Text("Salvar Contato")
                        }
                    }
                }
            }

            SectionCard("Contatos Cadastrados", icon = Icons.Filled.MedicalServices) {
                if (state.contacts.isEmpty()) {
                    Text(
                        "Nenhum contato cadastrado ainda.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.contacts.forEach { contact ->
                            ContactRow(
                                contact = contact,
                                onEdit = { vm.editContact(contact) },
                                onDelete = { vm.deleteContact(contact) },
                            )
                        }
                    }
                }
            }

            SectionCard(
                if (state.editingConsultationId != null) "Editar Consulta" else "Nova Consulta Médica",
                icon = Icons.Filled.CalendarMonth,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeField(
                        value = state.consultationDateTime,
                        onChange = vm::onConsultationDateTime,
                        label = "Data e hora *",
                    )
                    OutlinedTextField(
                        value = state.consultationProfessional,
                        onValueChange = vm::onConsultationProfessional,
                        label = { Text("Profissional *") },
                        placeholder = { Text("Ex: Dra. Maria - Cardiologista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.consultationLocation,
                        onValueChange = vm::onConsultationLocation,
                        label = { Text("Local") },
                        placeholder = { Text("Ex: Hospital Central, Sala 204") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.consultationNotes,
                        onValueChange = vm::onConsultationNotes,
                        label = { Text("Anotações") },
                        placeholder = { Text("Ex: Levar exames, jejum, sintomas para relatar") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = vm::clearConsultationForm, modifier = Modifier.weight(1f)) {
                            Text("Limpar")
                        }
                        Button(onClick = vm::saveConsultation, modifier = Modifier.weight(1f)) {
                            Text("Salvar Consulta")
                        }
                    }
                }
            }

            SectionCard("Consultas Salvas", icon = Icons.Filled.EventAvailable) {
                if (state.consultations.isEmpty()) {
                    Text(
                        "Nenhuma consulta agendada.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.consultations.forEach { consultation ->
                            ConsultationRow(
                                consultation = consultation,
                                onEdit = { vm.editConsultation(consultation) },
                                onDelete = { vm.deleteConsultation(consultation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: CareContact, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    careContactTypeLabel(contact.type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    contact.info,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (contact.notes.isNotBlank()) {
                    Text(
                        contact.notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar contato") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover contato", tint = RedMiss)
            }
        }
    }
}

@Composable
private fun ConsultationRow(
    consultation: MedicalConsultation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatLocalDateTime(consultation.dateTime),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(consultation.professional, style = MaterialTheme.typography.bodyLarge)
                if (consultation.location.isNotBlank()) {
                    Text(
                        consultation.location,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (consultation.notes.isNotBlank()) {
                    Text(
                        consultation.notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar consulta") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover consulta", tint = RedMiss)
            }
        }
    }
}
