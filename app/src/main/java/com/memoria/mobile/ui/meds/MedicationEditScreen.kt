@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.memoria.mobile.ui.meds

import android.app.TimePickerDialog
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.MemoriaToast
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.repoViewModel
import java.time.LocalTime
import com.memoria.mobile.ui.common.FitText
import androidx.compose.ui.text.style.TextAlign

/** `#frequency` options, same values and labels as the web select. */
private val frequencies = listOf(
    "daily" to "Todos os dias",
    "alternate" to "Dias alternados",
    "weekly" to "Dias específicos da semana",
)

/** `#weekDaysGroup` checkboxes: value 0..6 = Domingo..Sábado. */
private val weekDayNames = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

/** "Adicionar / Editar Medicamento" — the web `addMedicationPage` form. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationEditScreen(
    medicationId: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val vm = repoViewModel { MedicationEditViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(medicationId) { vm.start(medicationId) }
    MedicationFormEffects(state, vm, onSaved = onDone)

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTopGuide(PageGuide.MEDICATIONS)
            // `#pageTopGuide` is prepended to the page: image, then `<h2>`.
            InlinePageTitle(if (state.isNew) "Adicionar Medicamento" else "Editar Medicamento", topTrim = 16.dp)
            MedicationFormBody(state = state, vm = vm, onCancel = onCancel)
        }
    }
}

/**
 * The web's toasts around `#medicationForm`: the save result (then [onSaved]),
 * the library pick, and every error (`showToast('❌ …')` / the plan limit).
 */
@Composable
internal fun MedicationFormEffects(state: MedEditState, vm: MedicationEditViewModel, onSaved: () -> Unit) {
    LaunchedEffect(state.saved) {
        if (state.saved) {
            state.savedMessage?.let { MemoriaToast.show(it) }
            onSaved()
        }
    }
    LaunchedEffect(state.libraryMessage) {
        state.libraryMessage?.let {
            MemoriaToast.show(it)
            vm.consumeLibraryMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            MemoriaToast.show(com.memoria.mobile.ui.common.errorToast(it))
            vm.clearError()
        }
    }
}

/** Every field of `#medicationForm` plus `.form-actions` (Cancelar | Salvar Medicamento). */
@Composable
internal fun MedicationFormBody(state: MedEditState, vm: MedicationEditViewModel, onCancel: () -> Unit) {
    MedicationFormFields(state = state, vm = vm)

    // .form-actions: Cancelar | Salvar Medicamento
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { FitText("Cancelar") }
        Button(
            onClick = vm::save,
            enabled = !state.saving && !state.loading,
            modifier = Modifier.weight(1f),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            FitText(
                if (state.isNew) "Salvar Medicamento" else "Atualizar Medicamento",
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Every field of `#medicationForm`, in the web's order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationFormFields(state: MedEditState, vm: MedicationEditViewModel) {
    val context = LocalContext.current
    var showSuggestions by remember { mutableStateOf(false) }
    var iconMenuOpen by remember { mutableStateOf(false) }
    var contactTarget by remember { mutableStateOf<String?>(null) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val name = uri?.let { contactName(context, it) }
        if (uri != null && name.isNullOrBlank()) {
            MemoriaToast.show("⚠️ Não foi possível acessar a agenda.")
        }
        if (!name.isNullOrBlank()) {
            when (contactTarget) {
                "prescribingDoctor" -> vm.onPrescribingDoctor(name)
                "dispensingPharmacy" -> vm.onDispensingPharmacy(name)
            }
        }
        contactTarget = null
    }

    // ---- Nome do Medicamento * + biblioteca (autocomplete) ----
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { vm.onName(it); showSuggestions = true },
            label = { Text("Nome do Medicamento *") },
            placeholder = { Text("Ex: Paracetamol") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val query = state.name.trim()
        if (showSuggestions && query.length >= 2) {
            MedicationSuggestions(
                query = query,
                onPick = { med, dosage ->
                    vm.applyFromLibrary(med, dosage)
                    showSuggestions = false
                },
            )
        }
    }

    OutlinedTextField(
        value = state.dosage,
        onValueChange = vm::onDosage,
        label = { Text("Dosagem *") },
        placeholder = { Text("Ex: 500mg") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // ---- Ícone do Medicamento ----
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Ícone do Medicamento")
        // `.medication-icon-menu-toggle.is-open`: filled primary while the menu is open.
        if (iconMenuOpen) {
            Button(onClick = { iconMenuOpen = false }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Palette, contentDescription = null)
                Text("  Escolher ícone, formato e cor")
            }
        } else {
            OutlinedButton(onClick = { iconMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Palette, contentDescription = null)
                Text("  Escolher ícone, formato e cor")
            }
        }
        if (iconMenuOpen) MedicationCustomizationMenu(state, vm)
    }

    // ---- Frequência * ----
    SelectField(
        label = "Frequência *",
        value = state.frequency,
        options = frequencies,
        onSelect = vm::onFrequency,
    )

    if (state.frequency == "weekly") {
        Column {
            FieldLabel("Dias da Semana")
            weekDayNames.forEachIndexed { day, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleWeekDay(day) },
                ) {
                    Checkbox(checked = state.weekDays.contains(day), onCheckedChange = { vm.toggleWeekDay(day) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // ---- Horários * ----
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Horários *")
        state.times.forEachIndexed { index, time ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeSlotField(
                    value = time,
                    label = if (index == 0) "Horário 1" else "Horário adicional",
                    onChange = { vm.setTime(index, it) },
                    modifier = Modifier.weight(1f),
                )
                if (index > 0) {
                    IconButton(onClick = { vm.removeTimeSlot(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover horário")
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::addTimeSlot) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" Adicionar Horário")
        }
    }

    OutlinedTextField(
        value = state.instructions,
        onValueChange = vm::onInstructions,
        label = { Text("Instruções Especiais") },
        placeholder = { Text("Ex: Tomar com alimentos") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.stock,
        onValueChange = vm::onStock,
        label = { Text("Quantidade em Estoque") },
        placeholder = { Text("Ex: 30") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    // ---- Fornecedor / Farmácia (Premium) ----
    // Esta é a "farmácia de preferência": é para ESTE contacto que o servidor
    // manda o WhatsApp quando o stock fica crítico.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "  Fornecedor / Farmácia (Premium)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Avisaremos esta farmácia pelo WhatsApp caso o estoque acabe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.supplierName,
            onValueChange = vm::onSupplierName,
            placeholder = { Text("Nome da Farmácia (Ex: Farmácia Central)") },
            singleLine = true,
            enabled = state.isPremium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.supplierPhone,
            onValueChange = vm::onSupplierPhone,
            placeholder = { Text("WhatsApp (Ex: 11999999999)") },
            singleLine = true,
            enabled = state.isPremium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isPremium) {
            // .premium-lock-overlay
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "  Exclusivo Premium",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- Duração do Tratamento / uso contínuo ----
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = state.treatmentDurationDays,
            onValueChange = vm::onTreatmentDurationDays,
            label = { Text("Duração do Tratamento (dias)") },
            placeholder = { Text("Ex: 7") },
            singleLine = true,
            enabled = !state.continuousUse,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Informe quantos dias o tratamento vai durar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { vm.onContinuousUse(!state.continuousUse) },
    ) {
        Checkbox(checked = state.continuousUse, onCheckedChange = vm::onContinuousUse)
        Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(20.dp))
        Text("  Remédio de uso contínuo", style = MaterialTheme.typography.bodyLarge)
    }

    // ---- Médico Prescritor / Farmácia Dispensadora (com "Agenda") ----
    AgendaField(
        icon = { Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Médico Prescritor",
        value = state.prescribingDoctor,
        placeholder = "Ex: Dr. João Silva — Cardiologista",
        onChange = vm::onPrescribingDoctor,
        onAgenda = {
            contactTarget = "prescribingDoctor"
            runCatching { pickContact.launch(null) }
        },
    )
    AgendaField(
        icon = { Icon(Icons.Filled.LocalPharmacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        label = "Farmácia Dispensadora",
        value = state.dispensingPharmacy,
        placeholder = "Ex: Farmácia Central — Rua das Flores",
        onChange = vm::onDispensingPharmacy,
        onAgenda = {
            contactTarget = "dispensingPharmacy"
            runCatching { pickContact.launch(null) }
        },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

/** `#medNameSuggestions`: up to 8 cards from the library, or the "not found" line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationSuggestions(query: String, onPick: (LibraryMedication, String) -> Unit) {
    val results = remember(query) { MedicationLibrary.search(query).take(8) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (results.isEmpty()) {
                Text(
                    "Remédio não encontrado na biblioteca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            results.forEach { med ->
                Surface(
                    onClick = { onPick(med, med.commonDosages.firstOrNull().orEmpty()) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(med.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            MedicationLibrary.categoryLabel(med.category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            med.commonDosages.forEach { dosage ->
                                Surface(
                                    onClick = { onPick(med, dosage) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        dosage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        if (med.instructions.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    " ${med.instructions}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** `#medicationCustomizationMenu`: form tiles, shape/colour rows and the preview. */
@Composable
private fun MedicationCustomizationMenu(state: MedEditState, vm: MedicationEditViewModel) {
    val def = medFormDef(state.medicationForm)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // #medIconPicker
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MED_FORM_DEFS.forEach { form ->
                    val active = form.value == def.value
                    Surface(
                        onClick = { vm.onMedicationForm(form.value) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .width(76.dp)
                            .semantics { contentDescription = form.label },
                    ) {
                        Column(
                            Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // `.med-icon-picker .medication-image-icon img`: 36px.
                            MedicationFormGlyph(
                                form,
                                MaterialTheme.colorScheme.primary,
                                if (medicationFormImage(form.value) != null) 36.dp else 28.dp,
                            )
                            Text(form.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            // #iconColorPanel
            if (def.hasColor || def.hasShape || def.hasDualColor) {
                if (def.hasShape) {
                    Text("Formato:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PILL_SHAPES.forEach { (shape, title) ->
                            val active = state.pillShape == shape
                            Surface(
                                onClick = { vm.onPillShape(shape) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    if (active) 2.dp else 1.dp,
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier.semantics { contentDescription = title },
                            ) {
                                Box(Modifier.padding(8.dp).height(24.dp).width(40.dp), contentAlignment = Alignment.Center) {
                                    PillShapeIcon(shape, hexColor(state.pillColor), size = 18.dp)
                                }
                            }
                        }
                    }
                }
                if (def.hasDualColor) {
                    Text("Cor 1 (tampa):", style = MaterialTheme.typography.labelLarge)
                    ColorSwatches(state.capsuleColor1, vm::onCapsuleColor1)
                    Text("Cor 2 (corpo):", style = MaterialTheme.typography.labelLarge)
                    ColorSwatches(state.capsuleColor2, vm::onCapsuleColor2)
                } else if (def.hasColor) {
                    Text("Cor:", style = MaterialTheme.typography.labelLarge)
                    ColorSwatches(state.pillColor, vm::onPillColor)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pré-visualização:", style = MaterialTheme.typography.labelLarge)
                MedicationIcon(state.look, size = 48.dp)
            }
        }
    }
}

@Composable
private fun ColorSwatches(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PILL_COLORS.forEach { (hex, label) ->
            val active = sanitizePillColor(selected) == hex
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(hexColor(hex))
                    .border(
                        if (active) 3.dp else 1.dp,
                        if (active) MaterialTheme.colorScheme.primary else Color(0x33000000),
                        CircleShape,
                    )
                    .clickable { onSelect(hex) }
                    .semantics { contentDescription = label },
            )
        }
    }
}

/** A native `<select>`: read-only field + dropdown. */
@Composable
private fun SelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(v); expanded = false })
            }
        }
    }
}

/** `<input type="time">`: opens the platform clock dialog. */
@Composable
private fun TimeSlotField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val open = {
        val base = runCatching { LocalTime.parse(value) }.getOrNull() ?: LocalTime.now()
        TimePickerDialog(
            context,
            { _, hour, minute -> onChange("%02d:%02d".format(hour, minute)) },
            base.hour,
            base.minute,
            true,
        ).show()
    }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("--:--") },
            trailingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = open)
                .semantics { contentDescription = label },
        )
    }
}

/** `.input-with-agenda`: text field plus the "Agenda" contact picker button. */
@Composable
private fun AgendaField(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onAgenda: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text("  $label", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onAgenda,
                modifier = Modifier.semantics { contentDescription = "Importar da agenda do telefone" },
            ) {
                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Agenda")
            }
        }
    }
}

/** Display name of a contact returned by the system picker. */
private fun contactName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()
