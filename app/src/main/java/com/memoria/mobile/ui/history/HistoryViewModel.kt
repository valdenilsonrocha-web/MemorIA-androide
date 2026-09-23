package com.memoria.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.reminders.DoseActions
import com.memoria.mobile.reminders.ReminderMessage
import com.memoria.mobile.reminders.ReminderTexts
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.meds.doseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One medication running low, with the web's rotating replenishment text. */
data class StockNeed(val medication: Medication, val stock: Int, val message: ReminderMessage)

data class HistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    /** `all` | `taken` | `missed` — the `.history-filters` buttons. */
    val filter: String = "all",
    val entries: List<HistoryEntry> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val looks: Map<String, MedicationLook> = emptyMap(),
    /** Today's doses that are `pending` or `missed` (`getPendingMedicationsForUpdates()`). */
    val pending: List<Pair<DoseSlot, String>> = emptyList(),
    /** `getStockReplenishmentNeeds()`: active medications with fewer than 7 doses. */
    val stockNeeds: List<StockNeed> = emptyList(),
    val user: User? = null,
) {
    /** A web marca o banner de assinatura com `ad-only-free`: só aparece a quem não é Premium. */
    val isPremium: Boolean get() = user?.isPremium == true

    val visiblePending: List<Pair<DoseSlot, String>> get() = if (filter == "taken") emptyList() else pending
    val visibleStock: List<StockNeed> get() = if (filter == "taken") emptyList() else stockNeeds

    /** `reminderService.getHistoryByDate(filter)`: dd/MM/yyyy → entries, newest day first. */
    val grouped: List<Pair<String, List<HistoryEntry>>>
        get() = entries
            .filter { e ->
                when (filter) {
                    "taken" -> e.status == "taken"
                    "missed" -> e.status == "missed"
                    else -> true
                }
            }
            .sortedByDescending { timestampOf(it) }
            .groupBy { e -> timestampOf(e)?.toLocalDate()?.format(DATE_BR) ?: "" }
            .toList()

    /** The web's pending banner: names, "e mais N", singular/plural. */
    val pendingBannerText: String?
        get() {
            if (visiblePending.isEmpty()) return null
            val names = visiblePending.map { it.first.medication.name.trim() }.filter { it.isNotBlank() }.distinct()
            val preview = names.take(3).joinToString(", ")
            val remaining = (names.size - 3).coerceAtLeast(0)
            val label = if (remaining > 0) "$preview e mais $remaining" else preview.ifBlank { "medicamentos cadastrados" }
            val count = visiblePending.size
            val medWord = if (count == 1) "medicação" else "medicações"
            val statusWord = if (count == 1) "pendente" else "pendentes"
            return "Atenção! Você tem $count $medWord $statusWord hoje: $label. " +
                "Com a assinatura Premium, você ganha mais controle para não perder horários críticos."
        }

    fun lookFor(medicationId: String?, medicationName: String): MedicationLook? {
        val id = medicationId ?: medications.firstOrNull { it.name == medicationName }?.id
        return id?.let { looks[it] }
    }

    companion object {
        private val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        /** The web's `entry.timestamp`: when the dose was recorded. */
        fun timestampOf(e: HistoryEntry): java.time.ZonedDateTime? =
            listOfNotNull(e.takenAt, e.createdAt, e.scheduledFor).firstNotNullOfOrNull { iso ->
                runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
            }
    }
}

class HistoryViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            DoseActions.flushPending(repo)
            val historyResult = repo.history(limit = 200)
            val meds = (repo.medications() as? ApiResult.Ok)?.value.orEmpty().filter { it.active }
            val user = (repo.me() as? ApiResult.Ok)?.value
            val looks = repo.local.medicationLooks().associateBy { it.medicationId }

            if (historyResult is ApiResult.Err) {
                _state.value = _state.value.copy(loading = false, error = historyResult.message)
                return@launch
            }
            // Doses answered offline (still queued) are part of the history, as on the web.
            val entries = (historyResult as ApiResult.Ok).value + DoseActions.pendingHistory(repo)
            val today = LocalDate.now()
            val pending = Schedule.withHistory(Schedule.slotsFor(meds, today), entries, today)
                .map { it to doseStatus(it) }
                .filter { it.second == "pending" || it.second == "missed" }
            val stockNeeds = meds.filter { it.stock < 7 }.map { m ->
                val stock = m.stock.coerceAtLeast(0)
                StockNeed(m, stock, ReminderTexts.randomReplenishment(m.name, stock))
            }
            _state.value = _state.value.copy(
                loading = false,
                entries = entries,
                medications = meds,
                looks = looks,
                pending = pending,
                stockNeeds = stockNeeds,
                user = user,
            )
        }
    }

    fun setFilter(filter: String) { _state.value = _state.value.copy(filter = filter) }

    /** "Marcar como tomado" — `resolvePendingMedication()` → `takeMedication()`. */
    fun resolve(slot: DoseSlot) {
        val id = slot.medication.id ?: return
        viewModelScope.launch {
            val r = DoseActions.markTaken(repo, id, slot.medication.name, slot.medication.dosage, slot.time)
            _state.value = _state.value.copy(
                message = if (r is ApiResult.Ok) "✅ Medicamento registrado como tomado!" else "❌ Erro ao registrar medicamento.",
            )
            if (r is ApiResult.Ok) load()
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}
