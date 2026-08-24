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
    val subscriptionStatus: String? = null,
    val isPremium: Boolean = false,
    /** ISO instant of the LGPD consent, or null once revoked. */
    val consentDate: String? = null,
    val lastSync: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
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
    val capturedAt: String? = null,
    val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PrescriptionRequest(
    val fileName: String,
    val imageData: String,
    val capturedAt: String? = null,
)

// ---- Payments ------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class PaymentConfig(
    val gateway: String = "stripe",
    val configured: Boolean = false,
    val plans: PaymentPlans? = null,
)

@JsonClass(generateAdapter = true)
data class PaymentPlans(
    val monthly: PaymentPlan? = null,
    val annual: PaymentPlan? = null,
)

@JsonClass(generateAdapter = true)
data class PaymentPlan(
    val label: String = "",
    val interval: String = "",
    val amountInCents: Int = 0,
    val displayPrice: String = "",
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

// ---- Checkout (Mercado Pago) ----

@JsonClass(generateAdapter = true)
data class CheckoutRequest(
    val plan: String,
    val customerEmail: String,
    val invoiceEmail: String,
)

/**
 * `url` is the Mercado Pago `init_point` — the gateway's own hosted page, which
 * is where the card is typed on the website too. The app opens it in a Custom
 * Tab so the user stays inside MemorIA.
 */
@JsonClass(generateAdapter = true)
data class CheckoutSession(
    val sessionId: String? = null,
    val url: String? = null,
    val plan: String = "monthly",
    val trialDays: Int = 7,
    val amount: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class SubscriptionStatusRequest(
    val sessionId: String,
)

@JsonClass(generateAdapter = true)
data class SubscriptionStatusData(
    val plan: String = "monthly",
    val amount: Double = 0.0,
    val sessionId: String? = null,
    val trialDays: Int = 7,
    val subscriptionStatus: String? = null,
)
