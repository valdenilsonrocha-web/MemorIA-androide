package com.memoria.mobile.data.local

import com.memoria.mobile.di.MoshiProvider
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
/** `uiSetting` names of the web `localStorage.profile` health fields. */
object ProfileHealthKeys {
    const val WEIGHT = "profile_weight"
    const val HEIGHT = "profile_height"
    const val GENDER = "profile_gender"
}

class LocalStore(private val prefs: PreferencesStore) {

    private val moshi: Moshi = MoshiProvider.instance

    private fun <T> listAdapter(type: Class<T>): JsonAdapter<List<T>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, type))

    private suspend fun <T> read(slot: String, type: Class<T>): List<T> {
        val raw = prefs.string(slot) ?: return emptyList()
        return runCatching { listAdapter(type).fromJson(raw) }
            .onFailure { android.util.Log.w("LocalStore", "Falha ao desserializar registro local no slot '$slot'", it) }
            .getOrNull().orEmpty()
    }

    private suspend fun <T> write(slot: String, type: Class<T>, items: List<T>) {
        prefs.setString(slot, listAdapter(type).toJson(items))
    }

    suspend fun vitalSigns(): List<VitalSignsRecord> = read(VITAL_SIGNS, VitalSignsRecord::class.java)

    suspend fun saveVitalSigns(items: List<VitalSignsRecord>) =
        write(VITAL_SIGNS, VitalSignsRecord::class.java, items.take(MAX_VITAL_SIGNS_RECORDS))

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

    suspend fun medicationExtras(): List<MedicationExtras> =
        read(MEDICATION_EXTRAS, MedicationExtras::class.java)

    suspend fun medicationExtrasFor(medicationId: String): MedicationExtras? =
        medicationExtras().firstOrNull { it.medicationId == medicationId }

    /**
     * Upserts one medication's extras, dropping the entry entirely when every
     * field is blank — otherwise clearing the form would leave an empty record
     * behind that still counts as "has extras" on the details screen.
     */
    suspend fun saveMedicationExtras(extras: MedicationExtras) {
        val others = medicationExtras().filterNot { it.medicationId == extras.medicationId }
        write(
            MEDICATION_EXTRAS,
            MedicationExtras::class.java,
            if (extras.isEmpty) others else others + extras,
        )
    }

    /** Called when a medication is deleted, so its extras do not outlive it. */
    suspend fun deleteMedicationExtras(medicationId: String) {
        write(
            MEDICATION_EXTRAS,
            MedicationExtras::class.java,
            medicationExtras().filterNot { it.medicationId == medicationId },
        )
    }

    suspend fun clearMedicationExtras() =
        write(MEDICATION_EXTRAS, MedicationExtras::class.java, emptyList())

    // ---- Medication look (form / shape / colours) — see [MedicationLook] ----

    suspend fun medicationLooks(): List<MedicationLook> =
        read("medication_looks", MedicationLook::class.java)

    suspend fun medicationLookFor(medicationId: String): MedicationLook? =
        medicationLooks().firstOrNull { it.medicationId == medicationId }

    suspend fun saveMedicationLook(look: MedicationLook) {
        val others = medicationLooks().filterNot { it.medicationId == look.medicationId }
        write("medication_looks", MedicationLook::class.java, others + look)
    }

    suspend fun deleteMedicationLook(medicationId: String) {
        write(
            "medication_looks",
            MedicationLook::class.java,
            medicationLooks().filterNot { it.medicationId == medicationId },
        )
    }

    // ---- Simple per-device settings (the web keeps these in `settings.*`) ----
    // Used by Profile (age, photo), Settings (reports guide variant) and Plans
    // (invoice payer), mirroring what the web stores in localStorage only.

    suspend fun uiSetting(name: String): String? = prefs.string("ui_$name")

    suspend fun setUiSetting(name: String, value: String?) = prefs.setString("ui_$name", value)

    // ---- `localStorage.profile` health fields (weight / height / gender) ----
    // The web keeps them in the local `profile` object, not on the server: the
    // Saúde page writes them when "Salvar Segurança" is pressed and the profile
    // page shows/edits `profileWeight` / `profileHeight`. Values are strings as
    // typed (e.g. "72.5", "170"); gender is male | female | other.

    suspend fun profileWeight(): String? = uiSetting(ProfileHealthKeys.WEIGHT)?.takeIf { it.isNotBlank() }

    suspend fun setProfileWeight(value: String?) = setUiSetting(ProfileHealthKeys.WEIGHT, value?.trim()?.ifBlank { null })

    suspend fun profileHeight(): String? = uiSetting(ProfileHealthKeys.HEIGHT)?.takeIf { it.isNotBlank() }

    suspend fun setProfileHeight(value: String?) = setUiSetting(ProfileHealthKeys.HEIGHT, value?.trim()?.ifBlank { null })

    suspend fun profileGender(): String? = uiSetting(ProfileHealthKeys.GENDER)?.takeIf { it.isNotBlank() }

    suspend fun setProfileGender(value: String?) = setUiSetting(ProfileHealthKeys.GENDER, value?.trim()?.ifBlank { null })

    private companion object {
        const val VITAL_SIGNS = "health_vital_signs"
        const val WELLBEING = "health_wellbeing"
        const val SAFETY = "health_safety"
        const val CARE_CONTACTS = "care_network_contacts"
        const val CONSULTATIONS = "medical_consultations"
        const val EMERGENCY_CONTACTS = "emergency_contacts"
        const val MEDICATION_EXTRAS = "medication_extras"

        /** Bounded exactly like the web app's `slice(0, 50)`. */
        const val MAX_HEALTH_RECORDS = 50

        /** `VITAL_SIGNS_MAX_RECORDS` da web: o histórico de sinais vitais é extenso. */
        const val MAX_VITAL_SIGNS_RECORDS = 5000
    }
}
