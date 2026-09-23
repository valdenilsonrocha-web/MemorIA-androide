package com.memoria.mobile.ui.replenishment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.meds.MedicationIcon
import com.memoria.mobile.ui.meds.formatDateBr
import java.util.Locale

/** "Reposição" — the web `replenishmentPage`: when to buy each medication again. */
@Composable
fun ReplenishmentScreen(onBack: () -> Unit) {
    val vm = repoViewModel { ReplenishmentViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold { inner ->
        when {
            state.loading && state.items.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.items.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PageTopGuide(PageGuide.REPLENISHMENT) }

                // `#pageTopGuide` is prepended to the page: image, then `.page-header`.
                item {
                    InlinePageTitle(
                        "Reposição",
                        subtitle = "Veja quando comprar novamente cada medicamento com base no estoque atual.",
                        topTrim = 12.dp,
                    )
                }
                if (state.items.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                // A bare `<i class="fas fa-box-open">` (no `.empty-state-icon`): 1em.
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Nenhum medicamento ativo para calcular reposição.",
                                // `.empty-state p`: `--font-size-large` (20px).
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(state.items, key = { it.medication.id ?: it.medication.name }) { item ->
                    ReplenishmentCard(item)
                }
            }
        }
    }
}

/** `.replenishment-card.urgency-*` border and badge colours from main.css. */
private data class UrgencyColors(val border: Color, val badgeText: Color, val badgeBg: Color, val badgeBorder: Color)

private fun urgencyColors(urgency: Urgency): UrgencyColors = when (urgency) {
    Urgency.LOW -> UrgencyColors(Color(0xFF10B981), Color(0xFF065F46), Color(0x2410B981), Color(0x5910B981))
    Urgency.MEDIUM -> UrgencyColors(Color(0xFFF59E0B), Color(0xFF92400E), Color(0x29F59E0B), Color(0x66F59E0B))
    Urgency.HIGH -> UrgencyColors(Color(0xFFEF4444), Color(0xFF991B1B), Color(0x26EF4444), Color(0x73EF4444))
    Urgency.NONE -> UrgencyColors(Color(0xFF94A3B8), Color(0xFF334155), Color(0x2E94A3B8), Color(0x7394A3B8))
}

@Composable
private fun ReplenishmentCard(item: ReplenishmentItem) {
    val colors = urgencyColors(item.urgency)
    Card(
        Modifier.fillMaxWidth().alpha(if (item.urgency == Urgency.NONE) 0.92f else 1f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, colors.border),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // `.replenishment-card h3`: icon + name, urgency badge on the right.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MedicationIcon(item.look, size = 28.dp)
                    Text(
                        item.medication.name.ifBlank { "Medicamento" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.badgeBg,
                    border = BorderStroke(1.dp, colors.badgeBorder),
                ) {
                    Text(
                        "Urgência ${item.urgency.label}",
                        color = colors.badgeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            // `.replenishment-grid` — one column on a phone, gap 8px.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GridItem("Estoque atual", "${item.stock} doses")
                GridItem("Consumo estimado", "${format2(item.dailyDoses)} dose/dia")
                GridItem("Tipo de uso", if (item.continuousUse) "Uso contínuo" else "Tratamento temporário")
                GridItem("Duração do estoque", "${format1(item.daysRemaining)} dias")
                if (!item.continuousUse && item.treatmentEnd != null) {
                    GridItem("Fim do tratamento", formatDateBr(item.treatmentEnd))
                }
                GridItem("Fim do estoque", formatDateBr(item.stockEndDate))
                GridItem("Data sugerida de compra", item.purchaseText)
            }
        }
    }
}

/** `.replenishment-item`: grey box, small label over a bold value. */
@Composable
private fun GridItem(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF9FAFB))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
    }
}

// `toFixed()` on the web always uses a dot.
private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
