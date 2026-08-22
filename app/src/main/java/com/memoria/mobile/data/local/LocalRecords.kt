package com.memoria.mobile.data.local

import com.squareup.moshi.JsonClass

/**
 * Records the web app keeps in `localStorage` rather than on the server: health
 * measurements, the care network, and scheduled consultations. The backend has
 * no table for any of them, so the phone is the system of record — they live in
 * DataStore as JSON, mirroring the keys `app.js` uses (`healthVitalSigns`,
 * `healthWellbeing`, `healthSafety`, `careNetworkContacts`,
 * `medicalConsultations`, `emergencyContacts`).
 *
 * `date` is an ISO-8601 instant, newest first — the same ordering the web keeps.
 */

@JsonClass(generateAdapter = true)
data class VitalSignsRecord(
    val date: String,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val heartRate: Int? = null,
    val spo2: Double? = null,
    val glucose: Double? = null,
    val glucoseContext: String = "fasting",
    val hba1c: Double? = null,
    val temperature: Double? = null,
)

@JsonClass(generateAdapter = true)
data class WellbeingRecord(
    val date: String,
    val sleepQuality: Int? = null,
    val sleepHours: Double? = null,
    val hydration: Double? = null,
    val mood: Int? = null,
    val moodNote: String = "",
    val activityType: String = "",
    val activityDuration: Int? = null,
)

@JsonClass(generateAdapter = true)
data class SafetyRecord(
    val date: String,
    val weight: Double? = null,
    val height: Double? = null,
    val sex: String = "auto",
    val bmi: Double? = null,
    val careLevel: Int? = null,
)

/** Doctor, hospital, clinic, pharmacy or emergency contact. */
@JsonClass(generateAdapter = true)
data class CareContact(
    val id: String,
    val type: String = "medico",
    val name: String = "",
    val info: String = "",
    val notes: String = "",
)

@JsonClass(generateAdapter = true)
data class MedicalConsultation(
    val id: String,
    /** Local date-time, `yyyy-MM-dd'T'HH:mm` — no zone, like `datetime-local`. */
    val dateTime: String = "",
    val professional: String = "",
    val location: String = "",
    val notes: String = "",
)

/**
 * Emergency contact shown on the profile. It doubles as the caregiver list the
 * backend stores on the user (`PUT /api/auth/profile`), so saving one here also
 * pushes name/phone/relation upstream.
 */
@JsonClass(generateAdapter = true)
data class EmergencyContactRecord(
    val id: String,
    val name: String = "",
    val relation: String = "",
    val phone: String = "",
    val email: String = "",
)
