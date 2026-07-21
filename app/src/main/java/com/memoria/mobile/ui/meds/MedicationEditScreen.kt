package com.memoria.mobile.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.repoViewModel

private val frequencies = listOf(
    "daily" to "Diário",
    "alternate" to "Alternado",
    "weekly" to "Semanal",
)
private val weekDayLabels = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

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
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var newTime by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Novo medicamento" else "Editar medicamento") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text("Nome do medicamento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.dosage,
                onValueChange = vm::onDosage,
                label = { Text("Dosagem (ex.: 1 comprimido, 20mg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Frequência", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                frequencies.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.frequency == value,
                        onClick = { vm.onFrequency(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, frequencies.size),
                    ) { Text(label) }
                }
            }

            if (state.frequency == "weekly") {
                Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekDayLabels.forEachIndexed { day, label ->
                        FilterChip(
                            selected = state.weekDays.contains(day),
                            onClick = { vm.toggleWeekDay(day) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Text("Horários", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTime,
                    onValueChange = { newTime = it },
                    label = { Text("HH:MM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val normalized = normalizeTimeInput(newTime)
                    if (normalized != null) {
                        vm.addTime(normalized)
                        newTime = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar horário")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.times.forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTime(t) },
                        label = { Text(t) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remover")
                        },
                    )
                }
            }

            OutlinedTextField(
                value = state.stock,
                onValueChange = vm::onStock,
                label = { Text("Estoque (unidades)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.instructions,
                onValueChange = vm::onInstructions,
                label = { Text("Instruções (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Button(
                onClick = vm::save,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (state.isNew) "Cadastrar" else "Salvar")
            }
        }
    }
}

private fun normalizeTimeInput(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    val text = raw.trim()
    // Accept "8", "08", "0800", "8:00", "08:00"
    return when {
        text.matches(Regex("^\\d{1,2}:\\d{2}$")) -> padTime(text)
        digits.length in 3..4 -> {
            val padded = digits.padStart(4, '0')
            val h = padded.substring(0, 2).toIntOrNull() ?: return null
            val m = padded.substring(2, 4).toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) "%02d:%02d".format(h, m) else null
        }
        digits.length in 1..2 -> {
            val h = digits.toIntOrNull() ?: return null
            if (h in 0..23) "%02d:00".format(h) else null
        }
        else -> null
    }
}

private fun padTime(text: String): String? {
    val parts = text.split(":")
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) "%02d:%02d".format(h, m) else null
}
