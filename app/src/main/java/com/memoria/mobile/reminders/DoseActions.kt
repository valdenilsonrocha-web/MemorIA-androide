package com.memoria.mobile.reminders

import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.HistoryRequest
import com.memoria.mobile.data.remote.MedicationRequest
import com.memoria.mobile.ui.common.Schedule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The web's `takeMedication()` / `reminderService.markAsTaken()`, shared by every
 * place that records a dose as taken (dashboard, Atualizações, the notification
 * button) so they cannot drift apart.
 *
 * Besides the history entry, the web also takes one unit off the stock
 * (`stock - 1` when `stock > 0`). The server does not do that on its own, so
 * without this the phone's stock — and therefore the replenishment forecast and
 * the low-stock alarms — never went down.
 *
 * Offline, like the web (`storageService` first, `syncService` later): a "Tomei"
 * that cannot reach the server is kept in a small local queue and reported as
 * recorded — the web shows "✅ Medicamento registrado como tomado!" offline too.
 * [flushPending] sends the queue on the next chance (app open, alarm, next dose)
 * and [pendingHistory] lets the screens count the queued doses as taken
 * meanwhile, so the dose does not reappear as late.
 */
object DoseActions {

    private const val QUEUE_KEY = "pending_dose_queue"
    private val mutex = Mutex()

    private val adapter: JsonAdapter<List<HistoryRequest>> by lazy {
        Moshi.Builder().build().adapter(
            Types.newParameterizedType(List::class.java, HistoryRequest::class.java),
        )
    }

    suspend fun markTaken(
        repo: MemoriaRepository,
        medicationId: String,
        medicationName: String,
        dosage: String,
        time: String,
        scheduledFor: String = Schedule.isoFor(LocalDate.now(), time),
    ): ApiResult<Unit> {
        val request = HistoryRequest(
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            scheduleTime = time,
            status = "taken",
            scheduledFor = scheduledFor,
            takenAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
        // Anything still queued goes first, in order.
        flushPending(repo)
        val result = repo.addHistory(request)
        return when {
            result is ApiResult.Ok -> {
                decrementStock(repo, medicationId)
                result
            }
            result is ApiResult.Err && isNetworkFailure(result.message) -> {
                enqueue(repo, request)
                ApiResult.Ok(Unit)
            }
            else -> result
        }
    }

    /** Sends every queued dose; those the server accepted leave the queue. */
    suspend fun flushPending(repo: MemoriaRepository) = mutex.withLock {
        val queue = readQueue(repo)
        if (queue.isEmpty()) return@withLock
        val left = mutableListOf<HistoryRequest>()
        var offline = false
        for (req in queue) {
            if (offline) { left += req; continue }
            when (val r = repo.addHistory(req)) {
                is ApiResult.Ok -> if (req.status == "taken") decrementStock(repo, req.medicationId)
                is ApiResult.Err -> {
                    // Still offline: keep this one and the rest for later. A
                    // server-side refusal would never succeed, so it is dropped.
                    if (isNetworkFailure(r.message)) {
                        offline = true
                        left += req
                    }
                }
            }
        }
        writeQueue(repo, left)
    }

    /** Queued doses as history entries, to merge with the server's list. */
    suspend fun pendingHistory(repo: MemoriaRepository): List<HistoryEntry> =
        readQueue(repo).map { r ->
            HistoryEntry(
                medicationId = r.medicationId,
                medicationName = r.medicationName,
                dosage = r.dosage,
                scheduleTime = r.scheduleTime,
                status = r.status,
                takenAt = r.takenAt,
                scheduledFor = r.scheduledFor,
                createdAt = r.takenAt ?: r.scheduledFor,
            )
        }

    private suspend fun enqueue(repo: MemoriaRepository, request: HistoryRequest) = mutex.withLock {
        writeQueue(repo, readQueue(repo) + request)
    }

    private suspend fun readQueue(repo: MemoriaRepository): List<HistoryRequest> =
        runCatching { repo.local.uiSetting(QUEUE_KEY)?.let { adapter.fromJson(it) } }.getOrNull().orEmpty()

    private suspend fun writeQueue(repo: MemoriaRepository, items: List<HistoryRequest>) {
        runCatching { repo.local.setUiSetting(QUEUE_KEY, if (items.isEmpty()) null else adapter.toJson(items)) }
    }

    /**
     * The repository's connection messages all point the user at the “Servidor”
     * address or mention the connection; a server answer (4xx/5xx) does not.
     */
    private fun isNetworkFailure(message: String): Boolean =
        message.contains("“Servidor”") || message.contains("ligação", ignoreCase = true) ||
            message.contains("Falha de rede")

    /** `stock - 1`, only while there is stock — exactly `markAsTaken()` on the web. */
    private suspend fun decrementStock(repo: MemoriaRepository, medicationId: String) {
        val med = (repo.medications() as? ApiResult.Ok)?.value
            ?.firstOrNull { it.id == medicationId } ?: return
        if (med.stock <= 0) return
        repo.updateMedication(
            medicationId,
            MedicationRequest(
                name = med.name,
                dosage = med.dosage,
                frequency = med.frequency,
                times = med.times,
                weekDays = if (med.frequency == "weekly") med.weekDays else null,
                instructions = med.instructions,
                stock = med.stock - 1,
                active = med.active,
                // null = "do not touch": the pharmacy contact is left as it is.
                supplier = null,
            ),
        )
    }
}
