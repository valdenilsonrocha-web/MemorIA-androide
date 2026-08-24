package com.memoria.mobile.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.CareContact
import com.memoria.mobile.data.local.SafetyRecord
import com.memoria.mobile.data.local.VitalSignsRecord
import com.memoria.mobile.data.local.WellbeingRecord
import com.memoria.mobile.data.remote.ConsultationPayload
import com.memoria.mobile.data.remote.VitalSignsPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The three panels of the web `healthPage`. */
enum class HealthSection(val label: String) {
    VITAL_SIGNS("Sinais Vitais"),
    WELLBEING("Bem-estar"),
    SAFETY("Segurança"),
}

/** A reading classified against its reference range, for the live badge. */
data class HealthStatus(val text: String, val level: Level) {
    enum class Level { NORMAL, ATTENTION, ALERT }
}

data class HealthUiState(
    val section: HealthSection = HealthSection.VITAL_SIGNS,
    val message: String? = null,
    val error: String? = null,
    val vitalSigns: List<VitalSignsRecord> = emptyList(),
    val wellbeing: List<WellbeingRecord> = emptyList(),
    val safety: List<SafetyRecord> = emptyList(),
    val doctors: List<CareContact> = emptyList(),
    // Vital signs form
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val spo2: String = "",
    val glucose: String = "",
    val glucoseContext: String = "fasting",
    val hba1c: String = "",
    val temperature: String = "",
    // Wellbeing form
    val sleepQuality: Int? = null,
    val sleepHours: String = "",
    val hydration: String = "",
    val mood: Int? = null,
    val moodNote: String = "",
    val activityType: String = "",
    val activityDuration: String = "",
    // Safety form
    val weight: String = "",
    val height: String = "",
    val sex: String = "auto",
    val careLevel: Int? = null,
) {
    /** IMC = kg / m², recomputed on every keystroke like the web page does. */
    val bmi: Double?
        get() {
            val w = weight.replace(',', '.').toDoubleOrNull() ?: return null
            val h = height.replace(',', '.').toDoubleOrNull() ?: return null
            if (w <= 0 || h <= 0) return null
            val meters = h / 100.0
            return w / (meters * meters)
        }
}

class HealthViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val safety = repo.local.safety()
            _state.value = _state.value.copy(
                vitalSigns = repo.local.vitalSigns(),
                wellbeing = repo.local.wellbeing(),
                safety = safety,
                doctors = repo.local.careContacts().filter { it.type == "medico" },
                // Height and sex barely change; seeding them from the last record
                // spares the user retyping them to get an IMC.
                height = _state.value.height.ifBlank { safety.firstOrNull()?.height?.let(::trimNumber).orEmpty() },
                sex = safety.firstOrNull()?.sex ?: _state.value.sex,
                careLevel = _state.value.careLevel ?: safety.firstOrNull()?.careLevel,
            )
        }
    }

    fun selectSection(section: HealthSection) { _state.value = _state.value.copy(section = section) }

    // ---- Vital signs ----

    fun onSystolic(v: String) { _state.value = _state.value.copy(systolic = v) }
    fun onDiastolic(v: String) { _state.value = _state.value.copy(diastolic = v) }
    fun onHeartRate(v: String) { _state.value = _state.value.copy(heartRate = v) }
    fun onSpo2(v: String) { _state.value = _state.value.copy(spo2 = v) }
    fun onGlucose(v: String) { _state.value = _state.value.copy(glucose = v) }
    fun onGlucoseContext(v: String) { _state.value = _state.value.copy(glucoseContext = v) }
    fun onHba1c(v: String) { _state.value = _state.value.copy(hba1c = v) }
    fun onTemperature(v: String) { _state.value = _state.value.copy(temperature = v) }

    fun saveVitalSigns() {
        val s = _state.value
        val record = VitalSignsRecord(
            date = nowIso(),
            systolic = s.systolic.toIntOrNull(),
            diastolic = s.diastolic.toIntOrNull(),
            heartRate = s.heartRate.toIntOrNull(),
            spo2 = s.spo2.toDoubleOrNullBr(),
            glucose = s.glucose.toDoubleOrNullBr(),
            glucoseContext = s.glucoseContext,
            hba1c = s.hba1c.toDoubleOrNullBr(),
            temperature = s.temperature.toDoubleOrNullBr(),
        )
        val hasValue = listOfNotNull(
            record.systolic, record.diastolic, record.heartRate,
        ).isNotEmpty() || listOfNotNull(
            record.spo2, record.glucose, record.hba1c, record.temperature,
        ).isNotEmpty()
        if (!hasValue) {
            _state.value = s.copy(error = "Preencha ao menos uma medição.")
            return
        }
        val updated = listOf(record) + s.vitalSigns
        viewModelScope.launch {
            repo.local.saveVitalSigns(updated)
            _state.value = _state.value.copy(
                vitalSigns = updated.take(MAX_RECORDS),
                systolic = "", diastolic = "", heartRate = "", spo2 = "",
                glucose = "", hba1c = "", temperature = "",
                message = "Sinais vitais salvos!",
            )
            pushToServer()
        }
    }

    // ---- Wellbeing ----

    fun onSleepQuality(v: Int) { _state.value = _state.value.copy(sleepQuality = v) }
    fun onSleepHours(v: String) { _state.value = _state.value.copy(sleepHours = v) }
    fun onHydration(v: String) { _state.value = _state.value.copy(hydration = v) }
    fun onMood(v: Int) { _state.value = _state.value.copy(mood = v) }
    fun onMoodNote(v: String) { _state.value = _state.value.copy(moodNote = v) }
    fun onActivityType(v: String) { _state.value = _state.value.copy(activityType = v) }
    fun onActivityDuration(v: String) { _state.value = _state.value.copy(activityDuration = v) }

    fun saveWellbeing() {
        val s = _state.value
        val record = WellbeingRecord(
            date = nowIso(),
            sleepQuality = s.sleepQuality,
            sleepHours = s.sleepHours.toDoubleOrNullBr(),
            hydration = s.hydration.toDoubleOrNullBr(),
            mood = s.mood,
            moodNote = s.moodNote.trim(),
            activityType = s.activityType,
            activityDuration = s.activityDuration.toIntOrNull(),
        )
        val updated = listOf(record) + s.wellbeing
        viewModelScope.launch {
            repo.local.saveWellbeing(updated)
            _state.value = _state.value.copy(
                wellbeing = updated.take(MAX_RECORDS),
                sleepQuality = null, sleepHours = "", hydration = "",
                mood = null, moodNote = "", activityType = "", activityDuration = "",
                message = "Dados de bem-estar salvos!",
            )
        }
    }

    // ---- Safety ----

    fun onWeight(v: String) { _state.value = _state.value.copy(weight = v) }
    fun onHeight(v: String) { _state.value = _state.value.copy(height = v) }
    fun onSex(v: String) { _state.value = _state.value.copy(sex = v) }
    fun onCareLevel(v: Int) { _state.value = _state.value.copy(careLevel = v) }

    fun saveSafety() {
        val s = _state.value
        val record = SafetyRecord(
            date = nowIso(),
            weight = s.weight.toDoubleOrNullBr(),
            height = s.height.toDoubleOrNullBr(),
            sex = s.sex,
            bmi = s.bmi,
            careLevel = s.careLevel,
        )
        if (record.weight == null && record.height == null && record.careLevel == null) {
            _state.value = s.copy(error = "Preencha peso, altura ou a escala de cuidados.")
            return
        }
        val updated = listOf(record) + s.safety
        viewModelScope.launch {
            repo.local.saveSafety(updated)
            _state.value = _state.value.copy(
                safety = updated.take(MAX_RECORDS),
                weight = "",
                message = "Dados de segurança salvos!",
            )
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    /**
     * Mirrors the measurements up to the server, which keeps them for the
     * automated e-mail report — the web app does the same on every save.
     *
     * A failure is deliberately silent: the record is already safe on the phone,
     * and this screen works offline. Nagging about a sync the user did not ask
     * for would be noise; the next save retries with the full list anyway,
     * because the server replaces rather than appends.
     */
    private suspend fun pushToServer() {
        val vitals = _state.value.vitalSigns.map {
            VitalSignsPayload(
                date = it.date,
                systolic = it.systolic,
                diastolic = it.diastolic,
                heartRate = it.heartRate,
                spo2 = it.spo2,
                glucose = it.glucose,
                glucoseContext = it.glucoseContext,
                hba1c = it.hba1c,
                temperature = it.temperature,
            )
        }
        val consultations = repo.local.consultations().map {
            ConsultationPayload(
                id = it.id,
                dateTime = isoFromLocal(it.dateTime),
                professional = it.professional,
                location = it.location,
                notes = it.notes,
            )
        }
        repo.syncHealthRecords(vitals, consultations)
    }

    /** `datetime-local` has no zone; the server wants a real instant. */
    private fun isoFromLocal(raw: String): String = runCatching {
        LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrDefault(raw)

    private fun nowIso(): String =
        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val MAX_RECORDS = 50
    }
}

/** Accepts both `5.7` and `5,7` — Brazilian keyboards produce the comma. */
private fun String.toDoubleOrNullBr(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

// ---- Reference ranges (mirrors the badge logic in app.js) -------------------

fun bloodPressureStatus(systolic: Int?, diastolic: Int?): HealthStatus? {
    if (systolic == null || diastolic == null) return null
    return when {
        systolic >= 180 || diastolic >= 120 ->
            HealthStatus("Crise hipertensiva — procure ajuda", HealthStatus.Level.ALERT)
        systolic >= 140 || diastolic >= 90 -> HealthStatus("Hipertensão", HealthStatus.Level.ALERT)
        systolic >= 130 || diastolic >= 85 -> HealthStatus("Limítrofe", HealthStatus.Level.ATTENTION)
        systolic < 90 || diastolic < 60 -> HealthStatus("Pressão baixa", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun heartRateStatus(value: Int?): HealthStatus? = value?.let {
    when {
        it < 50 -> HealthStatus("Bradicardia", HealthStatus.Level.ATTENTION)
        it > 100 -> HealthStatus("Taquicardia", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun spo2Status(value: Double?): HealthStatus? = value?.let {
    when {
        it < 90 -> HealthStatus("Baixa — procure ajuda", HealthStatus.Level.ALERT)
        it < 95 -> HealthStatus("Atenção", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun glucoseStatus(value: Double?, context: String): HealthStatus? = value?.let {
    // Fasting and post-meal have different cut-offs; "random" uses the wider one.
    val (attention, alert) = when (context) {
        "fasting" -> 100.0 to 126.0
        "postmeal" -> 140.0 to 200.0
        else -> 140.0 to 200.0
    }
    when {
        it < 70 -> HealthStatus("Hipoglicemia", HealthStatus.Level.ALERT)
        it >= alert -> HealthStatus("Alta", HealthStatus.Level.ALERT)
        it >= attention -> HealthStatus("Limítrofe", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun hba1cStatus(value: Double?): HealthStatus? = value?.let {
    when {
        it >= 6.5 -> HealthStatus("Faixa de diabetes", HealthStatus.Level.ALERT)
        it >= 5.7 -> HealthStatus("Pré-diabetes", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun temperatureStatus(value: Double?): HealthStatus? = value?.let {
    when {
        it >= 39.0 -> HealthStatus("Febre alta", HealthStatus.Level.ALERT)
        it >= 37.8 -> HealthStatus("Febre", HealthStatus.Level.ATTENTION)
        it < 35.0 -> HealthStatus("Hipotermia", HealthStatus.Level.ALERT)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

fun hydrationStatus(litres: Double?): HealthStatus? = litres?.let {
    when {
        it < 1.5 -> HealthStatus("Abaixo do recomendado", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Boa hidratação", HealthStatus.Level.NORMAL)
    }
}

fun bmiStatus(bmi: Double?): HealthStatus? = bmi?.let {
    when {
        it < 18.5 -> HealthStatus("Abaixo do peso", HealthStatus.Level.ATTENTION)
        it < 25 -> HealthStatus("Peso adequado", HealthStatus.Level.NORMAL)
        it < 30 -> HealthStatus("Sobrepeso", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Obesidade", HealthStatus.Level.ALERT)
    }
}
