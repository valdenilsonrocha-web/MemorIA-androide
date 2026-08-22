package com.memoria.mobile.data

import com.memoria.mobile.BuildConfig
import com.memoria.mobile.data.local.PreferencesStore
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.ApiProvider
import com.memoria.mobile.data.remote.ApiService
import com.memoria.mobile.data.remote.AuthData
import com.memoria.mobile.data.remote.Caregiver
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
            credentials.save(cpf, password)
        }
        // A new session means a new schedule to arm.
        return result.alsoReschedule()
    }

    suspend fun register(
        cpf: String,
        name: String,
        password: String,
        email: String?,
        phone: String?,
    ): ApiResult<User> {
        val result = authCall {
            api().register(
                RegisterRequest(
                    cpf = digitsOnly(cpf),
                    name = name.trim(),
                    password = password,
                    email = email?.trim()?.ifBlank { null },
                    phone = phone?.let { digitsOnly(it) }?.ifBlank { null },
                    lgpdConsent = true,
                )
            )
        }
        // A brand-new account benefits most from not having to retype anything.
        if (result is ApiResult.Ok && credentials.rememberEnabled()) {
            credentials.save(cpf, password)
        }
        return result.alsoReschedule()
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
        simpleResult(api().deleteMedication(id))
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
        const val DEFAULT_SNOOZE = 10
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
