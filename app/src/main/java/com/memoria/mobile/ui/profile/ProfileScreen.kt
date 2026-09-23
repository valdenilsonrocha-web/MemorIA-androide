package com.memoria.mobile.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.EmergencyContactRecord
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.MemoriaToast
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.systemViewModel

/** Relation options of the web contact modal (`contactRelation`). */
private val RELATIONS = listOf(
    "filho" to "Filho(a)",
    "conjuge" to "Cônjuge",
    "irmao" to "Irmão(ã)",
    "cuidador" to "Cuidador(a)",
    "amigo" to "Amigo(a)",
    "outro" to "Outro",
)

/** "Tratamento" options of the web contact modal (`contactTitle`). */
private val TITLES = listOf("auto" to "Automático", "sra" to "Sra.", "sr" to "Sr.")

/** Label for a stored relation; free text from older builds is shown as typed. */
private fun relationLabel(relation: String): String {
    val key = java.text.Normalizer.normalize(relation.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return RELATIONS.firstOrNull { it.first == key }?.second ?: relation
}

/** "Meu Perfil" — the web `profilePage`: personal data and emergency contacts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = systemViewModel { repo, app, _ -> ProfileViewModel(repo, app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<EmergencyContactRecord?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setPhoto(uri)
    }

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
        snackbarHost = { SnackbarHost(snackbar) { com.memoria.mobile.ui.common.MemoriaSnackbar(it) } },
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

            SectionCard("Informações Pessoais", icon = Icons.Filled.Person) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfilePhoto(state.photoPath, state.photoVersion)
                        OutlinedButton(onClick = { pickPhoto.launch("image/*") }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Text("  Alterar Foto")
                        }
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::onName,
                        label = { Text("Nome Completo") },
                        placeholder = { Text("Seu nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.age,
                        onValueChange = vm::onAge,
                        label = { Text("Idade") },
                        placeholder = { Text("Sua idade") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        label = { Text("Email") },
                        placeholder = { Text("seu@email.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${state.contacts.size}") }
                            append(
                                if (state.user?.isPremium == true) {
                                    " contato(s) cadastrado(s). Premium ativo: você pode cadastrar até 3 " +
                                        "contatos de emergência."
                                } else {
                                    " contato(s) cadastrado(s). Plano gratuito permite apenas 1 contato. " +
                                        "Ative o Premium para cadastrar até 3 contatos."
                                },
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        // `.contacts-plan-message.is-locked` is #8a6500 on the free plan.
                        color = if (state.user?.isPremium == true) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color(0xFF8A6500)
                        },
                    )
                    if (state.contacts.isEmpty()) {
                        Text(
                            "Nenhum contato cadastrado ainda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.contacts.forEach { contact ->
                            ContactRow(
                                contact = contact,
                                onWhatsApp = {
                                    vm.contactWhatsAppUrl(contact)?.let { url ->
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } catch (_: ActivityNotFoundException) {
                                            MemoriaToast.show("❌ Não foi possível abrir o WhatsApp.")
                                        }
                                    }
                                },
                                onEdit = { vm.openContactDialog(contact) },
                                onDelete = { deleting = contact },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.openContactDialog() },
                            enabled = state.canAddContact,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Adicionar Contato")
                        }
                        OutlinedButton(
                            onClick = vm::openPrimaryContactEditor,
                            enabled = state.contacts.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("  Alterar Contato")
                        }
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

    deleting?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text("Tem certeza que deseja excluir este contato?") },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.deleteContact(contact) }) {
                    Text("Excluir", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfilePhoto(path: String?, version: Int) {
    val bitmap = remember(path, version) {
        path?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    // `.profile-photo`: 150px circle, 4px primary border, --bg-secondary behind;
    // the placeholder is `fa-user` at 80px in --text-secondary.
    Box(
        Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(150.dp).clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactRow(
    contact: EmergencyContactRecord,
    onWhatsApp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // `.contact-item`: --bg-secondary panel with the actions on the right.
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .semantics { contentDescription = "Clique para editar este contato" },
            ) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${relationLabel(contact.relation)} - ${contact.phone}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (contact.email.isNotBlank()) {
                    Text(
                        contact.email,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                // `.contact-action-whatsapp`: #f0fdf4 / #bbf7d0 border / #166534 text.
                ContactAction(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "WhatsApp",
                    title = "Enviar WhatsApp",
                    container = Color(0xFFF0FDF4),
                    borderColor = Color(0xFFBBF7D0),
                    content = Color(0xFF166534),
                    onClick = onWhatsApp,
                )
                ContactAction(Icons.Filled.Edit, "Editar", "Editar contato", onClick = onEdit)
                ContactAction(Icons.Filled.Delete, "Excluir", "Excluir", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ContactDialog(
    existing: EmergencyContactRecord?,
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, phone: String, email: String, title: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var relation by remember { mutableStateOf(existing?.relation.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title ?: "auto") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (existing != null) Icons.Filled.Edit else Icons.Filled.Phone, contentDescription = null)
                Text(if (existing != null) "  Editar Contato de Emergência" else "  Adicionar Contato de Emergência")
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome *") },
                    placeholder = { Text("Nome do contato") },
                    singleLine = true,
                )
                DialogSelect(
                    label = "Relação *",
                    options = listOf("" to "Selecione...") + RELATIONS +
                        // Keeps a free-text relation from older builds selectable.
                        listOfNotNull(
                            relation.takeIf { r -> r.isNotBlank() && RELATIONS.none { it.first == r } }
                                ?.let { it to relationLabel(it) },
                        ),
                    selected = relation,
                    onSelect = { relation = it },
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone *") },
                    placeholder = { Text("(00) 00000-0000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (opcional)") },
                    placeholder = { Text("email@exemplo.com (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                DialogSelect(
                    label = "Tratamento",
                    options = TITLES,
                    selected = title,
                    onSelect = { title = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, relation, phone, email, title) }) {
                Text(if (existing != null) "Salvar Alterações" else "Salvar Contato")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** The web's `<select>` inside the contact modal. */
@Composable
private fun DialogSelect(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Box {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true }
                .semantics { contentDescription = label },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

/** The web's `.contact-action` button: white, #d1d5db border, 12px radius, bold 14px label. */
@Composable
private fun ContactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    title: String,
    container: Color = Color.White,
    borderColor: Color = Color(0xFFD1D5DB),
    content: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.semantics { contentDescription = title },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("  $label", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
