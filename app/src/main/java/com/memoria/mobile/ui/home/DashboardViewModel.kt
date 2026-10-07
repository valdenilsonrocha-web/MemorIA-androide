package com.memoria.mobile.ui.home

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.reminders.DoseActions
import com.memoria.mobile.reminders.DoseAlarm
import com.memoria.mobile.reminders.ReminderScheduler
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.meds.MedLocal
import com.memoria.mobile.ui.meds.ZERO_STOCK_MESSAGES
import com.memoria.mobile.ui.meds.doseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val medications: List<Medication> = emptyList(),
    val todaySlots: List<DoseSlot> = emptyList(),
    val local: Map<String, MedLocal> = emptyMap(),
    /** `computeAdherenceStreak()`: consecutive days, up to today, with a dose taken. */
    val streak: Int = 0,
    /** `upsell_dismissed_date` — the daily Premium card was closed today. */
    val upsellDismissedToday: Boolean = false,
) {
    /** Status of each of today's doses, as `reminderService.getTodayMedications()`. */
    val statuses: List<Pair<DoseSlot, String>> get() = todaySlots.map { it to doseStatus(it) }

    val todayCount: Int get() = todaySlots.size
    val completedCount: Int get() = statuses.count { it.second == "taken" }

    /** `stats.pending + stats.upcoming` — overdue ones are not counted, as on the web. */
    val pendingCount: Int get() = statuses.count { it.second == "pending" || it.second == "upcoming" }

    /** "Próximos Horários": upcoming, pending and missed doses, in time order. */
    val upcoming: List<Pair<DoseSlot, String>>
        get() = statuses.filter { it.second != "taken" }

    val isPremium: Boolean get() = user?.isPremium == true

    fun localFor(med: Medication): MedLocal = med.id?.let { local[it] } ?: MedLocal()
}

class DashboardViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val medsResult = repo.medications()
            DoseActions.flushPending(repo)
            // Doses answered offline still count as taken until the queue is sent.
            val history = (repo.history(limit = 200) as? ApiResult.Ok)?.value.orEmpty() +
                DoseActions.pendingHistory(repo)
            val user = (repo.me() as? ApiResult.Ok)?.value
            val dismissed = runCatching { repo.local.uiSetting(UPSELL_KEY) }.getOrNull()

            when (medsResult) {
                is ApiResult.Ok -> {
                    val today = LocalDate.now()
                    val active = medsResult.value.filter { it.active }
                    val looks = repo.local.medicationLooks().associateBy { it.medicationId }
                    val extras = repo.local.medicationExtras().associateBy { it.medicationId }
                    val local = active.mapNotNull { m ->
                        m.id?.let { id -> id to MedLocal(looks[id], extras[id]) }
                    }.toMap()
                    // The web pops one of its "estoque zerado" nudges per empty medication.
                    val zeroStock = active.any { it.stock == 0 }
                    _state.value = DashboardUiState(
                        loading = false,
                        user = user,
                        medications = active,
                        local = local,
                        todaySlots = Schedule.withHistory(
                            Schedule.slotsFor(active, today),
                            history,
                            today,
                        ),
                        streak = streak(history),
                        upsellDismissedToday = dismissed == today.toString(),
                        message = if (zeroStock) "💡 ${ZERO_STOCK_MESSAGES.random()}" else null,
                    )
                }
                is ApiResult.Err ->
                    _state.update { it.copy(loading = false, error = medsResult.message) }
            }
        }
    }

    /** "Tomei" — `takeMedication()`. */
    fun take(slot: DoseSlot) {
        val id = slot.medication.id ?: return
        // Leaves "Próximos Horários" at once; the reload below confirms it, and a
        // failure puts it back.
        markSlot(slot, "taken")
        viewModelScope.launch {
            val r = DoseActions.markTaken(
                repo = repo,
                medicationId = id,
                medicationName = slot.medication.name,
                dosage = slot.medication.dosage,
                time = slot.time,
            )
            if (r is ApiResult.Ok) {
                _state.update { it.copy(message = "✅ Medicamento registrado como tomado!") }
                load()
            } else {
                markSlot(slot, slot.doneStatus)
                _state.update { it.copy(message = "❌ Erro ao registrar medicamento.") }
            }
        }
    }

    private fun markSlot(slot: DoseSlot, status: String?) = _state.update { s ->
        s.copy(todaySlots = s.todaySlots.map {
            if (it.medication.id == slot.medication.id && it.time == slot.time) it.copy(doneStatus = status) else it
        })
    }

    /**
     * "Soneca" — `snoozeMedication()`: the reminder comes back in the user's
     * snooze time. Nothing is written to the history, exactly like the web.
     */
    fun snooze(slot: DoseSlot, scheduler: ReminderScheduler) {
        val id = slot.medication.id ?: return
        viewModelScope.launch {
            val minutes = repo.snoozeMinutes()
            runCatching {
                scheduler.snooze(
                    DoseAlarm(
                        medicationId = id,
                        medicationName = slot.medication.name,
                        dosage = slot.medication.dosage,
                        instructions = slot.medication.instructions,
                        time = slot.time,
                        date = LocalDate.now().toString(),
                        snoozeMinutes = minutes,
                        soundId = repo.reminderSound(),
                    )
                )
            }
            _state.update { it.copy(message = "⏰ Alarme adiado por $minutes minutos") }
        }
    }

    /** "Excluir" (after the confirm) — `deleteMedication()`. */
    fun delete(medication: Medication) {
        val id = medication.id ?: return
        viewModelScope.launch {
            when (repo.deleteMedication(id)) {
                is ApiResult.Ok -> {
                    repo.local.deleteMedicationLook(id)
                    repo.local.deleteMedicationExtras(id)
                    _state.update { it.copy(message = "🗑️ Medicamento excluído") }
                    load()
                }
                is ApiResult.Err -> _state.update { it.copy(message = "❌ Erro ao excluir medicamento") }
            }
        }
    }

    /** "✕" on the daily Premium card: hidden until tomorrow. */
    fun dismissUpsell() {
        _state.update { it.copy(upsellDismissedToday = true) }
        viewModelScope.launch {
            runCatching { repo.local.setUiSetting(UPSELL_KEY, LocalDate.now().toString()) }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun streak(history: List<HistoryEntry>): Int {
        val zone = ZoneId.systemDefault()
        val takenDates = history.asSequence()
            .filter { it.status == "taken" }
            .mapNotNull { e ->
                (e.takenAt ?: e.scheduledFor)?.let { iso ->
                    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
                }
            }
            .toSet()
        var streak = 0
        var day = LocalDate.now()
        while (takenDates.contains(day) && streak < 365) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private companion object {
        const val UPSELL_KEY = "upsell_dismissed_date"
    }
}
