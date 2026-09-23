package com.memoria.mobile.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.MedicationExtras
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.MedicationRequest
import com.memoria.mobile.data.remote.Supplier
import com.memoria.mobile.ui.common.PlanLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedEditState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val isNew: Boolean = true,
    val name: String = "",
    val dosage: String = "",
    val frequency: String = "daily",
    /**
     * One entry per `<input type="time">` of the web form, in order. "" is a
     * slot the user added but has not filled yet — the web keeps those too and
     * simply drops them on save.
     */
    val times: List<String> = listOf(""),
    val weekDays: List<Int> = emptyList(),
    val stock: String = "0",
    val instructions: String = "",
    // Fornecedor / Farmácia (Premium) — coluna do servidor, alimenta o alerta de
    // stock crítico por WhatsApp à farmácia.
    val supplierName: String = "",
    val supplierPhone: String = "",
    // Campos que o servidor não guarda: ficam no telefone (LocalStore).
    val treatmentDurationDays: String = "",
    val continuousUse: Boolean = false,
    val prescribingDoctor: String = "",
    val dispensingPharmacy: String = "",
    // "Ícone do Medicamento" — forma farmacêutica, formato e cores (LocalStore).
    val medicationForm: String = "comprimido",
    val pillShape: String = "round",
    val pillColor: String = "#e5e7eb",
    val capsuleColor1: String = "#e5e7eb",
    val capsuleColor2: String = "#2f7dff",
    val isPremium: Boolean = false,
    val saved: Boolean = false,
    /** The web's success toast, shown by the screen once the save lands. */
    val savedMessage: String? = null,
    /** Set when the free plan is what stopped the save, so the UI can offer Premium. */
    val blockedByPlan: Boolean = false,
    /** The web's "✅ Nome (dose) selecionado…" toast after picking from the library. */
    val libraryMessage: String? = null,
) {
    val look: MedicationLook
        get() = MedicationLook(
            medicationId = "",
            medicationForm = medicationForm,
            pillShape = pillShape,
            pillColor = pillColor,
            capsuleColor1 = capsuleColor1,
            capsuleColor2 = capsuleColor2,
        )
}

class MedicationEditViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MedEditState())
    val state: StateFlow<MedEditState> = _state.asStateFlow()

    private var editingId: String? = null
    private var started = false

    /** [start] for a new medication, unless this form was already started. */
    fun startIfIdle() {
        if (!started) start(null)
    }

    fun start(medicationId: String?) {
        started = true
        editingId = medicationId
        if (medicationId == null) {
            _state.value = MedEditState(isNew = true)
            viewModelScope.launch { _state.value = _state.value.copy(isPremium = isPremium()) }
            return
        }
        _state.value = _state.value.copy(loading = true, isNew = false)
        viewModelScope.launch {
            val premium = isPremium()
            when (val r = repo.medications()) {
                is ApiResult.Ok -> {
                    val med = r.value.firstOrNull { it.id == medicationId }
                    if (med == null) {
                        _state.value = _state.value.copy(loading = false, error = "❌ Medicamento não encontrado")
                    } else {
                        val extras = repo.local.medicationExtrasFor(medicationId)
                        val look = repo.local.medicationLookFor(medicationId)
                        _state.value = MedEditState(
                            loading = false,
                            isNew = false,
                            name = med.name,
                            dosage = med.dosage,
                            frequency = med.frequency,
                            times = med.times.filter { it.isNotBlank() }.ifEmpty { listOf("") },
                            weekDays = med.weekDays ?: emptyList(),
                            stock = med.stock.toString(),
                            instructions = med.instructions ?: "",
                            supplierName = med.supplier?.name.orEmpty(),
                            supplierPhone = med.supplier?.phone.orEmpty(),
                            continuousUse = extras?.continuousUse ?: false,
                            treatmentDurationDays = if (extras?.continuousUse == true) "" else
                                extras?.treatmentDurationDays?.toString().orEmpty(),
                            prescribingDoctor = extras?.prescribingDoctor.orEmpty(),
                            dispensingPharmacy = extras?.dispensingPharmacy.orEmpty(),
                            medicationForm = normalizeMedicationForm(look?.medicationForm),
                            pillShape = look?.pillShape ?: "round",
                            pillColor = sanitizePillColor(look?.pillColor),
                            capsuleColor1 = sanitizePillColor(look?.capsuleColor1),
                            capsuleColor2 = sanitizePillColor(look?.capsuleColor2, "#2f7dff"),
                            isPremium = premium,
                        )
                    }
                }
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    error = "❌ ${r.message}",
                    isPremium = premium,
                )
            }
        }
    }

    private suspend fun isPremium(): Boolean =
        (repo.me() as? ApiResult.Ok)?.value?.isPremium == true

    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onDosage(v: String) { _state.value = _state.value.copy(dosage = v) }
    fun onStock(v: String) { _state.value = _state.value.copy(stock = v.filter { it.isDigit() }) }
    fun onInstructions(v: String) { _state.value = _state.value.copy(instructions = v) }
    fun onFrequency(v: String) { _state.value = _state.value.copy(frequency = v) }
    fun onSupplierName(v: String) { _state.value = _state.value.copy(supplierName = v) }
    fun onSupplierPhone(v: String) {
        _state.value = _state.value.copy(supplierPhone = v.filter { it.isDigit() }.take(20))
    }
    fun onTreatmentDurationDays(v: String) {
        _state.value = _state.value.copy(treatmentDurationDays = v.filter { it.isDigit() }.take(4))
    }
    fun onContinuousUse(v: Boolean) {
        // `updateContinuousUseFieldState()`: uso contínuo desativa e limpa a duração.
        _state.value = _state.value.copy(
            continuousUse = v,
            treatmentDurationDays = if (v) "" else _state.value.treatmentDurationDays,
        )
    }
    fun onPrescribingDoctor(v: String) { _state.value = _state.value.copy(prescribingDoctor = v) }
    fun onDispensingPharmacy(v: String) { _state.value = _state.value.copy(dispensingPharmacy = v) }

    fun onMedicationForm(v: String) { _state.value = _state.value.copy(medicationForm = normalizeMedicationForm(v)) }
    fun onPillShape(v: String) { _state.value = _state.value.copy(pillShape = v) }
    fun onPillColor(v: String) { _state.value = _state.value.copy(pillColor = sanitizePillColor(v)) }
    fun onCapsuleColor1(v: String) { _state.value = _state.value.copy(capsuleColor1 = sanitizePillColor(v)) }
    fun onCapsuleColor2(v: String) { _state.value = _state.value.copy(capsuleColor2 = sanitizePillColor(v, "#2f7dff")) }

    /** `applyMedicationFromLibrary()`. */
    fun applyFromLibrary(med: LibraryMedication, dosage: String) {
        _state.value = _state.value.copy(
            name = med.name,
            dosage = dosage,
            instructions = med.instructions,
            frequency = med.frequency.ifBlank { _state.value.frequency },
            libraryMessage = "✅ ${med.name} ($dosage) selecionado. Defina os horários e salve.",
        )
    }

    fun consumeLibraryMessage() { _state.value = _state.value.copy(libraryMessage = null) }

    /** "Adicionar Horário": a new, empty time slot. */
    fun addTimeSlot() {
        _state.value = _state.value.copy(times = _state.value.times + "")
    }

    fun setTime(index: Int, time: String) {
        val list = _state.value.times.toMutableList()
        if (index !in list.indices) return
        list[index] = time
        _state.value = _state.value.copy(times = list)
    }

    fun removeTimeSlot(index: Int) {
        val list = _state.value.times.toMutableList()
        if (index !in list.indices || list.size <= 1) return
        list.removeAt(index)
        _state.value = _state.value.copy(times = list)
    }

    fun toggleWeekDay(day: Int) {
        val current = _state.value.weekDays
        _state.value = _state.value.copy(
            weekDays = if (current.contains(day)) current - day else (current + day).sorted(),
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun save() {
        val s = _state.value
        val times = s.times.map { it.trim() }.filter { it.isNotBlank() }
        when {
            s.name.isBlank() -> { _state.value = s.copy(error = "⚠️ Informe o nome do medicamento."); return }
            s.dosage.isBlank() -> { _state.value = s.copy(error = "⚠️ Informe a dosagem."); return }
            times.isEmpty() -> { _state.value = s.copy(error = "⚠️ Adicione ao menos um horário."); return }
        }
        val request = MedicationRequest(
            name = s.name.trim(),
            dosage = s.dosage.trim(),
            frequency = s.frequency,
            times = times,
            weekDays = if (s.frequency == "weekly") s.weekDays else null,
            instructions = s.instructions.trim().ifBlank { null },
            stock = s.stock.toIntOrNull() ?: 0,
            active = true,
            // Only sent by a Premium account. A free one omits the field entirely
            // (null), which the backend reads as "do not touch" — so editing on the
            // phone cannot wipe a pharmacy contact set while the account was paid.
            supplier = if (s.isPremium) {
                Supplier(name = s.supplierName.trim(), phone = s.supplierPhone.trim())
            } else {
                null
            },
        )
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            // The free plan caps active medications, exactly as the website does.
            // The server does NOT enforce this one, so skipping the check here
            // meant the phone handed out for free what the site charges for.
            if (editingId == null && !allowedToAdd()) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = PLAN_LIMIT_MESSAGE,
                    blockedByPlan = true,
                )
                return@launch
            }

            val isEdit = editingId != null
            val result = editingId?.let { repo.updateMedication(it, request) }
                ?: repo.createMedication(request)
            when (result) {
                is ApiResult.Ok -> {
                    // Saved after the server call, keyed by the id it assigned: a new
                    // medication has no id to file the extras under until then.
                    (result.value.id ?: editingId)?.let { id -> saveLocal(id, s) }
                    _state.value = _state.value.copy(
                        saving = false,
                        saved = true,
                        savedMessage = if (isEdit) {
                            "✅ Medicamento atualizado com sucesso!"
                        } else {
                            "✅ Medicamento adicionado com sucesso!"
                        },
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(
                    saving = false,
                    error = if (isEdit) {
                        "❌ Erro ao atualizar medicamento. Tente novamente."
                    } else {
                        "❌ Erro ao adicionar medicamento. Tente novamente."
                    },
                )
            }
        }
    }

    /** The fields the backend has no column for — see [MedicationExtras] and [MedicationLook]. */
    private suspend fun saveLocal(medicationId: String, s: MedEditState) {
        repo.local.saveMedicationExtras(
            MedicationExtras(
                medicationId = medicationId,
                treatmentDurationDays = if (s.continuousUse) null else
                    s.treatmentDurationDays.toIntOrNull()?.takeIf { it > 0 },
                continuousUse = s.continuousUse,
                prescribingDoctor = s.prescribingDoctor.trim(),
                dispensingPharmacy = s.dispensingPharmacy.trim(),
            )
        )
        repo.local.saveMedicationLook(s.look.copy(medicationId = medicationId))
    }

    /**
     * Counts what the SERVER currently holds rather than trusting a screen's
     * cached list — two devices adding at the same time would otherwise both
     * believe there was room.
     */
    private suspend fun allowedToAdd(): Boolean {
        val user = (repo.me() as? ApiResult.Ok)?.value
        val active = (repo.medications() as? ApiResult.Ok)?.value?.count { it.active } ?: 0
        return PlanLimits.canAddMedication(user, active)
    }

    fun clearPlanBlock() { _state.value = _state.value.copy(blockedByPlan = false, error = null) }

    private companion object {
        /** The web's toast in `handleAddMedication()`, word for word. */
        const val PLAN_LIMIT_MESSAGE =
            "⭐ No plano gratuito você pode cadastrar até 2 medicamentos. Ative o Premium para medicamentos ilimitados."
    }
}
