package com.memoria.mobile.data

import com.memoria.mobile.BuildConfig
import com.memoria.mobile.ui.common.PlanLimits.withTemporaryFullAccess
import com.memoria.mobile.data.local.PreferencesStore
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.ApiProvider
import com.memoria.mobile.data.remote.ApiService
import com.memoria.mobile.data.remote.AuthData
import com.memoria.mobile.data.remote.Caregiver
import com.memoria.mobile.data.remote.ConsentRequest
import com.memoria.mobile.data.remote.ConsultationPayload
import com.memoria.mobile.data.remote.DeviceReportRequest
import com.memoria.mobile.data.remote.ForgotPasswordRequest
import com.memoria.mobile.data.remote.ResetPasswordRequest
import com.memoria.mobile.data.remote.VitalSignsPayload
import com.memoria.mobile.data.remote.VoiceAssistantConfig
import com.memoria.mobile.data.remote.VoiceAssistantDispatchData
import com.memoria.mobile.data.remote.VoiceAssistantDispatchRequest
import com.memoria.mobile.data.remote.Envelope
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.HistoryRequest
import com.memoria.mobile.data.remote.LoginRequest
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.MedicationRequest
import com.memoria.mobile.data.local.CredentialStore
import com.memoria.mobile.data.local.LocalStore
import com.memoria.mobile.data.remote.OwnerStats
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.data.remote.PrescriptionRequest
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.RegisterRequest
import com.memoria.mobile.data.remote.ExpiryPlan
import com.memoria.mobile.data.remote.SessionRefresh
import com.memoria.mobile.data.remote.SessionState
import com.memoria.mobile.data.remote.SimpleResponse
import com.memoria.mobile.data.remote.User
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Success/failure wrapper surfaced to the ViewModels. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val message: String) : ApiResult<Nothing>
}

/**
 * Single entry point to the backend + local session. Holds the current base URL
 * and token in memory (seeded from DataStore) and keeps both persisted.
 */
class MemoriaRepository(
    private val prefs: PreferencesStore,
    private val session: SessionState,
    private val apiProvider: ApiProvider,
) {
    @Volatile
    private var baseRoot: String = BuildConfig.DEFAULT_API_BASE_URL

    /**
     * Phone-side records the backend has no table for (health measurements, care
     * network, consultations). Exposed directly so the screens that own them can
     * read/write without a pass-through method per field.
     */
    val local: LocalStore = LocalStore(prefs)

    /** Keystore-encrypted CPF + password, for the "remember me" login. */
    val credentials: CredentialStore = CredentialStore(prefs)

    /**
     * Fired whenever something that affects the reminder schedule changes — a
     * medication added, edited or removed, a dose recorded, a login or logout.
     * [AppGraph] wires it to the alarm scheduler so no screen has to remember to.
     */
    @Volatile
    var onScheduleChanged: (() -> Unit)? = null

    /**
     * Fired on an explicit logout (Settings › Sair) or when the token dies and
     * there are no saved credentials to log back in with. Wired to
     * [com.memoria.mobile.reminders.ReminderScheduler.cancelAll] — expiry with
     * saved credentials must NOT go through here, or the weekly JWT rotation
     * would wipe every alarm.
     */
    @Volatile
    var onLoggedOut: (() -> Unit)? = null

    private val sessionMutex = Mutex()

    /** After a wrong-password silent refresh, do not keep hammering /auth/login. */
    @Volatile
    private var silentRefreshBlocked = false

    @Volatile
    private var lastSilentRefreshAt = 0L

    val tokenFlow: Flow<String?> = prefs.tokenFlow
    val baseUrlFlow: Flow<String?> = prefs.baseUrlFlow

    /**
     * Load persisted session at startup.
     *
     * A base URL saved in Settings used to beat [BuildConfig.DEFAULT_API_BASE_URL]
     * forever, so an address saved once against an old build kept the app pointed
     * at a dead host through every update — and the only escape was clearing app
     * data. The override now carries a stamp of the default it was made against:
     * when a new build ships a different default, the stale override is dropped.
     */
    suspend fun bootstrap() {
        val saved = prefs.baseUrl()?.takeIf { it.isNotBlank() }
        if (saved != null) {
            if (prefs.baseUrlStamp() == BuildConfig.DEFAULT_API_BASE_URL) {
                baseRoot = ApiProvider.normalizeRoot(saved)
            } else {
                prefs.clearBaseUrl()
                baseRoot = BuildConfig.DEFAULT_API_BASE_URL
            }
        }
        session.token = prefs.token()
    }

    fun currentBaseUrl(): String = baseRoot

    fun isLoggedIn(): Boolean = !session.token.isNullOrBlank()

    private fun api(): ApiService = apiProvider.service(baseRoot)

    suspend fun setBaseUrl(url: String) {
        baseRoot = ApiProvider.normalizeRoot(url)
        prefs.setBaseUrl(baseRoot, BuildConfig.DEFAULT_API_BASE_URL)
    }

    /** Drops any saved override and goes back to the address shipped in this build. */
    suspend fun resetBaseUrlToDefault() {
        baseRoot = BuildConfig.DEFAULT_API_BASE_URL
        prefs.clearBaseUrl()
    }

    /**
     * Ends the session because the SERVER rejected the token, not because the user
     * asked. If this phone still has the CPF and password, logs back in silently
     * so the 7-day JWT expiry does not cancel the reminder window — the person
     * this app is built for will not open it just to refresh a token.
     *
     * The dead token is left in place while the refresh runs, so the UI does not
     * flash the login screen on a successful refresh. A network failure also
     * leaves token and alarms alone and retries on the next 401.
     *
     * Idempotent: several in-flight requests fail together when a token dies.
     */
    suspend fun expireSession() = sessionMutex.withLock {
        val plan = SessionRefresh.plan(
            rememberEnabled = credentials.rememberEnabled(),
            hasSavedCredentials = credentials.hasSaved(),
            refreshBlocked = silentRefreshBlocked,
        )
        when (plan) {
            ExpiryPlan.SilentRefresh -> {
                val now = System.currentTimeMillis()
                if (session.token != null && now - lastSilentRefreshAt < SessionRefresh.RETRY_COOLDOWN_MS) {
                    return@withLock
                }
                lastSilentRefreshAt = now
                when (val result = loginWithSavedCredentials()) {
                    is ApiResult.Ok -> return@withLock
                    is ApiResult.Err -> {
                        if (SessionRefresh.looksLikeNetworkError(result.message)) return@withLock
                        silentRefreshBlocked = true
                        dropToken()
                    }
                }
            }
            ExpiryPlan.DropTokenKeepAlarms -> dropToken()
            ExpiryPlan.DropTokenCancelAlarms -> {
                dropToken()
                onLoggedOut?.invoke()
            }
        }
    }

    /**
     * Ends the session but deliberately KEEPS the remembered credentials, so a
     * user who logs out can get back in without retyping. Handing the phone to
     * someone else is the case that wants them gone — [forgetCredentials] does
     * that, and Settings puts it next to the logout button.
     */
    suspend fun logout() {
        sessionMutex.withLock {
            silentRefreshBlocked = false
            dropToken()
        }
        // Clears every pending alarm: a logged-out phone must not keep buzzing
        // about someone else's medication.
        onLoggedOut?.invoke()
    }

    private suspend fun dropToken() {
        if (session.token == null && prefs.token() == null) return
        session.token = null
        prefs.clearToken()
    }

    /** Wipes the saved CPF + password (Settings → "Esquecer dados salvos"). */
    suspend fun forgetCredentials() {
        credentials.clear()
    }

    // ---- Reminder preferences ----

    /** Minutes the notification's "Adiar" button pushes a dose forward. */
    suspend fun snoozeMinutes(): Int =
        prefs.string(KEY_SNOOZE)?.toIntOrNull()?.takeIf { it in 1..180 } ?: DEFAULT_SNOOZE

    suspend fun setSnoozeMinutes(minutes: Int) {
        prefs.setString(KEY_SNOOZE, minutes.coerceIn(1, 180).toString())
    }

    /** Which alert profile the dose channel uses — see `ReminderSound`. */
    suspend fun reminderSound(): String =
        prefs.string(KEY_SOUND)?.takeIf { it.isNotBlank() } ?: DEFAULT_SOUND

    suspend fun setReminderSound(id: String) = prefs.setString(KEY_SOUND, id)

    /** Whether the phone itself warns when a medication is running out. */
    suspend fun lowStockAlertsEnabled(): Boolean =
        prefs.string(KEY_LOW_STOCK_ALERTS)?.toBooleanStrictOrNull() ?: true

    suspend fun setLowStockAlertsEnabled(enabled: Boolean) =
        prefs.setString(KEY_LOW_STOCK_ALERTS, enabled.toString())

    /**
     * `auto` | `confirm` — mirrors the web's "Alerta de Emergência". Local by
     * design: the WhatsApp escalation itself is the server's job, this only says
     * whether the phone asks before nudging it along.
     */
    suspend fun emergencyAlertMode(): String =
        prefs.string(KEY_EMERGENCY_MODE)?.takeIf { it == "confirm" } ?: "auto"

    suspend fun setEmergencyAlertMode(mode: String) =
        prefs.setString(KEY_EMERGENCY_MODE, if (mode == "confirm") "confirm" else "auto")

    /** Palette key from the web's "Tema do Aplicativo" list. */
    val themePresetFlow: Flow<String?> = prefs.stringFlow(KEY_THEME_PRESET)

    suspend fun themePreset(): String =
        prefs.string(KEY_THEME_PRESET)?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_PRESET

    suspend fun setThemePreset(preset: String) = prefs.setString(KEY_THEME_PRESET, preset)

    // ---- Auth ----

    /**
     * On success the credentials are remembered (unless the user turned that off),
     * so the next login screen comes pre-filled. Saving happens here rather than
     * in the ViewModel because this is the only place that knows the password was
     * actually accepted — remembering a rejected one would lock the user out on
     * every launch.
     */
    suspend fun login(cpf: String, password: String): ApiResult<User> {
        val result = authCall { api().login(LoginRequest(cpf = digitsOnly(cpf), password = password)) }
        if (result is ApiResult.Ok) {
            silentRefreshBlocked = false
            if (credentials.rememberEnabled()) {
                credentials.save(cpf, password, name = result.value.name)
            }
        }
        // A new session means a new schedule to arm.
        return result.alsoReschedule()
    }

    /**
     * Automatic / 1-click login using Keystore-persisted credentials.
     * Designed for elderly users so they don't have to re-enter passwords.
     */
    suspend fun loginWithSavedCredentials(): ApiResult<User> {
        val saved = credentials.load() ?: return ApiResult.Err("Nenhuma credencial salva.")
        return login(saved.cpf, saved.password)
    }

    /**
     * [lgpdConsent] is the box the user actually ticked, never a constant. It was
     * hard-coded to `true` before, which meant the app claimed a consent its
     * owner had never been shown — for a Brazilian health app that is not
     * consent at all, whatever the server records.
     */
    suspend fun register(
        cpf: String,
        name: String,
        password: String,
        email: String?,
        phone: String?,
        lgpdConsent: Boolean,
        susCard: String? = null,
        gender: String? = null,
    ): ApiResult<User> {
        val result = authCall {
            api().register(
                RegisterRequest(
                    cpf = digitsOnly(cpf),
                    name = name.trim(),
                    password = password,
                    email = email?.trim()?.ifBlank { null },
                    phone = phone?.let { digitsOnly(it) }?.ifBlank { null },
                    lgpdConsent = lgpdConsent,
                    susCard = susCard?.let { digitsOnly(it) }?.ifBlank { null },
                    gender = gender?.ifBlank { null } ?: "not_informed",
                )
            )
        }
        // A brand-new account benefits most from not having to retype anything.
        if (result is ApiResult.Ok && credentials.rememberEnabled()) {
            credentials.save(cpf, password, name = name.trim())
        }
        return result.alsoReschedule()
    }

    /**
     * Starts password recovery. The server answers the same way whether or not
     * the account exists — deliberately, so the endpoint cannot be used to find
     * out which CPFs are registered. The screen repeats that wording.
     */
    suspend fun forgotPassword(cpf: String, email: String): ApiResult<Unit> = call {
        simpleResult(
            api().forgotPassword(
                ForgotPasswordRequest(cpf = digitsOnly(cpf), email = email.trim().lowercase())
            )
        )
    }

    /** Finishes recovery with the token the user received by e-mail. */
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = call {
        simpleResult(
            api().resetPassword(ResetPasswordRequest(token = token.trim(), newPassword = newPassword))
        )
    }

    /**
     * Pushes the phone's health records up so the automated e-mail report has
     * something to report. The server accepts them but never returns them, so
     * this is one-way on purpose — the device stays the readable copy.
     */
    suspend fun syncHealthRecords(
        vitalSigns: List<VitalSignsPayload>,
        consultations: List<ConsultationPayload>,
    ): ApiResult<User> = call {
        val r = api().updateProfile(
            ProfileUpdateRequest(
                healthVitalSigns = vitalSigns,
                medicalConsultations = consultations,
            )
        )
        envelopeValue(r) { it.user }
    }

    /**
     * Turns the automated caregiver report on or off and says where it goes.
     *
     * This is the switch behind the whole "relatório semanal" feature: with it on,
     * the server's report job e-mails the caregiver a period summary of doses
     * taken and missed, the health measurements logged on the phone, and the
     * consultations coming up. Every argument is optional so one field can be
     * changed without resending the rest.
     */
    suspend fun setReportSettings(
        enabled: Boolean? = null,
        frequency: String? = null,
        contactEmail: String? = null,
    ): ApiResult<User> = call {
        val r = api().updateProfile(
            ProfileUpdateRequest(
                reportAutoSendEnabled = enabled,
                reportFrequency = frequency?.takeIf { it == "weekly" || it == "monthly" },
                // Empty is meaningful: it tells the server to fall back to the
                // account e-mail, so it must not be collapsed to null here.
                reportContactEmail = contactEmail?.trim(),
            )
        )
        envelopeValue(r) { it.user }
    }

    // ---- Assistente de voz (Alexa / Echo Dot) ----

    suspend fun voiceAssistantConfig(): ApiResult<VoiceAssistantConfig> = call {
        val r = api().voiceAssistantConfig()
        envelopeValue(r) { it.config }
    }

    suspend fun setVoiceAssistantConfig(
        config: VoiceAssistantConfig,
    ): ApiResult<VoiceAssistantConfig> = call {
        val r = api().updateVoiceAssistantConfig(config)
        envelopeValue(r) { it.config }
    }

    /**
     * Sends one phrase through the configured assistant so the user can hear the
     * Echo Dot speak before trusting it with a real dose. `browser-tts` answers
     * `dispatched = false`, which the screen reports rather than treating as a
     * failure — there is no speaker for the server to reach in that mode.
     */
    suspend fun dispatchVoiceAssistant(
        message: String,
        medicationName: String = "",
        scheduleTime: String = "",
    ): ApiResult<VoiceAssistantDispatchData> = call {
        val r = api().dispatchVoiceAssistant(
            VoiceAssistantDispatchRequest(
                message = message.trim(),
                medicationName = medicationName.trim(),
                scheduleTime = scheduleTime.trim(),
            )
        )
        envelopeValue(r) { it }
    }

    suspend fun me(): ApiResult<User> = call {
        val r = api().me()
        envelopeValue(r) { it.user }
    }

    suspend fun updateProfile(request: ProfileUpdateRequest): ApiResult<User> = call {
        val r = api().updateProfile(request)
        envelopeValue(r) { it.user }
    }

    suspend fun setCaregivers(caregivers: List<Caregiver>, patientPhone: String?): ApiResult<User> {
        val request = ProfileUpdateRequest(
            phone = patientPhone?.let { digitsOnly(it) }?.ifBlank { null },
            caregivers = caregivers,
        )
        return updateProfile(request)
    }

    // ---- Medications ----

    suspend fun medications(): ApiResult<List<Medication>> = call {
        val r = api().getMedications()
        envelopeValue(r) { it.medications }
    }

    suspend fun createMedication(request: MedicationRequest): ApiResult<Medication> = call {
        val r = api().createMedication(request)
        envelopeValue(r) { it.medication }
    }.alsoReschedule()

    suspend fun updateMedication(id: String, request: MedicationRequest): ApiResult<Medication> = call {
        val r = api().updateMedication(id, request)
        envelopeValue(r) { it.medication }
    }.alsoReschedule()

    suspend fun deleteMedication(id: String): ApiResult<Unit> = call {
        val result = simpleResult(api().deleteMedication(id))
        // The phone-side extras (prescriber, pharmacy, treatment length) are keyed
        // by medication id. Left behind, they would resurface on whatever new
        // medication the server happened to hand that id to.
        if (result is ApiResult.Ok) local.deleteMedicationExtras(id)
        result
    }.alsoReschedule()

    // ---- History ----

    suspend fun history(limit: Int = 100): ApiResult<List<HistoryEntry>> = call {
        val r = api().getHistory(limit = limit)
        envelopeValue(r) { it.history }
    }

    suspend fun addHistory(request: HistoryRequest): ApiResult<Unit> = call {
        simpleResult(api().addHistory(request))
    }.alsoReschedule()

    suspend fun sendDeviceReport(request: DeviceReportRequest): ApiResult<Unit> = call {
        simpleResult(api().deviceReport(request))
    }

    suspend fun adherence(days: Int = 30): ApiResult<Adherence> = call {
        val r = api().getAdherence(days)
        envelopeValue(r) { it }
    }

    // ---- Prescriptions ----

    suspend fun prescriptions(): ApiResult<List<Prescription>> = call {
        val r = api().getPrescriptions()
        envelopeValue(r) { it.prescriptions }
    }

    suspend fun createPrescription(request: PrescriptionRequest): ApiResult<Prescription> = call {
        val r = api().createPrescription(request)
        envelopeValue(r) { it.prescription }
    }

    suspend fun deletePrescription(id: String): ApiResult<Unit> = call {
        simpleResult(api().deletePrescription(id))
    }

    suspend fun googlePlayConfig(): ApiResult<com.memoria.mobile.data.remote.GooglePlayConfig> = call {
        envelopeValue(api().googlePlayConfig()) { it }
    }

    suspend fun verifyGooglePlayPurchase(token: String): ApiResult<com.memoria.mobile.data.remote.GooglePlayEntitlement> = call {
        envelopeValue(api().verifyGooglePlayPurchase(com.memoria.mobile.data.remote.GooglePlayPurchaseRequest(token))) { it }
    }

    // ---- Admin ----

    suspend fun ownerStats(): ApiResult<OwnerStats> = call {
        val r = api().ownerStats()
        envelopeValue(r) { it }
    }

    // ---- Assinatura ----

    /**
     * Cancels a subscription contracted outside Google Play, before the app moved
     * to Google Play Billing. Google Play subscriptions are managed in the user's
     * Play account instead.
     */
    suspend fun cancelSubscription(): ApiResult<Unit> = call {
        simpleResult(api().cancelSubscription())
    }

    // ---- LGPD (direitos do titular) ----

    /**
     * Everything the server holds about the account, as raw JSON bytes.
     *
     * Deliberately not parsed: the right of access means handing over the whole
     * record, and re-serialising it through the app's DTOs would silently drop
     * any field this build does not model.
     */
    suspend fun exportData(): ApiResult<ByteArray> = call {
        val r = api().exportData()
        val bytes = r.body()?.bytes()
        if (r.isSuccessful && bytes != null) {
            ApiResult.Ok(bytes)
        } else {
            ApiResult.Err(serverError(r))
        }
    }

    /** Grants or revokes LGPD consent (`PUT /api/auth/consent`). */
    suspend fun setConsent(granted: Boolean): ApiResult<User> = call {
        val r = api().updateConsent(ConsentRequest(consent = granted))
        envelopeValue(r) { it.user }
    }

    /**
     * Erases the account and every record attached to it on the server, then
     * wipes what the phone kept — saved password, health notes, care network —
     * and cancels the alarms. A deletion that leaves the old data on the device
     * is not a deletion.
     */
    suspend fun deleteAccount(): ApiResult<Unit> = call {
        val result = simpleResult(api().deleteAccount())
        if (result is ApiResult.Ok) {
            credentials.clear()
            prefs.setString(KEY_SNOOZE, null)
            local.saveVitalSigns(emptyList())
            local.saveWellbeing(emptyList())
            local.saveSafety(emptyList())
            local.saveCareContacts(emptyList())
            local.saveConsultations(emptyList())
            local.saveEmergencyContacts(emptyList())
            local.clearMedicationExtras()
            logout()
        }
        result
    }

    // ---- Connectivity ----

    /** Health probe that reports WHY it failed, not merely that it did. */
    suspend fun checkServer(): ApiResult<Unit> = call {
        val r = api().health(apiProvider.healthUrl(baseRoot))
        if (r.isSuccessful && r.body()?.get("status") == "ok") {
            ApiResult.Ok(Unit)
        } else {
            ApiResult.Err(
                "O endereço respondeu, mas não parece ser o servidor MemorIA (HTTP ${r.code()})."
            )
        }
    }

    suspend fun serverReachable(): Boolean = checkServer() is ApiResult.Ok

    // ---- Helpers ----

    private suspend fun authCall(block: suspend () -> Response<Envelope<AuthData>>): ApiResult<User> =
        call {
            val r = block()
            val body = r.body()
            if (r.isSuccessful && body?.success == true && body.data?.token != null) {
                session.token = body.data.token
                prefs.setToken(body.data.token)
                val user = body.data.user
                if (user != null) ApiResult.Ok(user.withTemporaryFullAccess()) else ApiResult.Err("Resposta inválida do servidor.")
            } else {
                ApiResult.Err(errorMessage(r))
            }
        }

    private fun simpleResult(response: Response<SimpleResponse>): ApiResult<Unit> {
        val body = response.body()
        return if (response.isSuccessful && body?.success == true) {
            ApiResult.Ok(Unit)
        } else {
            val msg = body?.message?.takeIf { it.isNotBlank() } ?: serverError(response)
            ApiResult.Err(msg)
        }
    }

    private inline fun <B, T> envelopeValue(
        response: Response<Envelope<B>>,
        extract: (B) -> T?,
    ): ApiResult<T> {
        val body = response.body()
        return if (response.isSuccessful && body?.success == true) {
            val value = body.data?.let(extract)?.let { unlockPlan(it) }
            if (value != null) ApiResult.Ok(value) else ApiResult.Err("Resposta vazia do servidor.")
        } else {
            ApiResult.Err(errorMessage(response))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unlockPlan(value: T): T =
        if (value is User) value.withTemporaryFullAccess() as T else value

    private suspend inline fun <T> call(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (e: Exception) {
        ApiResult.Err(networkError(e))
    }

    /**
     * Turns a network/parsing exception into something an 80-year-old can act on.
     * The raw `e.message` used to reach the screen verbatim — in English, and for
     * a stalled connection often just the word "timeout". Every message names the
     * server in use, because a wrong address is the likeliest cause.
     */
    private fun networkError(e: Throwable): String = when (e) {
        // SocketTimeoutException is an InterruptedIOException — keep it first.
        is SocketTimeoutException ->
            "O servidor demorou demais a responder. Verifique a internet e o endereço em “Servidor” ($baseRoot)."
        is InterruptedIOException ->
            "A ligação demorou demais e foi cancelada. Tente de novo ou verifique o endereço em “Servidor” ($baseRoot)."
        is UnknownHostException ->
            "Não foi possível encontrar o servidor. Verifique a internet e o endereço em “Servidor” ($baseRoot)."
        is SSLException ->
            "Falha na ligação segura com o servidor ($baseRoot). Confirme também a data e a hora do telemóvel."
        is ConnectException ->
            "Não foi possível ligar ao servidor. Verifique a internet e o endereço em “Servidor” ($baseRoot)."
        is JsonDataException, is JsonEncodingException ->
            "O servidor respondeu num formato inesperado. Verifique o endereço em “Servidor” ($baseRoot)."
        is IOException ->
            "Falha de rede. Verifique a conexão e o endereço em “Servidor” ($baseRoot)."
        else -> e.message ?: "Erro inesperado. Tente novamente."
    }

    private fun <B> errorMessage(response: Response<Envelope<B>>): String {
        // A 2xx with success=false carries the message in the body...
        response.body()?.message?.let { if (it.isNotBlank()) return it }
        // ...but on 4xx/5xx the body is null and the JSON is in errorBody.
        return serverError(response)
    }

    /** Reads the backend's JSON `message`/`error` from errorBody, else a fallback. */
    private fun <T> serverError(response: Response<T>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val json = org.json.JSONObject(raw)
                val msg = json.optString("message").ifBlank { json.optString("error") }
                if (msg.isNotBlank()) return msg
            }
        }
        return when (response.code()) {
            401 -> "Sessão inválida ou credenciais incorretas."
            403 -> "Acesso negado."
            404 -> "Recurso não encontrado."
            in 500..599 -> "Erro no servidor. Tente novamente."
            else -> "Erro na requisição (${response.code()})."
        }
    }

    private fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    private companion object {
        const val KEY_SNOOZE = "reminder_snooze_minutes"
        const val KEY_SOUND = "reminder_sound"
        const val KEY_LOW_STOCK_ALERTS = "low_stock_alerts_enabled"
        const val KEY_EMERGENCY_MODE = "emergency_alert_mode"
        const val KEY_THEME_PRESET = "theme_preset"
        const val DEFAULT_SNOOZE = 10
        /** The web's `settings.notificationSound || 'system'`. */
        const val DEFAULT_SOUND = "system"

        /** Standard brand blue default (`BluePrimary` #0060F8). */
        const val DEFAULT_THEME_PRESET = "pastel-azul"
    }

    /**
     * Re-arms the reminder window after a call that changed the schedule.
     * Only on success — a failed write changed nothing, and re-reading the
     * medications after every network error would just burn battery.
     */
    private fun <T> ApiResult<T>.alsoReschedule(): ApiResult<T> = also {
        if (it is ApiResult.Ok) onScheduleChanged?.invoke()
    }
}
