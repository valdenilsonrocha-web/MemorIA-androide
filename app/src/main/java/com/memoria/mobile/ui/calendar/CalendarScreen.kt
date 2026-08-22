package com.memoria.mobile.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.Snooze
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val WEEK_HEADERS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

/** "Calendário" — the web `calendarPage`: a month grid plus the selected day. */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val vm = repoViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { BackTopBar("Calendário", onBack) }) { inner ->
        when {
            state.loading && state.medications.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.medications.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Visualize seus medicamentos por dia",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::previousMonth) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                    }
                    Text(monthLabel(state.month.year, state.month.monthValue), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
                    }
                }

                MonthGrid(
                    firstOfMonth = state.month.atDay(1),
                    lengthOfMonth = state.month.lengthOfMonth(),
                    selected = state.selectedDay,
                    doseCount = vm::doseCount,
                    onSelect = vm::selectDay,
                )

                DayDetails(day = state.selectedDay, slots = vm.slotsFor(state.selectedDay))
            }
        }
    }
}

@Composable
private fun MonthGrid(
    firstOfMonth: LocalDate,
    lengthOfMonth: Int,
    selected: LocalDate,
    doseCount: (LocalDate) -> Int,
    onSelect: (LocalDate) -> Unit,
) {
    // Sunday-first, matching the web calendar and the backend's weekDays encoding.
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val cells = leadingBlanks + lengthOfMonth
    val rows = (cells + 6) / 7
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEK_HEADERS.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val dayOfMonth = index - leadingBlanks + 1
                    if (dayOfMonth in 1..lengthOfMonth) {
                        val date = firstOfMonth.withDayOfMonth(dayOfMonth)
                        DayCell(
                            date = date,
                            doses = doseCount(date),
                            isSelected = date == selected,
                            isToday = date == today,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    doses: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        doses > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        doses > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (isToday) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = foreground,
            )
            if (doses > 0) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(foreground))
            }
        }
    }
}

@Composable
private fun DayDetails(day: LocalDate, slots: List<com.memoria.mobile.ui.common.DoseSlot>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(dayLabel(day), style = MaterialTheme.typography.titleLarge)
            if (slots.isEmpty()) {
                Text(
                    "Nenhuma dose programada para este dia.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                slots.forEach { slot ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            slot.time,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(slot.medication.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                slot.medication.dosage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            statusLabel(slot.doneStatus),
                            style = MaterialTheme.typography.bodyLarge,
                            color = statusColor(slot.doneStatus),
                        )
                    }
                }
            }
        }
    }
}

private fun monthLabel(year: Int, month: Int): String {
    val name = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, PT_BR)
    return "${name.replaceFirstChar { it.uppercase(PT_BR) }} $year"
}

private fun dayLabel(day: LocalDate): String {
    val weekDay = day.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, PT_BR)
    val month = day.month.getDisplayName(TextStyle.FULL_STANDALONE, PT_BR)
    return "${weekDay.replaceFirstChar { it.uppercase(PT_BR) }}, ${day.dayOfMonth} de $month"
}

private fun statusLabel(status: String?): String = when (status) {
    "taken" -> "Tomada"
    "missed" -> "Perdida"
    "snoozed" -> "Adiada"
    else -> "Programada"
}

@Composable
private fun statusColor(status: String?): Color = when (status) {
    "taken" -> GreenOk
    "missed" -> RedMiss
    "snoozed" -> Snooze
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
