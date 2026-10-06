package com.memoria.mobile.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.reminders.ReminderTexts
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.meds.MedicationIcon
import java.time.LocalDate
import com.memoria.mobile.ui.common.FitText

/** `loadCalendar()` month names and day headers. */
private val MONTH_NAMES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)
private val MONTHS_LOWER = MONTH_NAMES.map { it.lowercase() }
private val WEEK_HEADERS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

private val Primary = Color(0xFF0060F8)      // --primary-color
private val Secondary = Color(0xFF66A9FF)    // --secondary-color
private val Danger = Color(0xFFEF4444)       // --danger-color
private val ConsultBlue = Color(0xFF0060F8)
private val BgSecondary = Color(0xFFF9FAFB)  // --bg-secondary

/** "Calendário" — the web `calendarPage`: a month grid plus the selected day. */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val vm = repoViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold { inner ->
        when {
            state.loading && state.history.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.history.isEmpty() && state.consultations.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // `#pageTopGuide` is prepended to `#calendarPage`: image, then `.page-header`.
                PageTopGuide(PageGuide.CALENDAR)
                InlinePageTitle("Calendário", subtitle = "Visualize seus medicamentos por dia", topTrim = 16.dp)

                // .calendar-controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::previousMonth) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                    }
                    Text(
                        "${MONTH_NAMES[state.month.monthValue - 1]} ${state.month.year}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
                    }
                }

                MonthGrid(
                    firstOfMonth = state.month.atDay(1),
                    lengthOfMonth = state.month.lengthOfMonth(),
                    selected = state.selectedDay,
                    indicator = vm::indicator,
                    hasConsultation = vm::hasConsultation,
                    onSelect = vm::selectDay,
                )

                state.selectedDay?.let { day -> DayDetails(day, vm) }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    firstOfMonth: LocalDate,
    lengthOfMonth: Int,
    selected: LocalDate?,
    indicator: (LocalDate) -> DayIndicator?,
    hasConsultation: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    // Sunday-first, matching the web calendar and the backend's weekDays encoding.
    val leadingDays = firstOfMonth.dayOfWeek.value % 7
    val cells = leadingDays + lengthOfMonth
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    val prevMonthLength = firstOfMonth.minusDays(1).dayOfMonth

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEK_HEADERS.forEach { label ->
                FitText(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
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
                    val dayOfMonth = index - leadingDays + 1
                    when {
                        dayOfMonth < 1 -> OtherMonthCell(prevMonthLength + dayOfMonth, Modifier.weight(1f))
                        dayOfMonth > lengthOfMonth -> OtherMonthCell(dayOfMonth - lengthOfMonth, Modifier.weight(1f))
                        else -> {
                            val date = firstOfMonth.withDayOfMonth(dayOfMonth)
                            DayCell(
                                date = date,
                                indicator = indicator(date),
                                consultation = hasConsultation(date),
                                isSelected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `.calendar-day.other-month`: the neighbouring month's number at 30% opacity. */
@Composable
private fun OtherMonthCell(day: Int, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(0.3f)
            .clip(RoundedCornerShape(16.dp))
            .background(BgSecondary),
        contentAlignment = Alignment.Center,
    ) {
        Text("$day", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1F2937))
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    indicator: DayIndicator?,
    consultation: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp) // --border-radius
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (isSelected) Primary else BgSecondary)
            .border(2.dp, if (isToday) Primary else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color(0xFF1F2937),
            )
            if (indicator != null || consultation) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    indicator?.let {
                        Dot(if (it == DayIndicator.GREEN) Secondary else Danger)
                    }
                    if (consultation) Dot(ConsultBlue)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(color))
}

/** `loadDayDetails()`. */
@Composable
private fun DayDetails(day: LocalDate, vm: CalendarViewModel) {
    val history = vm.historyOn(day)
    val consultations = vm.consultationsOn(day)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (history.isEmpty() && consultations.isEmpty()) {
                Text("Nenhum registro neste dia", style = MaterialTheme.typography.titleLarge)
                return@Column
            }
            Text(
                "Registros de ${day.dayOfMonth} de ${MONTHS_LOWER[day.monthValue - 1]} de ${day.year}",
                style = MaterialTheme.typography.titleLarge,
            )
            if (history.isNotEmpty()) SectionLabel("Medicamentos")
            history.forEach { entry ->
                val taken = entry.status == "taken"
                DayItem(stripe = if (taken) Secondary else Danger) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MedicationIcon(vm.lookFor(entry), size = 28.dp)
                                Text(entry.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("${entry.dosage} - ${entry.scheduleTime}", style = MaterialTheme.typography.bodyLarge)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Icon(
                                if (taken) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = if (taken) Secondary else Danger,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(if (taken) "Tomado" else "Perdido", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (consultations.isNotEmpty()) SectionLabel("Consultas agendadas")
            consultations.forEach { c ->
                DayItem(stripe = ConsultBlue, background = Color(0xFFEFF6FF)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                c.professional.ifBlank { "Consulta médica" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Horário: ${consultationWhen(c.dateTime)}", style = MaterialTheme.typography.bodyLarge)
                            if (c.location.isNotBlank()) Text("Local: ${c.location}", style = MaterialTheme.typography.bodyLarge)
                            if (c.notes.isNotBlank()) Text("Anotações: ${c.notes}", style = MaterialTheme.typography.bodyLarge)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = ConsultBlue, modifier = Modifier.size(24.dp))
                            Text("Consulta", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** app.js `formatConsultationDateTime()` (its fallback differs from the notification one). */
private fun consultationWhen(raw: String): String {
    val text = ReminderTexts.formatConsultationDateTime(raw)
    return if (text == "horário não informado") "Data e hora não informadas" else text
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1F2937), // .day-details-section-title: --text-primary
    )
}

/** `.day-medication-item`: light box with a 4dp coloured left border. */
@Composable
private fun DayItem(stripe: Color, background: Color = BgSecondary, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
        Box(Modifier.weight(1f).padding(16.dp)) { content() }
    }
}
