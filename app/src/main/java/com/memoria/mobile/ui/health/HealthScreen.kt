package com.memoria.mobile.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.SafetyRecord
import com.memoria.mobile.data.local.VitalSignsRecord
import com.memoria.mobile.data.local.WellbeingRecord
import com.memoria.mobile.ui.common.DateTimeField
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.BorderSubtle
import java.util.Locale

private val GLUCOSE_CONTEXTS = listOf(
    "fasting" to "Em jejum",
    "postmeal" to "Pós-refeição",
    "random" to "Aleatório",
)

private val ACTIVITY_TYPES = listOf(
    "" to "Selecione...",
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

/** Cores de `.hm-icon-*` no `main.css`: fundo claro e ícone saturado. */
private enum class Tone(val bg: Color, val fg: Color) {
    RED(Color(0xFFFEE2E2), Color(0xFFDC2626)),
    BLUE(Color(0xFFDBEAFE), Color(0xFF0060F8)),
    ORANGE(Color(0xFFFFEDD5), Color(0xFFEA580C)),
    PURPLE(Color(0xFFE0EDFF), Color(0xFF0053D6)),
    YELLOW(Color(0xFFFEF9C3), Color(0xFFCA8A04)),
    GREEN(Color(0xFFDCFCE7), Color(0xFF16A34A)),
}

/** `.hm-care-*`: um cartão por nível, com a cor a subir com a gravidade. */
private data class CareLevel(
    val level: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
)

private val CARE_LEVELS = listOf(
    CareLevel(1, "Nível 1", "Independente", Icons.Filled.CheckCircle, Color(0xFFDCFCE7), Color(0xFF166534)),
    CareLevel(2, "Nível 2", "Precisa de acompanhamento", Icons.Filled.Error, Color(0xFFFEF9C3), Color(0xFF854D0E)),
    CareLevel(3, "Nível 3", "Precisa de atenção especial", Icons.Filled.Warning, Color(0xFFFFEDD5), Color(0xFF9A3412)),
    CareLevel(4, "Nível 4", "Precisa de cuidado intensivo", Icons.Filled.Cancel, Color(0xFFFEE2E2), Color(0xFF991B1B)),
)

private val SLEEP_FACES = listOf(
    Triple(1, "😴", "Muito ruim"), Triple(2, "😪", "Ruim"), Triple(3, "😐", "Regular"),
    Triple(4, "😊", "Boa"), Triple(5, "😁", "Excelente"),
)
private val MOOD_FACES = listOf(
    Triple(1, "😡", "Muito mal"), Triple(2, "😞", "Mal"), Triple(3, "😐", "Normal"),
    Triple(4, "😊", "Bem"), Triple(5, "🤩", "Ótimo"),
)

/**
 * "Saúde" — the web `healthPage`: as três secções (Sinais Vitais, Indicadores
 * de Bem-Estar, Segurança e Prevenção) empilhadas numa só página, como lá.
 */
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
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // `#pageTopGuide` is prepended to `#healthPage`: the image first,
            // then `.page-header` (h2 com o ícone + `.subtitle`).
            PageTopGuide(PageGuide.DOCTORS)
            InlinePageTitle(
                "Saúde",
                subtitle = "Monitore seus indicadores de saúde e bem-estar.",
                topTrim = 12.dp,
            )

            VitalSignsPanel(state, vm)
            HorizontalDivider(thickness = 1.5.dp)
            WellbeingPanel(state, vm)
            HorizontalDivider(thickness = 1.5.dp)
            SafetyPanel(state, vm)
        }
    }
}

// ---- Sinais Vitais ------------------------------------------------------------

@Composable
private fun VitalSignsPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionTitle(Icons.Filled.MonitorHeart, "Sinais Vitais")

    MetricCard(Icons.Filled.CalendarMonth, Tone.BLUE, "Data da atualização") {
        DateTimeField(
            value = state.measurementAt,
            onChange = vm::onMeasurementAt,
            label = "Data e hora",
        )
    }

    MetricCard(Icons.Filled.MonitorHeart, Tone.RED, "Pressão Arterial") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberInput(state.systolic, vm::onSystolic, "Sistólica", Modifier.weight(1f))
            Text("/", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NumberInput(state.diastolic, vm::onDiastolic, "Diastólica", Modifier.weight(1f))
            UnitLabel("mmHg")
        }
        StatusBadge(bloodPressureStatus(state.systolic.toDoubleOrNullBr(), state.diastolic.toDoubleOrNullBr()))
    }

    MetricCard(Icons.Filled.Favorite, Tone.RED, "Frequência Cardíaca") {
        SingleInput(state.heartRate, vm::onHeartRate, "Ex: 72", "bpm")
        StatusBadge(heartRateStatus(state.heartRate.toDoubleOrNullBr()))
    }

    MetricCard(Icons.Filled.Air, Tone.BLUE, "Saturação de Oxigênio (SpO₂)") {
        SingleInput(state.spo2, vm::onSpo2, "Ex: 98", "%", decimal = true)
        StatusBadge(spo2Status(state.spo2.toDoubleOrNullBr()))
    }

    MetricCard(Icons.Filled.WaterDrop, Tone.ORANGE, "Glicose") {
        SingleInput(state.glucose, vm::onGlucose, "Ex: 95", "mg/dL", decimal = true)
        SelectField(
            GLUCOSE_CONTEXTS,
            state.glucoseContext,
            vm::onGlucoseContext,
            accessibilityLabel = "Contexto da medição de glicose",
        )
        StatusBadge(glucoseStatus(state.glucose.toDoubleOrNullBr()))
    }

    MetricCard(Icons.Filled.Science, Tone.PURPLE, "Hemoglobina Glicada (HbA1c)") {
        SingleInput(state.hba1c, vm::onHba1c, "Ex: 5.7", "%", decimal = true)
        StatusBadge(hba1cStatus(state.hba1c.toDoubleOrNullBr()))
    }

    MetricCard(Icons.Filled.Thermostat, Tone.YELLOW, "Temperatura Corporal") {
        SingleInput(state.temperature, vm::onTemperature, "Ex: 36.5", "°C", decimal = true)
        StatusBadge(temperatureStatus(state.temperature.toDoubleOrNullBr()))
    }

    SaveButton("Salvar Sinais Vitais", vm::saveVitalSigns)

    HistorySection(state.vitalSigns.take(10)) { VitalSignsHistoryItem(it) }
}

@Composable
private fun VitalSignsHistoryItem(r: VitalSignsRecord) {
    val parts = buildList {
        if (r.systolic != null && r.systolic != 0 && r.diastolic != null && r.diastolic != 0) {
            add("PA: ${r.systolic}/${r.diastolic} mmHg")
        }
        r.heartRate?.takeIf { it != 0 }?.let { add("FC: $it bpm") }
        r.spo2?.takeIf { it != 0.0 }?.let { add("SpO₂: ${jsNumber(it)}%") }
        r.glucose?.takeIf { it != 0.0 }?.let { add("Glicose: ${jsNumber(it)} mg/dL") }
        r.hba1c?.takeIf { it != 0.0 }?.let { add("HbA1c: ${jsNumber(it)}%") }
        r.temperature?.takeIf { it != 0.0 }?.let { add("Temp: ${jsNumber(it)}°C") }
    }
    HistoryItem(r.date, parts)
}

// ---- Indicadores de Bem-Estar ---------------------------------------------------

@Composable
private fun WellbeingPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionTitle(Icons.Filled.SentimentSatisfied, "Indicadores de Bem-Estar")

    MetricCard(Icons.Filled.Bedtime, Tone.BLUE, "Qualidade do Sono") {
        RatingRow(SLEEP_FACES, state.sleepQuality, vm::onSleepQuality)
        SingleInput(state.sleepHours, vm::onSleepHours, "Horas dormidas", "h", decimal = true)
    }

    MetricCard(Icons.Filled.LocalDrink, Tone.BLUE, "Hidratação") {
        SingleInput(state.hydration, vm::onHydration, "Ex: 2.0", "L/dia", decimal = true)
    }

    MetricCard(Icons.Filled.SentimentVerySatisfied, Tone.YELLOW, "Humor e Disposição") {
        RatingRow(MOOD_FACES, state.mood, vm::onMood)
        OutlinedTextField(
            value = state.moodNote,
            onValueChange = vm::onMoodNote,
            placeholder = { Text("Observação (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    MetricCard(Icons.AutoMirrored.Filled.DirectionsRun, Tone.GREEN, "Atividade Física") {
        SelectField(
            ACTIVITY_TYPES,
            state.activityType,
            vm::onActivityType,
            accessibilityLabel = "Tipo de atividade",
        )
        SingleInput(state.activityDuration, vm::onActivityDuration, "Duração", "min")
    }

    SaveButton("Salvar Bem-Estar", vm::saveWellbeing)

    HistorySection(state.wellbeing.take(10)) { WellbeingHistoryItem(it) }
}

@Composable
private fun WellbeingHistoryItem(r: WellbeingRecord) {
    val parts = buildList {
        r.sleepQuality?.takeIf { it != 0 }?.let { q ->
            add("Sono: ${SLEEP_FACES.firstOrNull { it.first == q }?.second ?: q}")
        }
        r.sleepHours?.takeIf { it != 0.0 }?.let { add("${jsNumber(it)}h") }
        r.hydration?.takeIf { it != 0.0 }?.let { add("Água: ${jsNumber(it)}L") }
        r.mood?.takeIf { it != 0 }?.let { m ->
            add("Humor: ${MOOD_FACES.firstOrNull { it.first == m }?.second ?: m}")
        }
        if (r.activityType.isNotBlank()) {
            val duration = r.activityDuration?.takeIf { it != 0 }?.let { " ${it}min" }.orEmpty()
            add("Atividade: ${r.activityType}$duration")
        }
    }
    HistoryItem(r.date, parts)
}

// ---- Segurança e Prevenção --------------------------------------------------------

@Composable
private fun SafetyPanel(state: HealthUiState, vm: HealthViewModel) {
    SectionTitle(Icons.Filled.Shield, "Segurança e Prevenção")

    MetricCard(Icons.Filled.MonitorWeight, Tone.GREEN, "Peso") {
        SingleInput(state.weight, vm::onWeight, "Ex: 72.5", "kg", decimal = true)
    }

    MetricCard(Icons.Filled.Straighten, Tone.BLUE, "Altura") {
        SingleInput(state.height, vm::onHeight, "Ex: 170", "cm", decimal = true)
    }

    MetricCard(Icons.Filled.Transgender, Tone.PURPLE, "Sexo") {
        SelectField(SEX_OPTIONS, state.sex, vm::onSex, accessibilityLabel = "Sexo para cálculo do IMC")
    }

    MetricCard(Icons.Filled.Calculate, Tone.PURPLE, "IMC (Índice de Massa Corporal)") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = state.bmi?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text("Calculado automaticamente") },
                modifier = Modifier.weight(1f),
            )
            UnitLabel("kg/m²")
        }
        StatusBadge(bmiStatus(state.bmi, state.resolvedSex))
        Hint("O IMC é calculado automaticamente com base no peso, altura e sexo informado.")
    }

    MetricCard(Icons.Filled.Notifications, Tone.RED, "Escala de Cuidados") {
        Hint("Registre o nível de atenção de que a pessoa precisa.")
        CARE_LEVELS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { level ->
                    CareLevelCard(
                        level = level,
                        selected = state.careLevel == level.level,
                        onClick = { vm.onCareLevel(level.level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    SaveButton("Salvar Segurança", vm::saveSafety)

    HistorySection(state.safety.take(10)) { SafetyHistoryItem(it) }
}

@Composable
private fun CareLevelCard(level: CareLevel, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(level.bg)
            .border(2.5.dp, if (selected) level.fg else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${level.title}: ${level.description}" }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(level.icon, contentDescription = null, tint = level.fg, modifier = Modifier.size(22.dp))
        Text(level.title, color = level.fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            level.description,
            color = level.fg.copy(alpha = 0.75f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SafetyHistoryItem(r: SafetyRecord) {
    val parts = buildList {
        r.weight?.takeIf { it != 0.0 }?.let { add("Peso: ${jsNumber(it)} kg") }
        r.height?.takeIf { it != 0.0 }?.let { add("Altura: ${jsNumber(it)} cm") }
        if (r.sex.isNotBlank() && r.sex != "not_informed" && r.sex != "auto") {
            val label = when (r.sex) {
                "female" -> "Feminino"
                "male" -> "Masculino"
                else -> "Outro"
            }
            add("Sexo: $label")
        }
        r.bmi?.takeIf { it != 0.0 }?.let { add("IMC: ${jsNumber(it)}") }
        r.careLevel?.takeIf { it != 0 }?.let { add("Cuidados: Nível $it") }
    }
    HistoryItem(r.date, parts)
}

// ---- Peças partilhadas ---------------------------------------------------------------

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "  $title",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** `.health-metric-card`: ícone colorido à esquerda, rótulo e campos à direita. */
@Composable
private fun MetricCard(
    icon: ImageVector,
    tone: Tone,
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // `--border-light` (#f3f4f6): a página Saúde não usa a borda do tema.
        border = BorderStroke(1.5.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                content()
            }
        }
    }
}

@Composable
private fun NumberInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

/** Um campo numérico com a unidade à direita — `.hm-input-row` da web. */
@Composable
private fun SingleInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    unit: String,
    decimal: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberInput(value, onChange, placeholder, Modifier.weight(1f), decimal)
        UnitLabel(unit)
    }
}

@Composable
private fun UnitLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 11.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Os botões de emoji de sono e humor — `.hm-rating-btn`. */
@Composable
private fun RatingRow(faces: List<Triple<Int, String, String>>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        faces.forEach { (value, face, description) ->
            val isSelected = selected == value
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        // `.is-selected { background: rgba(16,185,129,.12) }`; o
                        // normal é `--bg-secondary`.
                        if (isSelected) Color(0x1F10B981)
                        else MaterialTheme.colorScheme.background,
                    )
                    .border(
                        2.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(value) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(face, fontSize = 22.sp)
            }
        }
    }
}

/** `.hm-status`: pílula verde, amarela ou vermelha. */
@Composable
private fun StatusBadge(status: HealthStatus?) {
    if (status == null) return
    val (bg, fg) = when (status.level) {
        HealthStatus.Level.NORMAL -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        HealthStatus.Level.ATTENTION -> Color(0xFFFEF9C3) to Color(0xFF92400E)
        HealthStatus.Level.ALERT -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    Text(
        status.text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text("  $label")
        }
    }
}

/** "Últimas Medições" — o mesmo título e o mesmo vazio nas três secções. */
@Composable
private fun <T> HistorySection(records: List<T>, item: @Composable (T) -> kotlin.Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("  Últimas Medições", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (records.isEmpty()) {
            Text(
                "Nenhuma medição registrada ainda.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        } else {
            records.forEach { item(it) }
        }
    }
}

/** `.health-history-item`: data pequena por cima, valores unidos por " • ". */
@Composable
private fun HistoryItem(date: String, parts: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                formatShortDateTime(date),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                parts.joinToString(" • ").ifEmpty { "—" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
