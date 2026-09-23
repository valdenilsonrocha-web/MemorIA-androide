package com.memoria.mobile.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.EmergencyContactRecord
import com.memoria.mobile.data.remote.Caregiver
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val name: String = "",
    /** Web `profileAge` — kept on the phone only, as the web keeps it in localStorage. */
    val age: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    val state: String = "",
    /** Absolute path of the profile photo copied into app storage, or null. */
    val photoPath: String? = null,
    /** Web `settings.profile.gender` — only used for the WhatsApp honorific. */
    val gender: String? = null,
    /** Bumped on every photo change so the image is re-decoded. */
    val photoVersion: Int = 0,
    val contacts: List<EmergencyContactRecord> = emptyList(),
    val editingContact: EmergencyContactRecord? = null,
    val contactDialogOpen: Boolean = false,
) {
    /** Server-side plan gate: Premium keeps 3 caregivers, free keeps 1. */
    val maxContacts: Int get() = if (user?.isPremium == true) 3 else 1

    val canAddContact: Boolean get() = contacts.size < maxContacts
}

class ProfileViewModel(
    private val repo: MemoriaRepository,
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val stored = repo.local.emergencyContacts()
            val age = repo.local.uiSetting(KEY_AGE).orEmpty()
            val photo = repo.local.uiSetting(KEY_PHOTO)?.takeIf { File(it).exists() }
            val gender = repo.local.profileGender()
            when (val r = repo.me()) {
                is ApiResult.Ok -> {
                    val user = r.value
                    // The SERVER owns this list, not the phone. The WhatsApp
                    // screen writes the same `caregivers` field, so trusting a
                    // local copy here let the two screens overwrite each other —
                    // and a caregiver added there vanished the next time this
                    // screen saved. The local store only carries the extras
                    // (e-mail, tratamento) that `caregivers` has no room for.
                    val extrasByPhone = stored.associateBy { it.phone.digitsOnly() }
                    val contacts = user.caregivers.map { caregiver ->
                        val extras = extrasByPhone[caregiver.phone.digitsOnly()]
                        EmergencyContactRecord(
                            id = extras?.id ?: UUID.randomUUID().toString(),
                            name = caregiver.name,
                            relation = caregiver.relation,
                            phone = caregiver.phone,
                            email = extras?.email.orEmpty(),
                            title = extras?.title ?: "auto",
                        )
                    }
                    _state.value = ProfileUiState(
                        loading = false,
                        user = user,
                        name = user.name,
                        age = age,
                        email = user.email,
                        phone = user.phone,
                        city = user.city,
                        state = user.state,
                        photoPath = photo,
                        gender = gender,
                        contacts = contacts,
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    error = r.message,
                    age = age,
                    photoPath = photo,
                    gender = gender,
                    contacts = stored,
                )
            }
        }
    }

    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onAge(v: String) { _state.value = _state.value.copy(age = v.filter { it.isDigit() }.take(3)) }
    fun onEmail(v: String) { _state.value = _state.value.copy(email = v) }
    fun onPhone(v: String) { _state.value = _state.value.copy(phone = v) }

    /**
     * "Salvar Informações". The web keeps no validation here (it saves whatever
     * is typed) and never touches the caregivers on this button, so neither does
     * the phone: blank fields go up as null, which the server ignores.
     */
    fun saveProfile() {
        val s = _state.value
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            repo.local.setUiSetting(KEY_AGE, s.age.trim().ifBlank { null })
            val request = ProfileUpdateRequest(
                name = s.name.trim().ifBlank { null },
                email = s.email.trim().ifBlank { null },
                phone = s.phone.trim().ifBlank { null },
                city = s.city.trim().ifBlank { null },
                state = s.state.trim().ifBlank { null },
            )
            when (val r = repo.updateProfile(request)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    saving = false,
                    user = r.value,
                    message = "✅ Perfil e dados médicos salvos com sucesso!",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    /**
     * "Alterar Foto": copies the picked image into the app's private storage, so
     * it survives the picker's temporary permission, and remembers its path.
     */
    fun setPhoto(source: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(app.filesDir, PHOTO_FILE)
                    app.contentResolver.openInputStream(source)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } ?: error("sem acesso à imagem")
                    target.absolutePath
                }.getOrNull()
            }
            if (saved == null) {
                _state.value = _state.value.copy(error = "Não foi possível carregar a foto escolhida.")
                return@launch
            }
            repo.local.setUiSetting(KEY_PHOTO, saved)
            _state.value = _state.value.copy(
                photoPath = saved,
                photoVersion = _state.value.photoVersion + 1,
                message = "✅ Foto atualizada!",
            )
        }
    }

    // ---- Emergency contacts ----

    fun openContactDialog(contact: EmergencyContactRecord? = null) {
        _state.value = _state.value.copy(contactDialogOpen = true, editingContact = contact)
    }

    /** "Alterar Contato": edits the first contact, as the web does. */
    fun openPrimaryContactEditor() {
        val first = _state.value.contacts.firstOrNull()
        if (first == null) {
            _state.value = _state.value.copy(error = "❌ Cadastre um contato para poder alterar.")
            return
        }
        openContactDialog(first)
    }

    fun closeContactDialog() {
        _state.value = _state.value.copy(contactDialogOpen = false, editingContact = null)
    }

    /**
     * Saves a contact locally and pushes the whole list upstream as caregivers,
     * which is what the alerting side of the backend actually reads.
     */
    fun saveContact(name: String, relation: String, phone: String, email: String, title: String) {
        val editing = _state.value.editingContact
        // The server truncates the list to the plan limit without saying so, so a
        // fourth contact used to look saved here and be gone after a reload.
        if (editing == null && !_state.value.canAddContact) {
            _state.value = _state.value.copy(error = contactLimitMessage(_state.value.user?.isPremium == true))
            return
        }
        if (name.isBlank() || relation.isBlank() || phone.isBlank()) {
            _state.value = _state.value.copy(error = "❌ Preencha os campos obrigatórios do contato")
            return
        }
        val contact = EmergencyContactRecord(
            id = editing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            relation = relation.trim(),
            phone = phone.trim(),
            email = email.trim(),
            title = title.ifBlank { "auto" },
        )
        val updated = if (editing != null) {
            _state.value.contacts.map { if (it.id == contact.id) contact else it }
        } else {
            _state.value.contacts + contact
        }
        persistContacts(
            updated,
            if (editing != null) "✅ Contato atualizado com sucesso" else "✅ Contato salvo e exibido no cadastro do usuário",
        )
    }

    /** The web deletes after its `confirm()` with no toast. */
    fun deleteContact(contact: EmergencyContactRecord) {
        persistContacts(_state.value.contacts.filterNot { it.id == contact.id }, null)
    }

    /**
     * Saves locally, shows the web's toast at once, then runs the web's
     * `syncCaregiversToBackend()`: `PUT /api/auth/profile` with only
     * `{ caregivers: [{ name, phone, relation }] }` (contacts without a phone
     * left out). A failed sync is silent, as on the web.
     */
    private fun persistContacts(contacts: List<EmergencyContactRecord>, successMessage: String?) {
        viewModelScope.launch {
            repo.local.saveEmergencyContacts(contacts)
            _state.value = _state.value.copy(
                contacts = contacts,
                contactDialogOpen = false,
                editingContact = null,
                message = successMessage,
            )
            val r = repo.updateProfile(ProfileUpdateRequest(caregivers = contacts.toCaregivers()))
            if (r is ApiResult.Ok) _state.value = _state.value.copy(user = r.value)
        }
    }

    // ---- WhatsApp (web `sendContactAlert`) ----

    /**
     * The contact's "WhatsApp" button. The web asks its server to send the text
     * (`POST /api/integrations/whatsapp/send`), but that route is admin-only, so
     * a normal account would always get a 403. The phone opens WhatsApp itself
     * instead, pre-filled with the web's `buildEmergencyAlertMessage()` text.
     * Returns the `https://wa.me/<digits>?text=…` link, or null (with the web's
     * toast) when the contact has no usable number.
     */
    fun contactWhatsAppUrl(contact: EmergencyContactRecord): String? {
        var phone = contact.phone.digitsOnly()
        if (phone.isEmpty()) {
            _state.value = _state.value.copy(error = "❌ Este contato não possui telefone válido")
            return null
        }
        // wa.me needs the country code; a Brazilian DDD + number has 10 or 11 digits.
        if (phone.length in 10..11) phone = "55$phone"
        val text = java.net.URLEncoder.encode(buildEmergencyAlertMessage(contact), "UTF-8").replace("+", "%20")
        return "https://wa.me/$phone?text=$text"
    }

    private fun buildEmergencyAlertMessage(contact: EmergencyContactRecord): String {
        val s = _state.value
        val patientName = s.name.trim().ifBlank { s.user?.name?.trim().orEmpty() }.ifBlank { "Paciente do MemorIA" }
        val contactName = contact.name.ifBlank { "contato de emergência" }
        val contactHonorific = when (contact.title) {
            "sr" -> "Sr."
            "sra" -> "Sra."
            else -> inferHonorific(contact.name)
        }
        // Web `resolvePatientHonorific`: an explicit gender wins over the name guess.
        val patientHonorific = when (s.gender?.lowercase()) {
            "female", "feminino" -> "Sra."
            "male", "masculino" -> "Sr."
            else -> inferHonorific(patientName)
        }
        val relationText = when (contact.relation) {
            "filho" -> "sua mãe/seu pai, $patientHonorific $patientName,"
            "conjuge" -> "seu cônjuge, $patientHonorific $patientName,"
            "irmao" -> "seu irmão/sua irmã, $patientHonorific $patientName,"
            "cuidador" -> "$patientHonorific $patientName, que está sob seus cuidados,"
            "amigo" -> "seu/sua amigo(a), $patientHonorific $patientName,"
            else -> "$patientHonorific $patientName,"
        }
        return "$contactHonorific $contactName, $relationText precisa de acompanhamento. " +
            "Este é um alerta de contato emergencial enviado pelo MemorIA."
    }

    /** Web `inferHonorific`. */
    private fun inferHonorific(name: String): String {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) return "Sr./Sra."
        val feminineHints = listOf("maria", "ana", "julia", "jéssica", "patricia", "beatriz", "michele", "terezinha")
        if (feminineHints.any { normalized.contains(it) }) return "Sra."
        return if (normalized.endsWith("a")) "Sra." else "Sr."
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private companion object {
        const val KEY_AGE = "profile_age"
        const val KEY_PHOTO = "profile_photo_path"
        const val PHOTO_FILE = "profile_photo.jpg"
    }
}

private fun List<EmergencyContactRecord>.toCaregivers(): List<Caregiver> =
    map { Caregiver(name = it.name, phone = it.phone, relation = it.relation) }
        .filter { it.phone.isNotEmpty() }

private fun String.digitsOnly(): String = filter { it.isDigit() }

private fun contactLimitMessage(premium: Boolean): String = if (premium) {
    "⭐ No plano Premium é permitido cadastrar até 3 contatos de emergência."
} else {
    "⭐ No plano gratuito é permitido 1 contato. Ative o Premium para cadastrar até 3 contatos."
}
