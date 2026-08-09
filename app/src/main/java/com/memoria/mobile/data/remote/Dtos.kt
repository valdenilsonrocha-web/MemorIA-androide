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
    val medicationId: String? = null,
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
