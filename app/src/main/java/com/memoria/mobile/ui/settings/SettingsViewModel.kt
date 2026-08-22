package com.memoria.mobile.ui.settings

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.reminders.DoseAlarm
import com.memoria.mobile.reminders.MemoriaNotifications
import com.memoria.mobile.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SettingsUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val baseUrl: String = "",
    val error: String? = null,
    val message: String? = null,
    val checking: Boolean = false,
    val reachable: Boolean? = null,
    val hasSavedCredentials: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val snoozeMinutes: Int = ReminderScheduler.DEFAULT_SNOOZE_MINUTES,
)

class SettingsViewModel(
    private val repo: MemoriaRepository,
    private val app: Application,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(baseUrl = repo.currentBaseUrl()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, baseUrl = repo.currentBaseUrl())
        viewModelScope.launch {
            val hasSaved = repo.credentials.hasSaved()
            val snooze = repo.snoozeMinutes()
            when (val r = repo.me()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    loading = false,
                    user = r.value,
                    hasSavedCredentials = hasSaved,
                    snoozeMinutes = snooze,
                    notificationsAllowed = MemoriaNotifications.canPost(app),
                    exactAlarmsAllowed = scheduler.canScheduleExact(),
                )
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    error = r.message,
                    hasSavedCredentials = hasSaved,
                    snoozeMinutes = snooze,
                    notificationsAllowed = MemoriaNotifications.canPost(app),
                    exactAlarmsAllowed = scheduler.canScheduleExact(),
                )
            }
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        _state.value = _state.value.copy(snoozeMinutes = minutes)
        viewModelScope.launch {
            repo.setSnoozeMinutes(minutes)
            // Pending alarms carry the old snooze in their Intent, so re-arm.
            scheduler.reschedule()
            _state.value = _state.value.copy(message = "Soneca definida em $minutes minutos.")
        }
    }

    /**
     * Posts a reminder right now so the user can confirm — before a real dose is
     * at stake — that the alert actually breaks through their phone's settings.
     */
    fun sendTestNotification() {
        MemoriaNotifications.ensureChannels(app)
        if (!MemoriaNotifications.canPost(app)) {
            _state.value = _state.value.copy(
                notificationsAllowed = false,
                error = "As notificações estão bloqueadas. Abra as notificações do sistema e permita o MemorIA.",
            )
            return
        }
        val dose = DoseAlarm(
            medicationId = "teste",
            medicationName = "Teste do MemorIA",
            dosage = "Exemplo",
            instructions = "Se você está vendo isto, os lembretes funcionam.",
            time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            date = LocalDate.now().toString(),
            snoozeMinutes = _state.value.snoozeMinutes,
        )
        NotificationManagerCompat.from(app)
            .notify(dose.requestCode, MemoriaNotifications.buildDoseNotification(app, dose))
        _state.value = _state.value.copy(message = "Lembrete de teste enviado.")
    }

    fun onBaseUrl(v: String) { _state.value = _state.value.copy(baseUrl = v) }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isBlank()) { _state.value = _state.value.copy(error = "Informe a URL do servidor."); return }
        viewModelScope.launch {
            repo.setBaseUrl(url)
            _state.value = _state.value.copy(
                baseUrl = repo.currentBaseUrl(),
                message = "Servidor atualizado.",
                reachable = null,
            )
        }
    }

    fun testConnection() {
        _state.value = _state.value.copy(checking = true, reachable = null)
        viewModelScope.launch {
            // checkServer() carries the reason, so a failure names its cause
            // instead of only turning the indicator red.
            when (val r = repo.checkServer()) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(checking = false, reachable = true)
                is ApiResult.Err ->
                    _state.value = _state.value.copy(checking = false, reachable = false, error = r.message)
            }
        }
    }

    /** Wipes the remembered CPF + password; the login form goes back to empty. */
    fun forgetCredentials() {
        viewModelScope.launch {
            repo.forgetCredentials()
            _state.value = _state.value.copy(
                hasSavedCredentials = false,
                message = "CPF e senha esquecidos neste celular.",
            )
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onDone()
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
