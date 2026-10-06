@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.memoria.mobile.ui.meds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.memoria.mobile.data.local.MedicationExtras
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.theme.RedMiss
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.memoria.mobile.ui.common.FitText
import com.memoria.mobile.ui.theme.GreenTake
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size

/** Phone-side data of one medication (look + extras), both optional. */
data class MedLocal(val look: MedicationLook? = null, val extras: MedicationExtras? = null)

/**
 * `reminderService.getMedicationStatus()`: taken when logged as taken today,
 * `missed` once the time has passed, `pending` within 15 minutes of it, else
 * `upcoming`.
 */
fun doseStatus(slot: DoseSlot, now: LocalTime = LocalTime.now()): String {
    if (slot.doneStatus == "taken") return "taken"
    val current = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    if (slot.time < current) return "missed"
    val slotMinutes = runCatching { LocalTime.parse(slot.time) }.getOrNull()
        ?.let { it.hour * 60 + it.minute } ?: return "upcoming"
    val nowMinutes = now.hour * 60 + now.minute
    return if (abs(slotMinutes - nowMinutes) <= 15) "pending" else "upcoming"
}

/** `.medication-status.status-*` pill. */
@Composable
fun StatusBadge(text: String, status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = doseStatusColors(status)
    Surface(color = bg, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Text(
            text,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** `.medication-form-badge`. */
@Composable
private fun FormBadge(form: String?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            medicationFormLabel(form),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label ") }
            append(value)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * `.medication-card`: white, 2px `--border-color` border, 24px radius, with the
 * 4px coloured bar of `.medication-card.<status>::before` across the TOP edge
 * (grey `--border-color` when the card has no status).
 */
@Composable
private fun StripedCard(stripe: Color?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, CardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(stripe ?: CardBorder))
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) { content() }
        }
    }
}

private val CardBorder = Color(0xFFE5E7EB) // --border-color

/** `createMedicationCard()` — one dose in "Próximos Horários". */
@Composable
fun MedicationScheduleCard(
    slot: DoseSlot,
    status: String,
    local: MedLocal,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onTake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val med = slot.medication
    val summary = treatmentSummary(med, local.extras?.continuousUse == true, local.extras?.treatmentDurationDays)
    StripedCard(
        stripe = doseStatusStripe(status, MaterialTheme.colorScheme.primary),
        modifier = modifier.alpha(if (status == "taken") 0.75f else 1f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MedicationIcon(local.look)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slot.time,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FormBadge(local.look?.medicationForm)
                }
            }
            Text(med.dosage, style = MaterialTheme.typography.bodyLarge)
            summary?.let { LabeledLine("Tratamento:", it) }
            med.instructions?.takeIf { it.isNotBlank() }?.let { Instructions(it) }
            StatusBadge(doseStatusLabel(status), status)
            // O servidor aceita nomes até 255 caracteres: os botões ficam numa
            // linha própria para um nome longo não os empurrar para fora do ecrã.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    FitText("Editar")
                }
                if (status != "taken") {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        FitText("Soneca")
                    }
                    Button(
                        onClick = onTake,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenTake, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        FitText("Tomei")
                    }
                }
            }
        }
    }
}

/** `createMedicationInfoCard()` — one entry of "Todos os Medicamentos". */
@Composable
fun MedicationInfoCard(
    med: Medication,
    local: MedLocal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val times = med.times.filter { it.isNotBlank() }
    val stock = med.stock
    val summary = treatmentSummary(med, local.extras?.continuousUse == true, local.extras?.treatmentDurationDays)
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MedicationIcon(local.look)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(med.dosage, style = MaterialTheme.typography.bodyLarge)
                    FormBadge(local.look?.medicationForm)
                }
            }
            Column(Modifier.padding(vertical = 4.dp)) {
                LabeledLine("Horários:", if (times.isNotEmpty()) times.joinToString(", ") else "Não definido")
                LabeledLine("Frequência:", frequencyLabel(med.frequency))
                LabeledLine("Estoque:", "$stock doses")
                summary?.let { LabeledLine("Tratamento:", it) }
            }
            med.instructions?.takeIf { it.isNotBlank() }?.let { Instructions(it) }
            when {
                stock == 0 -> StatusBadge("Estoque zerado!", "missed")
                stock < 7 -> StatusBadge("Estoque baixo: $stock doses", "missed")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    FitText("Editar")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    FitText("Excluir")
                }
            }
        }
    }
    StripedCard(
        stripe = null,
        modifier = if (onOpen != null) modifier.clickable(onClick = onOpen) else modifier,
    ) { body() }
}

/** `.medication-instructions`: secondary colour, italic. */
@Composable
fun Instructions(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The web's eight "Estoque zerado" nudges (`createMedicationInfoCard()`), one
 * picked at random and shown as a warning toast.
 */
val ZERO_STOCK_MESSAGES: List<String> = listOf(
    "Atenção! Seu estoque deste remédio acabou. Reponha o quanto antes para não interromper seu tratamento.",
    "Opa! Você ficou sem este medicamento. Programe-se para comprar mais e manter sua saúde em dia.",
    "Seu remédio terminou! Não se esqueça de repor para continuar o tratamento corretamente.",
    "Estoque zerado! Que tal adicionar este medicamento à sua lista de compras?",
    "Você usou a última dose deste remédio. Reponha logo para não correr riscos.",
    "Fique atento: o medicamento acabou. Repor agora é cuidar do seu bem-estar!",
    "Opa! Acabaram as doses deste remédio. Não deixe para depois, reponha já!",
    "Para não esquecer: seu estoque deste medicamento chegou ao fim. Programe a reposição!",
)

/** Text of the web's delete `confirm()`. */
const val DELETE_CONFIRM_TEXT = "Tem certeza que deseja excluir este medicamento?"
