package com.memoria.mobile.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.SafetyRecord
import com.memoria.mobile.data.local.VitalSignsRecord
import com.memoria.mobile.data.local.WellbeingRecord
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.Amber
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val GLUCOSE_CONTEXTS = listOf(
    "fasting" to "Em jejum",
    "postmeal" to "Pós-refeição",
    "random" to "Aleatório",
)

private val ACTIVITY_TYPES = listOf(
    "" to "Selecione",
    "caminhada" to "Caminhada",
    "corrida" to "Corrida",
    "ciclismo" to "Ciclismo",
    "natacao" to "Natação",
    "musculacao" to "Musculação",
    "yoga" to "Yoga / Pilates",
    "danca" to "Dança",
    "outro" to "Outro",
    "nenhuma" to "Nenhuma hoje",
)

private val SEX_OPTIONS = listOf(
    "auto" to "Usar cadastro",
    "female" to "Feminino",
    "male" to "Masculino",
    "other" to "Outro / não binário",
    "not_informed" to "Prefiro não informar",
)

private val CARE_LEVELS = listOf(
    Triple(1, "Nível 1", "Independente — alertas básicos"),
    Triple(2, "Nível 2", "Monitoramento — avisos ao cuidador"),
    Triple(3, "Nível 3", "Atenção especial — alertas frequentes"),
    Triple(4, "Nível 4", "Cuidado intensivo — notificações de emergência"),
)

private val SLEEP_FACES = listOf(1 to "😴", 2 to "😪", 3 to "😐", 4 to "😊", 5 to "😁")
private val MOOD_FACES = listOf(1 to "😡", 2 to "😞", 3 to "😐", 4 to "😊", 5 to "🤩")

/** "Saúde" — the web `healthPage`, its three panels kept as selectable sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(contentPadding: PaddingValues) {
    val vm = repoViewModel { HealthViewModel(it) }
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
        topBar = { TopAppBar(title = { Text("Saúde") }) },
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
            Text(
                "Monitore seus indicadores de saúde e bem-estar.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthSection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { vm.selectSection(section) },
                        label = { Text(section.label) },
                    )
                }
            }

            when (state.section) {
                HealthSection.VITAL_SIGNS -> VitalSignsPanel(state, vm)
                HealthSection.WELLBEING -> WellbeingPanel(state, vm)
                HealthSection.SAFETY -> SafetyPanel(state, vm)
            }
        }
    }
}

// ---- Vital signs ------------------------------------------------------------

@Composable
private fun VitalSignsPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionCard("Sinais Vitais", icon = Icons.Filled.MonitorHeart) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pressão Arterial (mmHg)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(state.systolic, vm::onSystolic, "Sistólica", Modifier.weight(1f))
                NumberField(state.diastolic, vm::onDiastolic, "Diastólica", Modifier.weight(1f))
            }
            StatusBadge(bloodPressureStatus(state.systolic.toIntOrNull(), state.diastolic.toIntOrNull()))

            NumberField(state.heartRate, vm::onHeartRate, "Frequência cardíaca (bpm)")
            StatusBadge(heartRateStatus(state.heartRate.toIntOrNull()))

            NumberField(state.spo2, vm::onSpo2, "Saturação de oxigênio SpO₂ (%)", decimal = true)
            StatusBadge(spo2Status(state.spo2.toDoubleBr()))

            NumberField(state.glucose, vm::onGlucose, "Glicose (mg/dL)", decimal = true)
            ChipRow(GLUCOSE_CONTEXTS, state.glucoseContext, vm::onGlucoseContext)
            StatusBadge(glucoseStatus(state.glucose.toDoubleBr(), state.glucoseContext))

            NumberField(state.hba1c, vm::onHba1c, "Hemoglobina glicada HbA1c (%)", decimal = true)
            StatusBadge(hba1cStatus(state.hba1c.toDoubleBr()))

            NumberField(state.temperature, vm::onTemperature, "Temperatura corporal (°C)", decimal = true)
            StatusBadge(temperatureStatus(state.temperature.toDoubleBr()))

            SaveButton("Salvar Sinais Vitais", vm::saveVitalSigns)
        }
    }

    HistoryCard("Últimas Medições", state.vitalSigns.isEmpty()) {
        state.vitalSigns.take(10).forEach { record -> VitalSignsHistoryRow(record) }
    }
}

@Composable
private fun VitalSignsHistoryRow(record: VitalSignsRecord) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            Schedule.localDateOf(record.date)?.format(DATE_BR) ?: record.date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (record.systolic != null && record.diastolic != null) {
            DetailRow("Pressão", "${record.systolic}/${record.diastolic} mmHg")
        }
        record.heartRate?.let { DetailRow("Freq. cardíaca", "$it bpm") }
        record.spo2?.let { DetailRow("SpO₂", "${decimal(it)}%") }
        record.glucose?.let {
            val context = GLUCOSE_CONTEXTS.firstOrNull { c -> c.first == record.glucoseContext }?.second
            DetailRow("Glicose", "${decimal(it)} mg/dL${context?.let { c -> " ($c)" }.orEmpty()}")
        }
        record.hba1c?.let { DetailRow("HbA1c", "${decimal(it)}%") }
        record.temperature?.let { DetailRow("Temperatura", "${decimal(it)} °C") }
    }
}

// ---- Wellbeing --------------------------------------------------------------

@Composable
private fun WellbeingPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionCard("Bem-estar", icon = Icons.Filled.Mood) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Qualidade do sono", style = MaterialTheme.typography.labelLarge)
            FaceRow(SLEEP_FACES, state.sleepQuality, vm::onSleepQuality)
            NumberField(state.sleepHours, vm::onSleepHours, "Horas dormidas (h)", decimal = true)

            NumberField(state.hydration, vm::onHydration, "Hidratação (L/dia)", decimal = true)
            StatusBadge(hydrationStatus(state.hydration.toDoubleBr()))

            Text("Humor e disposição", style = MaterialTheme.typography.labelLarge)
            FaceRow(MOOD_FACES, state.mood, vm::onMood)
            OutlinedTextField(
                value = state.moodNote,
                onValueChange = vm::onMoodNote,
                label = { Text("Observação (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Atividade física", style = MaterialTheme.typography.labelLarge)
            ChipRow(ACTIVITY_TYPES, state.activityType, vm::onActivityType)
            NumberField(state.activityDuration, vm::onActivityDuration, "Duração (min)")

            SaveButton("Salvar Bem-estar", vm::saveWellbeing)
        }
    }

    HistoryCard("Últimos Registros", state.wellbeing.isEmpty()) {
        state.wellbeing.take(10).forEach { record -> WellbeingHistoryRow(record) }
    }
}

@Composable
private fun WellbeingHistoryRow(record: WellbeingRecord) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            Schedule.localDateOf(record.date)?.format(DATE_BR) ?: record.date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        record.sleepQuality?.let {
            DetailRow("Sono", "${SLEEP_FACES.firstOrNull { f -> f.first == it }?.second.orEmpty()} ($it/5)")
        }
        record.sleepHours?.let { DetailRow("Horas dormidas", "${decimal(it)} h") }
        record.hydration?.let { DetailRow("Hidratação", "${decimal(it)} L") }
        record.mood?.let {
            DetailRow("Humor", "${MOOD_FACES.firstOrNull { f -> f.first == it }?.second.orEmpty()} ($it/5)")
        }
        if (record.moodNote.isNotBlank()) DetailRow("Observação", record.moodNote)
        if (record.activityType.isNotBlank()) {
            val label = ACTIVITY_TYPES.firstOrNull { it.first == record.activityType }?.second
                ?: record.activityType
            DetailRow("Atividade", label + (record.activityDuration?.let { " · $it min" } ?: ""))
        }
    }
}

// ---- Safety -----------------------------------------------------------------

@Composable
private fun SafetyPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionCard("Segurança", icon = Icons.Filled.HealthAndSafety) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(state.weight, vm::onWeight, "Peso (kg)", decimal = true)
            NumberField(state.height, vm::onHeight, "Altura (cm)", decimal = true)

            Text("Sexo", style = MaterialTheme.typography.labelLarge)
            ChipRow(SEX_OPTIONS, state.sex, vm::onSex)

            DetailRow("IMC", state.bmi?.let { decimal(it) } ?: "Informe peso e altura")
            StatusBadge(bmiStatus(state.bmi))

            Text("Escala de cuidados", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CARE_LEVELS.forEach { (level, title, description) ->
                    FilterChip(
                        selected = state.careLevel == level,
                        onClick = { vm.onCareLevel(level) },
                        label = {
                            Column {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(description, style = MaterialTheme.typography.bodyLarge)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SaveButton("Salvar Segurança", vm::saveSafety)
        }
    }

    SectionCard("Meus Médicos", icon = Icons.Filled.MedicalServices) {
        if (state.doctors.isEmpty()) {
            Text(
                "Nenhum médico cadastrado. Adicione em Mais > Meus Médicos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                state.doctors.forEach { doctor -> DetailRow(doctor.name, doctor.info) }
            }
        }
    }

    HistoryCard("Últimas Medições", state.safety.isEmpty()) {
        state.safety.take(10).forEach { record -> SafetyHistoryRow(record) }
    }
}

@Composable
private fun SafetyHistoryRow(record: SafetyRecord) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            Schedule.localDateOf(record.date)?.format(DATE_BR) ?: record.date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        record.weight?.let { DetailRow("Peso", "${decimal(it)} kg") }
        record.height?.let { DetailRow("Altura", "${decimal(it)} cm") }
        record.bmi?.let { DetailRow("IMC", decimal(it)) }
        record.careLevel?.let { level ->
            DetailRow("Escala de cuidados", CARE_LEVELS.firstOrNull { it.first == level }?.second ?: "$level")
        }
    }
}

// ---- Shared pieces ----------------------------------------------------------

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaceRow(faces: List<Pair<Int, String>>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        faces.forEach { (value, face) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(face, style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
}

@Composable
private fun StatusBadge(status: HealthStatus?) {
    if (status == null) return
    Text(
        status.text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = when (status.level) {
            HealthStatus.Level.NORMAL -> GreenOk
            HealthStatus.Level.ATTENTION -> Amber
            HealthStatus.Level.ALERT -> RedMiss
        },
    )
}

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Save, contentDescription = null)
        Text("  $label")
    }
}

@Composable
private fun HistoryCard(title: String, empty: Boolean, content: @Composable () -> Unit) {
    SectionCard(title, icon = Icons.Filled.History) {
        if (empty) {
            Text(
                "Nenhum registro ainda.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column { content() }
        }
    }
}

private fun String.toDoubleBr(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun decimal(value: Double): String = String.format(PT_BR, "%.1f", value)
