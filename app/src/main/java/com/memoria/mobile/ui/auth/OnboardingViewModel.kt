package com.memoria.mobile.ui.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.ProfileUpdateRequest
import com.memoria.mobile.reminders.ReminderScheduler
import com.memoria.mobile.reminders.ReminderSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Year

/**
 * The web's first-run setup (`setupOnboarding()` in app.js) keeps its answers in
 * the browser's own settings — `onboardingCompleted`, `alertDisplayType`,
 * `notificationSound`, `profile.birthYear`. The phone keeps them on the device
 * the same way; the name also goes to the account, since the rest of the app
 * reads it from there.
 */
object OnboardingPrefs {
    private const val FILE = "memoria_onboarding"
    private const val KEY_DONE = "onboarding_completed"
    const val KEY_ALERT_TYPE = "alert_display_type" // normal | fullscreen
    const val KEY_BIRTH_YEAR = "birth_year"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isCompleted(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun alertDisplayType(context: Context): String =
        prefs(context).getString(KEY_ALERT_TYPE, "fullscreen") ?: "fullscreen"

    fun birthYear(context: Context): Int? =
        prefs(context).getInt(KEY_BIRTH_YEAR, 0).takeIf { it > 0 }

    internal fun save(context: Context, alertType: String, birthYear: Int) {
        prefs(context).edit()
            .putBoolean(KEY_DONE, true)
            .putString(KEY_ALERT_TYPE, alertType)
            .putInt(KEY_BIRTH_YEAR, birthYear)
            .apply()
    }
}

/**
 * The onboarding's three sounds. The id is stored as-is, exactly as the web's
 * `finishOnboarding()` writes `notificationSound` (`default` / `gentle` / `loud`);
 * [ReminderSound.from] knows each of them.
 */
enum class OnboardingSound(val webId: String, val label: String) {
    DEFAULT("default", "Padrão"),
    GENTLE("gentle", "Suave"),
    LOUD("loud", "Alto"),
}

data class OnboardingUiState(
    val step: Int = 0,
    val name: String = "",
    val birthYear: String = "",
    val alertType: String = "fullscreen",
    val sound: OnboardingSound = OnboardingSound.DEFAULT,
    val saving: Boolean = false,
    /** One-shot toast text, like the web's `showToast`. */
    val toast: String? = null,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val repo: MemoriaRepository,
    private val app: Application,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingUiState(
            birthYear = OnboardingPrefs.birthYear(app)?.toString().orEmpty(),
            alertType = OnboardingPrefs.alertDisplayType(app),
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var accountName: String = ""

    init {
        viewModelScope.launch {
            val current = repo.reminderSound()
            val sound = OnboardingSound.entries.firstOrNull { it.webId == current }
                ?: if (current == "soft") OnboardingSound.GENTLE else OnboardingSound.DEFAULT
            _state.value = _state.value.copy(sound = sound)
            val user = (repo.me() as? ApiResult.Ok)?.value
            accountName = user?.name.orEmpty().trim()
            if (_state.value.name.isBlank() && accountName.isNotBlank()) {
                _state.value = _state.value.copy(name = accountName)
            }
        }
    }

    fun onName(value: String) { _state.value = _state.value.copy(name = value.take(80)) }

    fun onBirthYear(value: String) {
        _state.value = _state.value.copy(birthYear = value.filter { it.isDigit() }.take(4))
    }

    fun onAlertType(value: String) { _state.value = _state.value.copy(alertType = value) }

    fun onSound(value: OnboardingSound) { _state.value = _state.value.copy(sound = value) }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    fun back() {
        val s = _state.value
        if (s.step > 0) _state.value = s.copy(step = s.step - 1)
    }

    fun next() {
        val s = _state.value
        when (s.step) {
            1 -> if (s.name.isBlank()) {
                _state.value = s.copy(toast = "⚠️ Informe seu nome para continuar.")
                return
            }
            2 -> {
                val year = s.birthYear.toIntOrNull()
                if (year == null || year < 1900 || year > Year.now().value) {
                    _state.value = s.copy(toast = "⚠️ Informe um ano de nascimento válido.")
                    return
                }
            }
        }
        if (s.step < LAST_STEP) {
            _state.value = s.copy(step = s.step + 1)
        } else {
            finish()
        }
    }

    private fun finish() {
        val s = _state.value
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            OnboardingPrefs.save(app, s.alertType, s.birthYear.toIntOrNull() ?: 0)
            runCatching {
                repo.setReminderSound(s.sound.webId)
                scheduler.reschedule()
            }
            val name = s.name.trim()
            if (name.isNotBlank() && name != accountName) {
                // Best effort: offline, the name simply stays as it was on the account.
                runCatching { repo.updateProfile(ProfileUpdateRequest(name = name)) }
            }
            _state.value = _state.value.copy(
                saving = false,
                finished = true,
                toast = "✅ Configuração inicial concluída!",
            )
        }
    }

    companion object {
        const val LAST_STEP = 4
    }
}
