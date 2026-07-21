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
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.RegisterRequest
import com.memoria.mobile.data.remote.SessionState
import com.memoria.mobile.data.remote.SimpleResponse
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

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

    val tokenFlow: Flow<String?> = prefs.tokenFlow
    val baseUrlFlow: Flow<String?> = prefs.baseUrlFlow

    /** Load persisted session at startup. */
    suspend fun bootstrap() {
        prefs.baseUrl()?.takeIf { it.isNotBlank() }?.let { baseRoot = ApiProvider.normalizeRoot(it) }
        session.token = prefs.token()
    }

    fun currentBaseUrl(): String = baseRoot

    fun isLoggedIn(): Boolean = !session.token.isNullOrBlank()

    private fun api(): ApiService = apiProvider.service(baseRoot)

    suspend fun setBaseUrl(url: String) {
        baseRoot = ApiProvider.normalizeRoot(url)
        prefs.setBaseUrl(baseRoot)
    }

    suspend fun logout() {
        session.token = null
        prefs.clearToken()
    }

    // ---- Auth ----

    suspend fun login(cpf: String, password: String): ApiResult<User> =
        authCall { api().login(LoginRequest(cpf = digitsOnly(cpf), password = password)) }

    suspend fun register(
        cpf: String,
        name: String,
        password: String,
        email: String?,
        phone: String?,
    ): ApiResult<User> = authCall {
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
    }

    suspend fun updateMedication(id: String, request: MedicationRequest): ApiResult<Medication> = call {
        val r = api().updateMedication(id, request)
        envelopeValue(r) { it.medication }
    }

    suspend fun deleteMedication(id: String): ApiResult<Unit> = call {
        simpleResult(api().deleteMedication(id))
    }

    // ---- History ----

    suspend fun history(limit: Int = 100): ApiResult<List<HistoryEntry>> = call {
        val r = api().getHistory(limit = limit)
        envelopeValue(r) { it.history }
    }

    suspend fun addHistory(request: HistoryRequest): ApiResult<Unit> = call {
        simpleResult(api().addHistory(request))
    }

    suspend fun adherence(days: Int = 30): ApiResult<Adherence> = call {
        val r = api().getAdherence(days)
        envelopeValue(r) { it }
    }

    // ---- Connectivity ----

    suspend fun serverReachable(): Boolean = try {
        val r = api().health(apiProvider.healthUrl(baseRoot))
        r.isSuccessful && r.body()?.get("status") == "ok"
    } catch (_: Exception) {
        false
    }

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
        ApiResult.Err(e.message ?: "Falha de rede. Verifique a conexão e o endereço do servidor.")
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
}
