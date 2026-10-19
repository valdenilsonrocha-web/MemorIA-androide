package com.memoria.mobile.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs mirror the MemorIA backend contract (see the backend `controllers`
 * directory). Note: `/` followed by `*` must not appear literally in a Kotlin
 * comment — unlike Java, Kotlin NESTS block comments, so it would open an inner
 * comment and leave this KDoc unterminated (the file failed to compile).
 * Every response is the envelope `{ success, message, data: {...} }`.
 */

@JsonClass(generateAdapter = true)
data class Envelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val count: Int? = null,
    val data: T? = null,
    val error: String? = null,
)

/** Envelope for endpoints that return no `data` payload (delete, add-history). */
@JsonClass(generateAdapter = true)
data class SimpleResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

// ---- Auth ----------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val cpf: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val cpf: String,
    val name: String,
    val password: String,
    val email: String? = null,
    val phone: String? = null,
    val lgpdConsent: Boolean = true,
    /** `registerSusCard` da web — só dígitos, até 15. */
    val susCard: String? = null,
    /** `registerGender` da web: not_informed | female | male | other. */
    val gender: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthData(
    val user: User? = null,
    val token: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserData(
    val user: User? = null,
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val cpfMasked: String = "",
    val city: String = "",
    val state: String = "",
    val healthUnit: String = "",
    val isActive: Boolean = true,
    val isAdmin: Boolean = false,
    val caregivers: List<Caregiver> = emptyList(),
    val subscriptionProvider: String? = null,
    val subscriptionPlan: String? = null,
    val subscriptionExpiresAt: String? = null,
    val subscriptionStatus: String? = null,
    val isPremium: Boolean = false,
    val isTrialActive: Boolean = false,
    val trialDaysRemaining: Int? = null,
    val trialExpired: Boolean = false,
    val trialEndsAt: String? = null,
    val firstLoginAt: String? = null,
    /**
     * Automated e-mail report the backend sends to the caregiver, carrying the
     * medication summary, the health measurements and the upcoming
     * consultations. Null on a server old enough not to send the block.
     */
    val reportSettings: ReportSettings? = null,
    /** ISO instant of the LGPD consent, or null once revoked. */
    val consentDate: String? = null,
    val lastSync: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * `reportSettings` exactly as `sanitizeUserOutput()` emits it.
 *
 * The three writable fields are named differently on the way back up —
 * `reportAutoSendEnabled` / `reportFrequency` / `reportContactEmail` on
 * [ProfileUpdateRequest]. That asymmetry is the backend's contract, not a slip.
 */
@JsonClass(generateAdapter = true)
data class ReportSettings(
    val enabled: Boolean = false,
    /** weekly | monthly */
    val frequency: String = "weekly",
    val contactEmail: String = "",
    val lastReportSentAt: String? = null,
    val lastReportPeriodKey: String? = null,
)

@JsonClass(generateAdapter = true)
data class Caregiver(
    val name: String = "",
    val phone: String = "",
    val relation: String = "",
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val city: String? = null,
    val state: String? = null,
    val caregivers: List<Caregiver>? = null,
    /**
     * Health records the server stores for the automated report. Null means
     * "do not touch" — the server only overwrites a field the client sends, so
     * a profile edit never wipes measurements it was not asked to change.
     */
    val healthVitalSigns: List<VitalSignsPayload>? = null,
    val medicalConsultations: List<ConsultationPayload>? = null,
    /**
     * Automated report. Null means "leave it as it is", so saving a phone number
     * can never silently switch the caregiver's weekly e-mail off.
     */
    val reportAutoSendEnabled: Boolean? = null,
    /** weekly | monthly */
    val reportFrequency: String? = null,
    val reportContactEmail: String? = null,
)

// ---- Medications ---------------------------------------------------------

@JsonClass(generateAdapter = true)
data class MedicationListData(
    val medications: List<Medication> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MedicationData(
    val medication: Medication? = null,
)

@JsonClass(generateAdapter = true)
data class Medication(
    val id: String? = null,
    val name: String = "",
    val dosage: String = "",
    val frequency: String = "daily", // daily | alternate | weekly
    val times: List<String> = emptyList(), // ["08:00","20:00"]
    val weekDays: List<Int>? = null, // 0..6 (Sun..Sat) for weekly
    val instructions: String? = null,
    val stock: Int = 0,
    val active: Boolean = true,
    val supplier: Supplier? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class Supplier(
    val name: String = "",
    val phone: String = "",
)

@JsonClass(generateAdapter = true)
data class MedicationRequest(
    val name: String,
    val dosage: String,
    val frequency: String,
    val times: List<String>,
    val weekDays: List<Int>? = null,
    val instructions: String? = null,
    val stock: Int = 0,
    val active: Boolean = true,
    val supplier: Supplier? = null,
)

// ---- History -------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class HistoryListData(
    val history: List<HistoryEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HistoryEntry(
    val id: String? = null,
    val medicationId: String? = null,
    val medicationName: String = "",
    val dosage: String = "",
    val scheduleTime: String = "",
    val status: String = "", // taken | missed | snoozed
    val takenAt: String? = null,
    val scheduledFor: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class HistoryRequest(
    // Não-nulável de propósito: no servidor `medication_id` é UUID NOT NULL com
    // chave estrangeira, portanto omiti-lo era um pedido inválido — o contrato
    // dizia opcional e o backend respondia 500. O compilador passa a garanti-lo.
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val scheduleTime: String,
    val status: String,
    val scheduledFor: String,
    val takenAt: String? = null,
    val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class Adherence(
    val period: String = "",
    val total: Int = 0,
    val taken: Int = 0,
    val missed: Int = 0,
    val adherenceRate: String = "0%",
)

// ---- Prescriptions -------------------------------------------------------

@JsonClass(generateAdapter = true)
data class PrescriptionListData(
    val prescriptions: List<Prescription> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PrescriptionData(
    val prescription: Prescription? = null,
)

/**
 * `imageData` is a `data:image/...;base64,...` URL — the same shape the web app
 * posts. The backend caps it at 5 MB of decoded bytes, so the camera/gallery
 * picture is downscaled and re-encoded before it gets here.
 */
@JsonClass(generateAdapter = true)
data class Prescription(
    val id: String? = null,
    val fileName: String = "receita.jpg",
    val imageData: String = "",
    val ocrText: String = "",
    val capturedAt: String? = null,
    val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PrescriptionRequest(
    val fileName: String,
    val imageData: String,
    val ocrText: String? = null,
    val capturedAt: String? = null,
)

// ---- Admin (owner dashboard) --------------------------------------------

@JsonClass(generateAdapter = true)
data class OwnerStats(
    val overview: OwnerOverview? = null,
    val byGender: List<CountBucket> = emptyList(),
    val byState: List<CountBucket> = emptyList(),
    val byCity: List<CountBucket> = emptyList(),
    val byAge: List<AgeBucket> = emptyList(),
    val topMedications: List<CountBucket> = emptyList(),
    val signupTrend: List<TrendPoint> = emptyList(),
    val recentSignups: List<RecentSignup> = emptyList(),
    val generatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class OwnerOverview(
    val totalUsers: Int = 0,
    val newToday: Int = 0,
    val newThisWeek: Int = 0,
    val newThisMonth: Int = 0,
    val activeUsers: Int = 0,
    val totalMedications: Int = 0,
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val adherenceRate: String? = null,
)

/**
 * One `GROUP BY` row. The grouped column differs per query — `gender`, `state`,
 * `city`, or the medication `name` — so every one is optional and [label] picks
 * whichever came back.
 */
@JsonClass(generateAdapter = true)
data class CountBucket(
    val gender: String? = null,
    val state: String? = null,
    val city: String? = null,
    val name: String? = null,
    val count: Int = 0,
) {
    val label: String
        get() = listOfNotNull(name, city, state, gender)
            .firstOrNull { it.isNotBlank() }
            ?: "Não informado"
}

@JsonClass(generateAdapter = true)
data class AgeBucket(
    val group: String = "",
    val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TrendPoint(
    val date: String = "",
    val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RecentSignup(
    val id: String? = null,
    val name: String = "",
    val email: String = "",
    val city: String = "",
    val state: String = "",
    val gender: String = "not_informed",
    val createdAt: String? = null,
    val lastSync: String? = null,
)

// ---- LGPD ----

@JsonClass(generateAdapter = true)
data class ConsentRequest(
    /** true grants, false revokes — the server stamps or clears `consentDate`. */
    val consent: Boolean,
)

// ---- Recuperação de senha ----

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    val cpf: String,
    val email: String,
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

// ---- Saúde sincronizada com o servidor ----

/**
 * Shapes accepted by `PUT /api/auth/profile`. The server sanitises and clamps
 * every field, then keeps them for the automated e-mail report — it does NOT
 * return them, so the phone stays the readable copy.
 */
@JsonClass(generateAdapter = true)
data class VitalSignsPayload(
    val date: String,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val heartRate: Int? = null,
    val spo2: Double? = null,
    val glucose: Double? = null,
    val glucoseContext: String = "random",
    val hba1c: Double? = null,
    val temperature: Double? = null,
)

@JsonClass(generateAdapter = true)
data class ConsultationPayload(
    val id: String,
    val dateTime: String,
    val professional: String,
    val location: String = "",
    val notes: String = "",
)

// ---- Voice assistant (Alexa / Echo Dot) ----------------------------------

/**
 * `GET/PUT /api/integrations/voice-assistant/config`.
 *
 * The webhook is what actually reaches the speaker: the MemorIA server posts the
 * reminder to it, and whatever sits behind it (an Alexa skill, an IFTTT hook, a
 * Home Assistant endpoint) speaks it on the Echo Dot. So `webhookUrl` is
 * mandatory for every provider except `browser-tts`, which speaks locally and
 * has nothing to post to — the server rejects the save otherwise.
 */
@JsonClass(generateAdapter = true)
data class VoiceAssistantConfig(
    val enabled: Boolean = false,
    /** alexa | google | webhook | browser-tts */
    val provider: String = "alexa",
    val deviceName: String = "",
    val webhookUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class VoiceAssistantConfigData(
    val config: VoiceAssistantConfig? = null,
)

@JsonClass(generateAdapter = true)
data class VoiceAssistantDispatchRequest(
    val message: String = "",
    val medicationName: String = "",
    val scheduleTime: String = "",
)

@JsonClass(generateAdapter = true)
data class VoiceAssistantDispatchData(
    val dispatched: Boolean = false,
    /** "browser-tts" when the server only prepared the phrase instead of posting it. */
    val mode: String? = null,
)

// ---- Notification health (POST /notifications/device-report) -------------

/**
 * One local reminder the phone tried to show. [id] is stable for that attempt,
 * so the phone can resend its whole log and the server keeps one copy.
 */
@JsonClass(generateAdapter = true)
data class DeviceReportEvent(
    val id: String,
    /** dose | stock | consultation */
    val kind: String,
    val label: String? = null,
    val scheduledFor: String? = null,
    val firedAt: String,
    /** shown | blocked (Android refused to show it) */
    val outcome: String,
    /** How long after the planned time the alarm actually rang. */
    val delayMinutes: Int? = null,
)

/**
 * What Android lets the app do right now. Any `false` here is a reminder the
 * patient will not see or hear, whatever the server sends.
 */
@JsonClass(generateAdapter = true)
data class DeviceReportRequest(
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null,
    val notificationsEnabled: Boolean? = null,
    val doseChannelEnabled: Boolean? = null,
    val exactAlarmAllowed: Boolean? = null,
    val batteryUnrestricted: Boolean? = null,
    val fullScreenAllowed: Boolean? = null,
    val scheduledAlarms: Int? = null,
    val events: List<DeviceReportEvent> = emptyList(),
)
