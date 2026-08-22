package com.memoria.mobile.data.local

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Typed list storage on top of [PreferencesStore]'s JSON slots — the phone-side
 * system of record for everything the backend has no table for (see
 * [LocalRecords]).
 *
 * Reads are deliberately forgiving: a slot whose JSON no longer parses (an older
 * build wrote a different shape) yields an empty list instead of throwing, so a
 * schema change degrades to "no history yet" rather than crashing the screen.
 *
 * The adapters are concrete rather than reified because a generic helper would
 * have to be `inline` + `reified`, and an inline member cannot touch the private
 * [moshi] instance.
 */
class LocalStore(private val prefs: PreferencesStore) {

    private val moshi: Moshi = Moshi.Builder().build()

    private fun <T> listAdapter(type: Class<T>): JsonAdapter<List<T>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, type))

    private suspend fun <T> read(slot: String, type: Class<T>): List<T> {
        val raw = prefs.string(slot) ?: return emptyList()
        return runCatching { listAdapter(type).fromJson(raw) }.getOrNull().orEmpty()
    }

    private suspend fun <T> write(slot: String, type: Class<T>, items: List<T>) {
        prefs.setString(slot, listAdapter(type).toJson(items))
    }

    suspend fun vitalSigns(): List<VitalSignsRecord> = read(VITAL_SIGNS, VitalSignsRecord::class.java)

    suspend fun saveVitalSigns(items: List<VitalSignsRecord>) =
        write(VITAL_SIGNS, VitalSignsRecord::class.java, items.take(MAX_HEALTH_RECORDS))

    suspend fun wellbeing(): List<WellbeingRecord> = read(WELLBEING, WellbeingRecord::class.java)

    suspend fun saveWellbeing(items: List<WellbeingRecord>) =
        write(WELLBEING, WellbeingRecord::class.java, items.take(MAX_HEALTH_RECORDS))

    suspend fun safety(): List<SafetyRecord> = read(SAFETY, SafetyRecord::class.java)

    suspend fun saveSafety(items: List<SafetyRecord>) =
        write(SAFETY, SafetyRecord::class.java, items.take(MAX_HEALTH_RECORDS))

    suspend fun careContacts(): List<CareContact> = read(CARE_CONTACTS, CareContact::class.java)

    suspend fun saveCareContacts(items: List<CareContact>) =
        write(CARE_CONTACTS, CareContact::class.java, items)

    suspend fun consultations(): List<MedicalConsultation> =
        read(CONSULTATIONS, MedicalConsultation::class.java)

    suspend fun saveConsultations(items: List<MedicalConsultation>) =
        write(CONSULTATIONS, MedicalConsultation::class.java, items)

    suspend fun emergencyContacts(): List<EmergencyContactRecord> =
        read(EMERGENCY_CONTACTS, EmergencyContactRecord::class.java)

    suspend fun saveEmergencyContacts(items: List<EmergencyContactRecord>) =
        write(EMERGENCY_CONTACTS, EmergencyContactRecord::class.java, items)

    private companion object {
        const val VITAL_SIGNS = "health_vital_signs"
        const val WELLBEING = "health_wellbeing"
        const val SAFETY = "health_safety"
        const val CARE_CONTACTS = "care_network_contacts"
        const val CONSULTATIONS = "medical_consultations"
        const val EMERGENCY_CONTACTS = "emergency_contacts"

        /** Bounded exactly like the web app's `slice(0, 50)`. */
        const val MAX_HEALTH_RECORDS = 50
    }
}
