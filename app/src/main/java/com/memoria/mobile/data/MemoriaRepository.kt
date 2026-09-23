package com.memoria.mobile.data

import com.memoria.mobile.BuildConfig
import com.memoria.mobile.data.local.PreferencesStore
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.ApiProvider
import com.memoria.mobile.data.remote.ApiService
import com.memoria.mobile.data.remote.AuthData
import com.memoria.mobile.data.remote.Caregiver
import com.memoria.mobile.data.remote.CardInput
import com.memoria.mobile.data.remote.CardTokenizer
import com.memoria.mobile.data.remote.ConsentRequest
import com.memoria.mobile.data.remote.ConsultationPayload
import com.memoria.mobile.data.remote.ForgotPasswordRequest
import com.memoria.mobile.data.remote.ResetPasswordRequest
import com.memoria.mobile.data.remote.VitalSignsPayload
import com.memoria.mobile.data.remote.VoiceAssistantConfig
import com.memoria.mobile.data.remote.VoiceAssistantDispatchData
import com.memoria.mobile.data.remote.VoiceAssistantDispatchRequest
import com.memoria.mobile.data.remote.SubscribeRequest
import com.memoria.mobile.data.remote.SubscriptionStatusData
import com.memoria.mobile.data.remote.SubscriptionStatusRequest
import com.memoria.mobile.data.remote.Envelope
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.data.remote.HistoryRequest
import com.memoria.mobile.data.remote.LoginRequest
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.data.remote.MedicationRequest
import com.memoria.mobile.data.local.CredentialStore
import com.memoria.mobile.data.local.LocalStore
import com.memoria.mobile.data.remote.OwnerStats
import com.memoria.mobile.data.remote.PaymentConfig
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.data.remote.PrescriptionRequest
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.RegisterRequest
import com.memoria.mobile.data.remote.SessionState
import com.memoria.mobile.data.remote.SimpleResponse
import com.memoria.mobile.data.remote.User
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.flow.Flow
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

    /** Card-to-token exchange with Mercado Pago, on its own unauthenticated client. */
    private val cardTokenizer = CardTokenizer()

    /**
     * Fired whenever something that affects the reminder schedule changes — a
     * medication added, edited or removed, a dose recorded, a login or logout.
     * [AppGraph] wires it to the alarm scheduler so no screen has to remember to.
     */
    @Volatile
    var onScheduleChanged: (() -> Unit)? = null

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
     * Ends the session but deliberately KEEPS the remembered credentials, so a
     * user who logs out can get back in without retyping. Handing the phone to
     * someone else is the case that wants them gone — [forgetCredentials] does
     * that, and Settings puts it next to the logout button.
     */
    /**
     * Ends the session because the SERVER rejected the token, not because the user
     * asked. Keeps the remembered CPF and password on purpose: a token that
     * expired after its 7 days is not a reason to make someone retype credentials
     * they already trusted this phone with — the login screen comes pre-filled and
     * getting back in is one tap.
     *
     * Idempotent: several requests can fail at once when a token dies, and each
     * one calls this.
     */
    suspend fun expireSession() {
        if (session.token == null && prefs.token() == null) return
        session.token = null
        prefs.clearToken()
        // Cancels every pending alarm: a logged-out phone must not keep buzzing
        // about medication it can no longer record.
        onScheduleChanged?.invoke()
    }

    suspend fun logout() {
        session.token = null
        prefs.clearToken()
        // Clears every pending alarm: a logged-out phone must not keep buzzing
        // about someone else's medication.
        onScheduleChanged?.invoke()
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
        if (result is ApiResult.Ok && credentials.rememberEnabled()) {
            credentials.save(cpf, password, name = result.value.name)
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

    /**
     * Web `apiService.sendWhatsAppMessage(to, message, metadata)`. Ok(success)
     * when the server answered 2xx; Err when it refused (non-2xx) or the network
     * failed — the two cases the web tells apart with different toasts.
     */
    suspend fun sendWhatsAppMessage(
        to: String,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    ): ApiResult<Boolean> = call {
        val r = api().sendWhatsAppMessage(
            com.memoria.mobile.data.remote.WhatsAppSendRequest(to = to, message = message, metadata = metadata)
        )
        if (r.isSuccessful) ApiResult.Ok(r.body()?.success == true) else ApiResult.Err(serverError(r))
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

    // ---- Plans / payments ----

    suspend fun paymentConfig(): ApiResult<PaymentConfig> = call {
        val r = api().paymentConfig()
        envelopeValue(r) { it }
    }

    // ---- Admin ----

    suspend fun ownerStats(): ApiResult<OwnerStats> = call {
        val r = api().ownerStats()
        envelopeValue(r) { it }
    }

    // ---- Assinatura ----

    /**
     * Subscribes with a card the app already turned into a Mercado Pago token.
     *
     * The whole flow is native: the card is typed on a MemorIA screen, swapped
     * for a token directly with Mercado Pago on the device, and only the token
     * travels to the MemorIA backend — which rejects the request outright if it
     * ever carries card fields. No browser, no website, no page in between.
     */
    suspend fun subscribeWithCard(
        plan: String,
        cardTokenId: String,
        payerEmail: String,
    ): ApiResult<SubscriptionStatusData> = call {
        val r = api().subscribeWithCard(
            SubscribeRequest(
                plan = plan,
                cardTokenId = cardTokenId,
                payerEmail = payerEmail.trim(),
            )
        )
        envelopeValue(r) { it }
    }

    /** Tokenises a card on the device; the number never reaches this backend. */
    suspend fun tokenizeCard(publicKey: String, card: CardInput): Result<String> =
        cardTokenizer.tokenize(publicKey, card)

    /**
     * Confirms with the gateway whether the subscription went through.
     *
     * A 402 here is not a failure to report — it is Mercado Pago saying "not
     * confirmed yet", which is the normal answer in the seconds after checkout.
     */
    suspend fun subscriptionStatus(sessionId: String): ApiResult<SubscriptionStatusData> = call {
        val r = api().subscriptionStatus(SubscriptionStatusRequest(sessionId = sessionId))
        envelopeValue(r) { it }
    }

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
                if (user != null) ApiResult.Ok(user) else ApiResult.Err("Resposta inválida do servidor.")
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
            val value = body.data?.let(extract)
            if (value != null) ApiResult.Ok(value) else ApiResult.Err("Resposta vazia do servidor.")
        } else {
            ApiResult.Err(errorMessage(response))
        }
    }

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

        /** Same default the web app applies (`settings.themePreset`). */
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
