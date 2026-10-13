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
    /** Web "Tratamento": auto | sra | sr. */
    val title: String = "auto",
)

/**
 * The medication fields the web form collects but the backend has no column for
 * — treatment length, continuous use, prescriber and dispensing pharmacy. On the
 * web they live in `localStorage`; here they live in DataStore, keyed by the
 * server's medication id.
 *
 * Kept apart from [com.memoria.mobile.data.remote.Medication] on purpose: sending
 * them in `MedicationRequest` would be silently dropped by
 * `pickMedicationPayload()`, which would look like a save that worked.
 *
 * `supplier`, by contrast, IS a server column and travels with the medication —
 * it is what the backend messages when the stock runs critical.
 */
@JsonClass(generateAdapter = true)
data class MedicationExtras(
    val medicationId: String,
    /** Days the treatment is meant to last; null for an open-ended one. */
    val treatmentDurationDays: Int? = null,
    val continuousUse: Boolean = false,
    val prescribingDoctor: String = "",
    val dispensingPharmacy: String = "",
) {
    val isEmpty: Boolean
        get() = treatmentDurationDays == null &&
            !continuousUse &&
            prescribingDoctor.isBlank() &&
            dispensingPharmacy.isBlank()
}

/**
 * How a medication looks on screen — the web form's "Ícone do Medicamento":
 * pharmaceutical form (`comprimido`, `capsula`, `gotas`…), pill shape and colours.
 * The backend has no column for any of it (on the web it lives in
 * `localStorage`), so it stays on the phone, keyed by the server's medication id.
 */
@JsonClass(generateAdapter = true)
data class MedicationLook(
    val medicationId: String,
    val medicationForm: String = "comprimido",
    val pillShape: String = "round",
    val pillColor: String = "#e5e7eb",
    val capsuleColor1: String = "#e5e7eb",
    val capsuleColor2: String = "#2f7dff",
)

/** Texto que o OCR leu numa foto de receita. O servidor antigo não tem coluna para isto. */
@JsonClass(generateAdapter = true)
data class PrescriptionReading(
    val prescriptionId: String,
    val text: String,
)
