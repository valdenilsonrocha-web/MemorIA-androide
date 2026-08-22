package com.memoria.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.EmergencyContactRecord
import com.memoria.mobile.data.remote.Caregiver
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    val state: String = "",
    val contacts: List<EmergencyContactRecord> = emptyList(),
    val editingContact: EmergencyContactRecord? = null,
    val contactDialogOpen: Boolean = false,
)

class ProfileViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val stored = repo.local.emergencyContacts()
            when (val r = repo.me()) {
                is ApiResult.Ok -> {
                    val user = r.value
                    // The server's caregiver list is authoritative for a fresh
                    // install, where the phone has no local copy yet.
                    val contacts = stored.ifEmpty {
                        user.caregivers.map {
                            EmergencyContactRecord(
                                id = UUID.randomUUID().toString(),
                                name = it.name,
                                relation = it.relation,
                                phone = it.phone,
                            )
                        }
                    }
                    _state.value = ProfileUiState(
                        loading = false,
                        user = user,
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        city = user.city,
                        state = user.state,
                        contacts = contacts,
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    error = r.message,
                    contacts = stored,
                )
            }
        }
    }

    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onEmail(v: String) { _state.value = _state.value.copy(email = v) }
    fun onPhone(v: String) { _state.value = _state.value.copy(phone = v) }
    fun onCity(v: String) { _state.value = _state.value.copy(city = v) }
    fun onState(v: String) { _state.value = _state.value.copy(state = v) }

    fun saveProfile() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(error = "Informe o seu nome.")
            return
        }
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val request = ProfileUpdateRequest(
                name = s.name.trim(),
                email = s.email.trim().ifBlank { null },
                phone = s.phone.trim().ifBlank { null },
                city = s.city.trim().ifBlank { null },
                state = s.state.trim().ifBlank { null },
                caregivers = s.contacts.toCaregivers(),
            )
            when (val r = repo.updateProfile(request)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    saving = false,
                    user = r.value,
                    message = "Informações salvas.",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    // ---- Emergency contacts ----

    fun openContactDialog(contact: EmergencyContactRecord? = null) {
        _state.value = _state.value.copy(contactDialogOpen = true, editingContact = contact)
    }

    fun closeContactDialog() {
        _state.value = _state.value.copy(contactDialogOpen = false, editingContact = null)
    }

    /**
     * Saves a contact locally and pushes the whole list upstream as caregivers,
     * which is what the alerting side of the backend actually reads.
     */
    fun saveContact(name: String, relation: String, phone: String, email: String) {
        if (name.isBlank() || phone.isBlank()) {
            _state.value = _state.value.copy(error = "Informe ao menos nome e telefone.")
            return
        }
        val editing = _state.value.editingContact
        val contact = EmergencyContactRecord(
            id = editing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            relation = relation.trim(),
            phone = phone.trim(),
            email = email.trim(),
        )
        val updated = if (editing != null) {
            _state.value.contacts.map { if (it.id == contact.id) contact else it }
        } else {
            _state.value.contacts + contact
        }
        persistContacts(updated, if (editing != null) "Contato atualizado." else "Contato salvo.")
    }

    fun deleteContact(contact: EmergencyContactRecord) {
        persistContacts(
            _state.value.contacts.filterNot { it.id == contact.id },
            "Contato removido.",
        )
    }

    private fun persistContacts(contacts: List<EmergencyContactRecord>, successMessage: String) {
        viewModelScope.launch {
            repo.local.saveEmergencyContacts(contacts)
            _state.value = _state.value.copy(
                contacts = contacts,
                contactDialogOpen = false,
                editingContact = null,
            )
            when (val r = repo.setCaregivers(contacts.toCaregivers(), _state.value.phone)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    user = r.value,
                    message = successMessage,
                )
                // The phone already has the change; only the sync failed, and
                // saying so is more useful than a bare "erro ao salvar".
                is ApiResult.Err -> _state.value = _state.value.copy(
                    message = "$successMessage Sincronização pendente: ${r.message}",
                )
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}

private fun List<EmergencyContactRecord>.toCaregivers(): List<Caregiver> = map {
    Caregiver(name = it.name, phone = it.phone, relation = it.relation)
}
