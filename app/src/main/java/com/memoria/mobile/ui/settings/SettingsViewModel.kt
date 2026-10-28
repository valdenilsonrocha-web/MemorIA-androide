package com.memoria.mobile.ui.settings

import kotlinx.coroutines.flow.update

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.ConsultationPayload
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.data.remote.VitalSignsPayload
import com.memoria.mobile.reminders.MemoriaNotifications
import com.memoria.mobile.reminders.ReminderScheduler
import com.memoria.mobile.reminders.ReminderSound
import com.memoria.mobile.ui.common.WhatsAppContacts
import com.memoria.mobile.ui.theme.ThemePreset
import com.memoria.mobile.util.BrazilianPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
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
    /** Web `apiService.isAuthenticated()`: a session token exists. */
    val hasToken: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    // Defaults = the web's `storageService.getDefaultSettings()`.
    val snoozeMinutes: Int = ReminderScheduler.DEFAULT_SNOOZE_MINUTES,
    val reminderSound: ReminderSound = ReminderSound.PADRAO,
    /** The web disables `#testSoundBtn` for 1.2 s after each press. */
    val soundTesting: Boolean = false,
    val lowStockAlerts: Boolean = true,
    val themePreset: String = ThemePreset.PASTEL_AZUL.id,
    /** "a" (suave) | "b" (alto contraste) — web `reportsGuideVariant`. */
    val reportsGuideVariant: String = "a",
    // Relatório automático por e-mail (guardado no servidor)
    val reportEnabled: Boolean = false,
    val reportFrequency: String = "weekly",
    /** caregiver | doctor. With doctor, the summary also goes to [reportContactPhone] by WhatsApp. */
    val reportContactType: String = "caregiver",
    val reportContactPhone: String = "",
    val reportContactEmail: String = "",
    /** The e-mail as last saved — the web's status line reads the saved value, not the field. */
    val reportSavedEmail: String = "",
    val reportSaving: Boolean = false,
    val lastReportSentAt: String? = null,
    // Privacidade e LGPD — o interruptor só vale depois de "Salvar Consentimento LGPD".
    val consentToggle: Boolean = false,
    val consentSaving: Boolean = false,
    // Sincronização Online
    val syncing: Boolean = false,
    val lastSyncAtMillis: Long? = null,
    val exporting: Boolean = false,
) {
    val isPremium: Boolean get() = user?.isPremium == true
    val isLoggedIn: Boolean get() = user != null
    val consentDate: String? get() = user?.consentDate
}

class SettingsViewModel(
    private val repo: MemoriaRepository,
    private val app: Application,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(baseUrl = repo.currentBaseUrl()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Bytes of "Exportar Dados" waiting for the user to pick a file. Kept out of the data class on purpose. */
    private val _pendingExport = MutableStateFlow<ByteArray?>(null)
    val pendingExport: StateFlow<ByteArray?> = _pendingExport.asStateFlow()

    private var player: MediaPlayer? = null

    fun load() {
        _state.update { it.copy(loading = true, baseUrl = repo.currentBaseUrl()) }
        viewModelScope.launch {
            loadLocalPreferences()
            when (val r = repo.me()) {
                is ApiResult.Ok -> applyUser(r.value, markSynced = true)
                is ApiResult.Err -> _state.update { it.copy(
                    loading = false,
                    error = r.message,
                ) }
            }
        }
    }

    /**
     * Local preferences resolve without the network, so sound, theme and snooze
     * stay usable even when the server cannot be reached.
     */
    private suspend fun loadLocalPreferences() {
        _state.update { it.copy(
            hasSavedCredentials = repo.credentials.hasSaved(),
            hasToken = repo.isLoggedIn(),
            snoozeMinutes = repo.snoozeMinutes(),
            reminderSound = ReminderSound.from(repo.reminderSound()),
            lowStockAlerts = repo.lowStockAlertsEnabled(),
            themePreset = repo.themePreset(),
            reportsGuideVariant = if (repo.local.uiSetting(KEY_REPORTS_VARIANT) == "b") "b" else "a",
            notificationsAllowed = MemoriaNotifications.canPost(app),
            exactAlarmsAllowed = scheduler.canScheduleExact(),
            lastSyncAtMillis = repo.local.uiSetting(KEY_LAST_SYNC)?.toLongOrNull(),
        ) }
    }

    private suspend fun applyUser(user: User, markSynced: Boolean) {
        val report = user.reportSettings
        val now = System.currentTimeMillis()
        if (markSynced) repo.local.setUiSetting(KEY_LAST_SYNC, now.toString())
        // Web: `reportAutomation.contactEmail || currentUser.reportSettings.contactEmail || currentUser.email`.
        val email = report?.contactEmail?.ifBlank { null } ?: user.email
        _state.update { it.copy(
            loading = false,
            user = user,
            reportEnabled = report?.enabled ?: false,
            reportFrequency = if (report?.frequency == "monthly") "monthly" else "weekly",
            reportContactType = if (report?.contactType == "doctor") "doctor" else "caregiver",
            reportContactPhone = report?.contactPhone?.takeIf { it.isNotBlank() }
                ?.let { WhatsAppContacts.forField("+$it") }.orEmpty(),
            reportContactEmail = email,
            reportSavedEmail = email,
            lastReportSentAt = report?.lastReportSentAt,
            consentToggle = user.consentDate != null,
            lastSyncAtMillis = if (markSynced) now else _state.value.lastSyncAtMillis,
        ) }
    }

    /** Called when the screen comes back from the system permission dialog. */
    fun refreshPermissions() {
        _state.update { it.copy(
            notificationsAllowed = MemoriaNotifications.canPost(app),
            exactAlarmsAllowed = scheduler.canScheduleExact(),
        ) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        val allowed = granted || MemoriaNotifications.canPost(app)
        _state.update { it.copy(
            notificationsAllowed = allowed,
            message = if (allowed) "✅ Notificações ativadas!" else null,
            error = if (allowed) null else "❌ Permissão de notificação negada",
        ) }
        if (allowed) viewModelScope.launch { scheduler.reschedule() }
    }

    // ---- Lembretes ----

    fun setSnoozeMinutes(minutes: Int) {
        _state.update { it.copy(snoozeMinutes = minutes) }
        viewModelScope.launch {
            repo.setSnoozeMinutes(minutes)
            // Pending alarms carry the old snooze in their Intent, so re-arm.
            scheduler.reschedule()
            _state.update { it.copy(message = SAVED) }
        }
    }

    /**
     * Each sound profile is its own notification channel, so the armed alarms have
     * to be rebuilt: a pending Intent still points at the channel it was created
     * with, and changing the setting alone would keep the old tone forever.
     */
    fun setReminderSound(sound: ReminderSound) {
        _state.update { it.copy(reminderSound = sound) }
        viewModelScope.launch {
            repo.setReminderSound(sound.id)
            scheduler.reschedule()
            _state.update { it.copy(message = SAVED) }
        }
    }

    /**
     * "Testar" — the web's `testSoundBtn`: plays the tone picked in the select
     * (and its vibration pattern) without posting anything, then keeps the button
     * disabled for 1.2 s. An id outside the select (the onboarding's "default")
     * tests as "system", exactly as `value || 'system'` does on the web.
     */
    fun testSound() {
        if (_state.value.soundTesting) return
        val sound = _state.value.reminderSound.takeIf { it in ReminderSound.webOptions } ?: ReminderSound.SYSTEM
        _state.update { it.copy(soundTesting = true) }

        player?.release()
        player = null
        sound.soundUri?.let { uri ->
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(sound.audioAttributes)
                    setDataSource(app, uri)
                    setOnCompletionListener { mp ->
                        mp.release()
                        if (player === mp) player = null
                    }
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        sound.vibrationPattern?.let { pattern ->
            runCatching {
                val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    app.getSystemService(Vibrator::class.java)
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
        viewModelScope.launch {
            delay(1200)
            _state.update { it.copy(soundTesting = false) }
        }
    }

    fun setLowStockAlerts(enabled: Boolean) {
        _state.update { it.copy(lowStockAlerts = enabled) }
        viewModelScope.launch {
            repo.setLowStockAlertsEnabled(enabled)
            scheduler.reschedule()
            _state.update { it.copy(message = SAVED) }
        }
    }

    fun setThemePreset(preset: String) {
        _state.update { it.copy(themePreset = preset) }
        viewModelScope.launch {
            repo.setThemePreset(preset)
            _state.update { it.copy(message = SAVED) }
        }
    }

    fun setReportsGuideVariant(variant: String) {
        val v = if (variant == "b") "b" else "a"
        _state.update { it.copy(reportsGuideVariant = v) }
        viewModelScope.launch {
            repo.local.setUiSetting(KEY_REPORTS_VARIANT, v)
            _state.update { it.copy(message = SAVED) }
        }
    }

    // ---- Relatório automático por e-mail ----

    fun onReportEnabled(enabled: Boolean) {
        _state.update { it.copy(reportEnabled = enabled) }
        saveReportSettings()
    }

    fun onReportFrequency(frequency: String) {
        _state.update { it.copy(reportFrequency = frequency) }
        saveReportSettings()
    }

    fun onReportContactEmail(email: String) {
        _state.update { it.copy(reportContactEmail = email) }
    }

    fun onReportContactType(type: String) {
        _state.update { it.copy(reportContactType = if (type == "doctor") "doctor" else "caregiver") }
        saveReportSettings()
    }

    fun onReportContactPhone(phone: String) {
        _state.update { it.copy(reportContactPhone = phone) }
    }

    /** "Importar dos contatos do WhatsApp" for the doctor: saved at once. */
    fun onReportDoctorPicked(phone: String) {
        _state.update { it.copy(reportContactPhone = phone) }
        saveReportSettings()
    }

    /**
     * The web's `saveSettings()` + `syncAutomatedReportSettingsToBackend()`:
     * "✅ Configurações salvas" shows at once, and the report settings go to
     * `PUT /api/auth/profile` with the same payload the web sends
     * (`reportAutoSendEnabled`, `reportFrequency`, `reportContactEmail || null`,
     * `medicalConsultations`, `healthVitalSigns`). An enabled report without a
     * valid e-mail is not sent upstream; a failed sync is silent, as on the web.
     */
    fun saveReportSettings() {
        val s = _state.value
        val email = s.reportContactEmail.trim().lowercase()
        _state.value = s.copy(reportSavedEmail = email, message = SAVED)
        if (!s.isLoggedIn) return
        val doctor = s.reportContactType == "doctor"
        val phone = reportPhoneForServer(s.reportContactPhone)
        // A doctor reached by WhatsApp needs no e-mail; anyone else still does.
        if (s.reportEnabled && !isValidEmail(email) && !(doctor && phone.isNotEmpty())) return
        if (s.reportContactPhone.isNotBlank() && phone.isEmpty()) return
        _state.update { it.copy(reportSaving = true) }
        viewModelScope.launch {
            val request = ProfileUpdateRequest(
                reportAutoSendEnabled = s.reportEnabled,
                reportFrequency = if (s.reportFrequency == "monthly") "monthly" else "weekly",
                reportContactType = if (doctor) "doctor" else "caregiver",
                reportContactPhone = phone,
                // Moshi drops nulls, and the server turns "" into null: both mean "no e-mail".
                reportContactEmail = email,
                medicalConsultations = consultationsPayload(),
                healthVitalSigns = vitalSignsPayload(),
            )
            when (val r = repo.updateProfile(request)) {
                is ApiResult.Ok -> _state.update { it.copy(
                    reportSaving = false,
                    user = r.value,
                    lastReportSentAt = r.value.reportSettings?.lastReportSentAt ?: _state.value.lastReportSentAt,
                ) }
                is ApiResult.Err -> _state.update { it.copy(reportSaving = false) }
            }
        }
    }

    private suspend fun vitalSignsPayload(): List<VitalSignsPayload> =
        repo.local.vitalSigns().map {
            VitalSignsPayload(
                date = it.date,
                systolic = it.systolic,
                diastolic = it.diastolic,
                heartRate = it.heartRate,
                spo2 = it.spo2,
                glucose = it.glucose,
                glucoseContext = it.glucoseContext,
                hba1c = it.hba1c,
                temperature = it.temperature,
            )
        }

    private suspend fun consultationsPayload(): List<ConsultationPayload> =
        repo.local.consultations().map {
            ConsultationPayload(
                id = it.id,
                dateTime = isoFromLocal(it.dateTime),
                professional = it.professional,
                location = it.location,
                notes = it.notes,
            )
        }

    /** `datetime-local` has no zone; the server wants a real instant. */
    private fun isoFromLocal(raw: String): String = runCatching {
        LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrDefault(raw)

    // ---- Privacidade e LGPD ----

    fun onConsentToggle(checked: Boolean) { _state.update { it.copy(consentToggle = checked) } }

    fun saveConsent() {
        val consent = _state.value.consentToggle
        if (!_state.value.isLoggedIn) {
            _state.update { it.copy(error = "⚠️ Faça login para atualizar o consentimento LGPD na nuvem.") }
            return
        }
        _state.update { it.copy(consentSaving = true) }
        viewModelScope.launch {
            when (val r = repo.setConsent(consent)) {
                is ApiResult.Ok -> _state.update { it.copy(
                    consentSaving = false,
                    user = r.value,
                    consentToggle = r.value.consentDate != null,
                    message = if (consent) "✅ Consentimento LGPD atualizado." else "✅ Consentimento LGPD revogado.",
                ) }
                is ApiResult.Err -> _state.update { it.copy(
                    consentSaving = false,
                    error = "❌ Erro ao atualizar consentimento LGPD: ${r.message}",
                ) }
            }
        }
    }

    // ---- Sincronização Online ----

    /** The web's `syncNowBtn`: "🔄 Sincronizando..." then the sync result. */
    fun syncNow() {
        _state.update { it.copy(syncing = true, message = "🔄 Sincronizando...") }
        viewModelScope.launch {
            when (val r = repo.me()) {
                is ApiResult.Ok -> {
                    applyUser(r.value, markSynced = true)
                    // Re-arms every dose from what the server now says.
                    scheduler.reschedule()
                    _state.update { it.copy(syncing = false, message = "✅ Dados sincronizados com sucesso!") }
                }
                is ApiResult.Err -> _state.update { it.copy(
                    syncing = false,
                    error = "❌ Erro ao sincronizar dados: ${r.message}",
                ) }
            }
        }
    }

    // ---- Dados ----

    fun requestExport() {
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            when (val r = repo.exportData()) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(exporting = false) }
                    _pendingExport.value = r.value
                }
                is ApiResult.Err -> _state.update { it.copy(exporting = false, error = r.message) }
            }
        }
    }

    fun writeExportTo(destination: Uri) {
        val bytes = _pendingExport.value ?: return
        _pendingExport.value = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                        ?: error("sem acesso de escrita")
                }.isSuccess
            }
            _state.value = if (ok) {
                _state.value.copy(message = "✅ Dados exportados!")
            } else {
                _state.value.copy(error = "Não foi possível gravar o arquivo escolhido.")
            }
        }
    }

    fun discardExport() { _pendingExport.value = null }

    /**
     * "Apagar deste celular" — the web's `storageService.clearAllData()`: wipes
     * what the device keeps (local records and every setting back to the web
     * defaults) and nothing on the server; the account and its medications stay.
     * The toast says "deste celular" so it does not read as an account deletion.
     */
    fun clearLocalData() {
        viewModelScope.launch {
            repo.local.saveVitalSigns(emptyList())
            repo.local.saveWellbeing(emptyList())
            repo.local.saveSafety(emptyList())
            repo.local.saveCareContacts(emptyList())
            repo.local.saveConsultations(emptyList())
            repo.local.saveEmergencyContacts(emptyList())
            repo.local.clearMedicationExtras()
            repo.setSnoozeMinutes(ReminderScheduler.DEFAULT_SNOOZE_MINUTES)
            repo.setReminderSound(ReminderSound.PADRAO.id)
            repo.setThemePreset(ThemePreset.PASTEL_AZUL.id)
            repo.setEmergencyAlertMode("auto")
            repo.setLowStockAlertsEnabled(true)
            repo.local.setUiSetting(KEY_REPORTS_VARIANT, null)
            repo.local.setUiSetting(KEY_LAST_SYNC, null)
            repo.local.setUiSetting(KEY_PROFILE_PHOTO, null)
            scheduler.reschedule()
            loadLocalPreferences()
            _state.update { it.copy(message = "🗑️ Dados deste celular apagados") }
        }
    }

    // ---- Servidor / acesso ----

    fun onBaseUrl(v: String) { _state.update { it.copy(baseUrl = v) } }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim()
        if (url.isBlank()) { _state.update { it.copy(error = "Informe a URL do servidor.") }; return }
        viewModelScope.launch {
            repo.setBaseUrl(url)
            _state.update { it.copy(
                baseUrl = repo.currentBaseUrl(),
                message = "Servidor atualizado.",
                reachable = null,
            ) }
        }
    }

    fun testConnection() {
        _state.update { it.copy(checking = true, reachable = null) }
        viewModelScope.launch {
            // checkServer() carries the reason, so a failure names its cause
            // instead of only turning the indicator red.
            when (val r = repo.checkServer()) {
                is ApiResult.Ok ->
                    _state.update { it.copy(checking = false, reachable = true) }
                is ApiResult.Err ->
                    _state.update { it.copy(checking = false, reachable = false, error = r.message) }
            }
        }
    }

    /** Wipes the remembered CPF + password; the login form goes back to empty. */
    fun forgetCredentials() {
        viewModelScope.launch {
            repo.forgetCredentials()
            _state.update { it.copy(
                hasSavedCredentials = false,
                message = "CPF e senha esquecidos neste celular.",
            ) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            onDone()
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    override fun onCleared() {
        player?.release()
        player = null
    }

    companion object {
        private const val SAVED = "✅ Configurações salvas"
        private const val KEY_REPORTS_VARIANT = "reports_guide_variant"
        private const val KEY_LAST_SYNC = "last_sync_at"
        /** Shared with the header — keep the key. */
        private const val KEY_PROFILE_PHOTO = "profile_photo_path"

        /**
         * The doctor's WhatsApp as the server stores it: digits with the country
         * code. "" for an empty field, and also for a number too short to be real.
         */
        fun reportPhoneForServer(raw: String): String {
            if (raw.isBlank()) return ""
            val id = BrazilianPhone.toWhatsAppId(raw)
            return if (id.length in 12..15) id else ""
        }

        /** Same rule as the web's `isValidEmail`. */
        fun isValidEmail(value: String): Boolean {
            val normalized = value.trim().lowercase()
            val at = normalized.indexOf('@')
            if (at <= 0 || at != normalized.lastIndexOf('@')) return false
            val domain = normalized.substring(at + 1)
            return !normalized.contains(' ') &&
                domain.contains('.') &&
                !domain.startsWith('.') &&
                !domain.endsWith('.')
        }
    }
}
