package com.memoria.mobile.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.MemoriaRepository
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
import java.time.temporal.ChronoUnit

/** A reading classified against its reference range, for the live badge. */
data class HealthStatus(val text: String, val level: Level) {
    /** `hm-status-ok` / `hm-status-warn` / `hm-status-danger` da web. */
    enum class Level { NORMAL, ATTENTION, ALERT }
}

data class HealthUiState(
    val message: String? = null,
    val error: String? = null,
    val vitalSigns: List<VitalSignsRecord> = emptyList(),
    val wellbeing: List<WellbeingRecord> = emptyList(),
    val safety: List<SafetyRecord> = emptyList(),
    // Vital signs form
    /** `yyyy-MM-dd'T'HH:mm`, como o `datetime-local` "Data da atualização". */
    val measurementAt: String = nowLocalInput(),
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
    /**
     * O sexo do "cadastro" — na web é o `profile.gender`, que a própria página
     * Saúde grava ao salvar a Segurança. Aqui vem do último registo com sexo.
     */
    val profileSex: String = "not_informed",
) {
    /** IMC = kg / m², recomputed on every keystroke like the web page does. */
    val bmi: Double?
        get() {
            val w = weight.toDoubleOrNullBr() ?: return null
            val h = height.toDoubleOrNullBr() ?: return null
            if (w <= 0 || h <= 0) return null
            val meters = h / 100.0
            return w / (meters * meters)
        }

    /** `resolveHealthSexValue()`: "Usar cadastro" cai no sexo do cadastro. */
    val resolvedSex: String get() = if (sex == "auto") profileSex else sex
}

class HealthViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    private var seeded = false

    fun load() {
        viewModelScope.launch {
            val safety = repo.local.safety()
            val profileSex = normalizeHealthSex(repo.local.profileGender())
                .takeIf { it != "not_informed" && it != "auto" }
                ?: profileSexOf(safety)
            val profileHeight = repo.local.profileHeight()
                ?: safety.firstOrNull { it.height != null }?.height?.let(::jsNumber)
            val s = _state.value
            _state.value = s.copy(
                vitalSigns = repo.local.vitalSigns().newestFirst(),
                wellbeing = repo.local.wellbeing().sortedByDescending { instantOf(it.date)?.toEpochMilli() ?: 0L },
                safety = safety.sortedByDescending { instantOf(it.date)?.toEpochMilli() ?: 0L },
                profileSex = profileSex,
            )
            // Como na web, a altura (`profile.height`), o sexo (`profile.gender`)
            // e a escala de cuidados só são pré-preenchidos na primeira visita —
            // depois é o formulário que manda.
            if (!seeded) {
                seeded = true
                _state.value = _state.value.copy(
                    height = s.height.ifBlank { profileHeight.orEmpty() },
                    sex = if (profileSex != "not_informed") profileSex else "auto",
                    careLevel = s.careLevel ?: safety.firstOrNull { it.careLevel != null }?.careLevel,
                )
            }
        }
    }

    // ---- Vital signs ----

    fun onMeasurementAt(v: String) { _state.value = _state.value.copy(measurementAt = v) }
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
        // A data escolhida em "Data da atualização"; em branco vale "agora".
        val date = if (s.measurementAt.isBlank()) {
            nowIso()
        } else {
            isoFromLocalOrNull(s.measurementAt) ?: run {
                _state.value = s.copy(error = "❌ Informe uma data válida para salvar os sinais vitais.")
                return
            }
        }
        val record = VitalSignsRecord(
            date = date,
            systolic = s.systolic.toIntOrNullBr(),
            diastolic = s.diastolic.toIntOrNullBr(),
            heartRate = s.heartRate.toIntOrNullBr(),
            spo2 = s.spo2.toDoubleOrNullBr(),
            glucose = s.glucose.toDoubleOrNullBr(),
            glucoseContext = s.glucoseContext.ifBlank { "fasting" },
            hba1c = s.hba1c.toDoubleOrNullBr(),
            temperature = s.temperature.toDoubleOrNullBr(),
        )
        // Uma medição antiga lançada hoje vai para o seu lugar na cronologia.
        val updated = (listOf(record) + s.vitalSigns).newestFirst().take(VITAL_SIGNS_MAX_RECORDS)
        viewModelScope.launch {
            repo.local.saveVitalSigns(updated)
            _state.value = _state.value.copy(
                vitalSigns = updated,
                measurementAt = nowLocalInput(),
                systolic = "", diastolic = "", heartRate = "", spo2 = "",
                glucose = "", glucoseContext = "fasting", hba1c = "", temperature = "",
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
            moodNote = s.moodNote,
            activityType = s.activityType,
            activityDuration = s.activityDuration.toIntOrNullBr(),
        )
        val updated = (listOf(record) + s.wellbeing).take(MAX_RECORDS)
        viewModelScope.launch {
            repo.local.saveWellbeing(updated)
            _state.value = _state.value.copy(
                wellbeing = updated,
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
            weight = s.weight.toDoubleOrNullBr()?.takeIf { it != 0.0 },
            height = s.height.toDoubleOrNullBr()?.takeIf { it != 0.0 },
            sex = s.resolvedSex,
            // A web grava o valor do campo IMC, que já vem com uma casa decimal.
            bmi = s.bmi?.let { Math.round(it * 10.0) / 10.0 },
            careLevel = s.careLevel,
        )
        val updated = (listOf(record) + s.safety).take(MAX_RECORDS)
        viewModelScope.launch {
            repo.local.saveSafety(updated)
            // "Save weight to profile": peso, altura e sexo vão para o `profile`
            // local, que a página Perfil mostra e que "Usar cadastro" lê.
            record.weight?.let { repo.local.setProfileWeight(jsNumber(it)) }
            record.height?.let { repo.local.setProfileHeight(jsNumber(it)) }
            if (record.sex != "not_informed") repo.local.setProfileGender(record.sex)
            // `sfForm.reset()`: o formulário inteiro volta ao estado inicial.
            _state.value = _state.value.copy(
                safety = updated,
                profileSex = if (record.sex in setOf("female", "male", "other")) record.sex else _state.value.profileSex,
                weight = "",
                height = "",
                sex = "auto",
                careLevel = null,
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
    private fun isoFromLocal(raw: String): String = isoFromLocalOrNull(raw) ?: raw

    private fun isoFromLocalOrNull(raw: String): String? = runCatching {
        LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrNull()

    private fun nowIso(): String =
        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * Recurso para instalações anteriores ao `profile_gender`: o sexo que o
     * último registo de Segurança gravou (é o mesmo valor que a web põe em
     * `profile.gender` ao salvar).
     */
    private fun profileSexOf(safety: List<SafetyRecord>): String =
        safety.firstOrNull { it.sex in setOf("female", "male", "other") }?.sex ?: "not_informed"

    private companion object {
        /** Bem-estar e Segurança continuam a guardar 50, como na web. */
        const val MAX_RECORDS = 50
    }
}

/** "Agora" no formato do `datetime-local` — `toDateTimeLocalValue()` da web. */
private fun nowLocalInput(): String =
    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .take(16)

/** `normalizeHealthSexValue()` do `app.js`. */
fun normalizeHealthSex(value: String?): String = when (value.orEmpty().trim().lowercase()) {
    "male", "masculino", "m" -> "male"
    "female", "feminino", "f" -> "female"
    "other", "outro", "nao_binario", "não_binário", "non-binary" -> "other"
    "auto" -> "auto"
    else -> "not_informed"
}

/** Accepts both `5.7` and `5,7` — Brazilian keyboards produce the comma. */
internal fun String.toDoubleOrNullBr(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** `Number(fd.get(...))` aceita "72.0"; aqui um inteiro com vírgula também vale. */
private fun String.toIntOrNullBr(): Int? = toDoubleOrNullBr()?.let { Math.round(it).toInt() }

// ---- Faixas de referência (as mesmas de `updateBPStatus`/`updateSingleStatus`) ----

fun bloodPressureStatus(systolic: Double?, diastolic: Double?): HealthStatus? {
    if (systolic == null || diastolic == null || systolic == 0.0 || diastolic == 0.0) return null
    return when {
        systolic < 90 || diastolic < 60 -> HealthStatus("Hipotensão", HealthStatus.Level.ATTENTION)
        systolic < 120 && diastolic < 80 -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
        systolic < 130 && diastolic < 80 -> HealthStatus("Elevada", HealthStatus.Level.ATTENTION)
        systolic < 140 || diastolic < 90 -> HealthStatus("Hipertensão estágio 1", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Hipertensão estágio 2", HealthStatus.Level.ALERT)
    }
}

fun heartRateStatus(value: Double?): HealthStatus? = value?.takeIf { it != 0.0 }?.let {
    when {
        it <= 60 -> HealthStatus("Bradicardia", HealthStatus.Level.ATTENTION)
        it <= 100 -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
        else -> HealthStatus("Taquicardia", HealthStatus.Level.ALERT)
    }
}

fun spo2Status(value: Double?): HealthStatus? = value?.takeIf { it != 0.0 }?.let {
    when {
        it <= 94 -> HealthStatus("Baixa", HealthStatus.Level.ALERT)
        it <= 95 -> HealthStatus("Atenção", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
    }
}

/** A web não distingue o contexto (jejum/pós-refeição) na classificação. */
fun glucoseStatus(value: Double?): HealthStatus? = value?.takeIf { it != 0.0 }?.let {
    when {
        it <= 70 -> HealthStatus("Hipoglicemia", HealthStatus.Level.ALERT)
        it <= 100 -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
        it <= 126 -> HealthStatus("Elevada", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Alta", HealthStatus.Level.ALERT)
    }
}

fun hba1cStatus(value: Double?): HealthStatus? = value?.takeIf { it != 0.0 }?.let {
    when {
        it <= 5.7 -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
        it <= 6.4 -> HealthStatus("Pré-diabetes", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Diabetes", HealthStatus.Level.ALERT)
    }
}

fun temperatureStatus(value: Double?): HealthStatus? = value?.takeIf { it != 0.0 }?.let {
    when {
        it <= 36.0 -> HealthStatus("Hipotermia", HealthStatus.Level.ALERT)
        it <= 37.5 -> HealthStatus("Normal", HealthStatus.Level.NORMAL)
        it <= 38.3 -> HealthStatus("Subfebril", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Febre", HealthStatus.Level.ALERT)
    }
}

/** `calculateBMI()` + `getBmiThresholdsBySex()`: a mulher tem cortes mais baixos. */
fun bmiStatus(bmi: Double?, sex: String): HealthStatus? = bmi?.let {
    val (underweight, overweight, obesity) = if (sex == "female") {
        Triple(18.0, 24.0, 29.0)
    } else {
        Triple(18.5, 25.0, 30.0)
    }
    when {
        it < underweight -> HealthStatus("Abaixo do peso", HealthStatus.Level.ATTENTION)
        it < overweight -> HealthStatus("Peso normal", HealthStatus.Level.NORMAL)
        it < obesity -> HealthStatus("Sobrepeso", HealthStatus.Level.ATTENTION)
        else -> HealthStatus("Obesidade", HealthStatus.Level.ALERT)
    }
}
