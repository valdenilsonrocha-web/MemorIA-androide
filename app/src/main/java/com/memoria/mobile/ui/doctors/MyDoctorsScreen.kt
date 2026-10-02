package com.memoria.mobile.ui.doctors


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.memoria.mobile.ui.theme.TextPrimary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.CareContact
import com.memoria.mobile.data.local.MedicalConsultation
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.DateTimeField
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.systemViewModel
import com.memoria.mobile.ui.health.SelectField
import com.memoria.mobile.ui.theme.RedMiss
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `formatConsultationDateTime()`: `dateStyle: 'short', timeStyle: 'short'` em pt-BR. */
private val CONSULTATION_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")

/**
 * "Meus Médicos" — the web `myDoctorsPage`: the care network (doctor, hospital,
 * clinic, pharmacy, emergency contact) and scheduled consultations. Both live on
 * the phone; the backend has no table for either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDoctorsScreen(onBack: () -> Unit) {
    val vm = systemViewModel { repo, _, scheduler -> MyDoctorsViewModel(repo, scheduler) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var contactToDelete by remember { mutableStateOf<CareContact?>(null) }
    var consultationToDelete by remember { mutableStateOf<MedicalConsultation?>(null) }

    // O botão "Agenda" da web (Contact Picker API). Escolher um número pela
    // tabela de telefones concede leitura só dessa linha — não pede permissão.
    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val picked = readPickedContact(context, uri)
        vm.fillFromAgenda(picked?.first, picked?.second)
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(com.memoria.mobile.ui.common.errorToast(it)); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { MemoriaSnackbar(it) } },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTopGuide(PageGuide.DOCTORS)
            InlinePageTitle(
                "Meus Médicos",
                subtitle = "Cadastre nome e contato de médico, hospital, clínica, farmácia e contato de emergência.",
                topTrim = 16.dp,
            )

            SectionCard("Novo Contato", icon = Icons.Filled.Contacts) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectField(
                        CARE_CONTACT_TYPES,
                        state.contactType,
                        vm::onContactType,
                        label = "Tipo *",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.contactName,
                            onValueChange = vm::onContactName,
                            label = { Text("Nome *") },
                            placeholder = { Text("Ex: Dr. João Silva") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                )
                                runCatching { pickContact.launch(intent) }
                                    .onFailure { vm.fillFromAgenda(null, null) }
                            },
                            // `.btn-agenda-picker`: borda 1.5px e texto na cor
                            // primária, raio 16px, 13px/600.
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.semantics {
                                contentDescription = "Importar da agenda do telefone"
                            },
                        ) {
                            Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Agenda", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
                    // `.contacts-actions-row` no telemóvel: "Limpar" (min-width 100%) numa
                    // linha e "Salvar" na seguinte, com gap de 10px.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = vm::clearContactForm, modifier = Modifier.fillMaxWidth()) {
                            Text("Limpar")
                        }
                        Button(onClick = vm::saveContact) {
                            Text(if (state.editingContactId != null) "Salvar Alterações" else "Salvar Contato")
                        }
                    }
                }
            }

            SectionCard("Contatos Cadastrados", icon = Icons.Filled.MedicalInformation) {
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
                                onDelete = { contactToDelete = contact },
                            )
                        }
                    }
                }
            }

            SectionCard("Nova Consulta Médica", icon = Icons.Filled.EventAvailable) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = vm::clearConsultationForm, modifier = Modifier.fillMaxWidth()) {
                            Text("Limpar")
                        }
                        Button(onClick = vm::saveConsultation) {
                            Text(if (state.editingConsultationId != null) "Salvar Alterações" else "Salvar Consulta")
                        }
                    }
                }
            }

            SectionCard("Consultas Salvas", icon = Icons.AutoMirrored.Filled.Assignment) {
                if (state.consultations.isEmpty()) {
                    Text(
                        "Nenhuma consulta salva ainda.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.consultations.forEach { consultation ->
                            ConsultationRow(
                                consultation = consultation,
                                onEdit = { vm.editConsultation(consultation) },
                                onDelete = { consultationToDelete = consultation },
                            )
                        }
                    }
                }
            }
        }
    }

    contactToDelete?.let { contact ->
        ConfirmDialog(
            text = "Deseja excluir o contato ${contact.name}?",
            onConfirm = {
                contactToDelete = null
                vm.deleteContact(contact)
            },
            onDismiss = { contactToDelete = null },
        )
    }
    consultationToDelete?.let { consultation ->
        ConfirmDialog(
            text = "Deseja excluir a consulta com ${consultation.professional}?",
            onConfirm = {
                consultationToDelete = null
                vm.deleteConsultation(consultation)
            },
            onDismiss = { consultationToDelete = null },
        )
    }
}

/** O `confirm()` do navegador. */
@Composable
private fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** `.contact-item`: nome, "Tipo - contato", observações e as acções. */
@Composable
private fun ContactRow(contact: CareContact, onEdit: () -> Unit, onDelete: () -> Unit) {
    ContactItemBox {
        Column(Modifier.fillMaxWidth()) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${careContactTypeLabel(contact.type)} - ${contact.info}",
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
            ItemActions(
                editDescription = "Editar contato",
                deleteDescription = "Excluir",
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ConsultationRow(
    consultation: MedicalConsultation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ContactItemBox {
        Column(Modifier.fillMaxWidth()) {
            Text(
                consultation.professional.ifBlank { "Profissional não informado" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Consulta: ${formatConsultationDateTime(consultation.dateTime)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (consultation.location.isNotBlank()) {
                Text(
                    "Local: ${consultation.location}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (consultation.notes.isNotBlank()) {
                Text(
                    "Anotações: ${consultation.notes}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ItemActions(
                editDescription = "Editar consulta",
                deleteDescription = "Excluir consulta",
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

/** `.contact-actions`: "Editar" e "Excluir", cada um com o seu ícone. */
@Composable
private fun ItemActions(
    editDescription: String,
    deleteDescription: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Em ecrã estreito (`max-width: 640px`) as acções ocupam a linha inteira,
    // alinhadas à esquerda, com gap de 8px.
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContactActionButton(Icons.Filled.Edit, "Editar", editDescription, onEdit)
        ContactActionButton(Icons.Filled.Delete, "Excluir", deleteDescription, onDelete)
    }
}

/**
 * `.contact-action`: fundo branco, borda 1px #d1d5db, raio 12px, texto
 * `--text-primary` (o "Excluir" não é vermelho na web), ícone 16px + rótulo
 * 13px/600, padding 9×10px.
 */
@Composable
private fun ContactActionButton(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  $label", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** `.contact-item`: fundo `--bg-secondary`, raio 16px, padding 20px. */
@Composable
private fun ContactItemBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        content()
    }
}

private fun formatConsultationDateTime(raw: String): String =
    runCatching { LocalDateTime.parse(raw).format(CONSULTATION_DATE) }
        .getOrDefault("Data e hora não informadas")

/** Nome e número da linha escolhida na agenda, ou null se não for legível. */
private fun readPickedContact(context: Context, uri: Uri): Pair<String?, String?>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(0) to cursor.getString(1)
    }
}.getOrNull()
