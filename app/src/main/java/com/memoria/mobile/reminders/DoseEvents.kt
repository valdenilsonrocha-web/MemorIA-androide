package com.memoria.mobile.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bumped whenever a dose is answered outside the screen that lists it — the
 * full-screen alert or the notification buttons, handled by [ReminderReceiver]
 * in the background. Screens that list today's doses reload on each bump, so a
 * dose confirmed in the alert leaves "Próximos Horários" without the user
 * having to leave and reopen the page.
 */
object DoseEvents {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun notifyChanged() = _changes.update { it + 1 }
}
