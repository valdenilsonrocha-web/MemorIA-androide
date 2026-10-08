package com.memoria.mobile.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.reminders.DoseActions
import com.memoria.mobile.ui.common.Schedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * What the app shell (header + bottom bar) needs, kept out of every screen.
 *
 * [pendingCount] is the web's `getPendingMedicationCount()`: today's doses whose
 * status is `pending` or `missed` (not taken, and either past their time or
 * within 15 minutes of it) plus the active medications with fewer than 7 units
 * left (`getMedicationsNeedingAttention()`). It drives the red badge on the bell
 * and the red "Atualizações" tab.
 */
class ShellViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _pendingCount = MutableStateFlow(0)

    /**
     * Hours left in the free 7-day Premium access when it ends within 24h — the web's
     * `#trialExpiryBanner` (`updateTrialExpiryBanner`). Null hides the banner.
     */
    private val _trialHoursLeft = MutableStateFlow<Int?>(null)
    val trialHoursLeft: StateFlow<Int?> = _trialHoursLeft.asStateFlow()
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var job: Job? = null

    /** Failures keep the last known count — the badge is a hint, not data. */
    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val meCall = async { repo.me() }
            val medsCall = async { repo.medications() }
            val historyCall = async { repo.history() }
            val meds = (medsCall.await() as? ApiResult.Ok)?.value ?: return@launch
            // Doses answered offline ("Tomei" with no connection) wait in a local
            // queue; they are taken all the same and must not light the badge.
            val queued = runCatching { DoseActions.pendingHistory(repo) }.getOrDefault(emptyList())
            val history = (historyCall.await() as? ApiResult.Ok)?.value.orEmpty() + queued
            _pendingCount.value = countPending(meds, history)
            (meCall.await() as? ApiResult.Ok)?.value?.let { user -> _trialHoursLeft.value = trialHoursLeft(user) }
        }
    }

    private fun countPending(meds: List<Medication>, history: List<HistoryEntry>): Int {
        val active = meds.filter { it.active }
        val today = LocalDate.now()
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val nowMinutes = minutesOf(now)
        val slots = Schedule.withHistory(Schedule.slotsFor(active, today), history, today)
        val dosePending = slots.count { slot ->
            if (slot.doneStatus == "taken") return@count false
            val missed = slot.time < now
            val dueSoon = kotlin.math.abs(minutesOf(slot.time) - nowMinutes) <= 15
            missed || dueSoon
        }
        val lowStock = active.count { it.stock < 7 }
        return (dosePending + lowStock).coerceAtLeast(0)
    }

    private fun minutesOf(hhmm: String): Int {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }
}

/**
 * `updateTrialExpiryBanner()`: only a trial still running that ends in the next
 * 24 hours; rounded up, never below 1h. The trial is the server's free access
 * from the first login (`getUserTrialInfo`): no card, nothing charged when it ends.
 */
private fun trialHoursLeft(user: com.memoria.mobile.data.remote.User): Int? {
    if (!user.isTrialActive) return null
    val ends = user.trialEndsAt?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return null
    val remainingMs = ends.toEpochMilli() - System.currentTimeMillis()
    val oneDayMs = 24L * 60 * 60 * 1000
    if (remainingMs <= 0 || remainingMs > oneDayMs) return null
    return maxOf(1, Math.ceil(remainingMs / (60.0 * 60 * 1000)).toInt())
}
