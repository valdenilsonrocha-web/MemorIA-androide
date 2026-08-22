package com.memoria.mobile.di

import android.content.Context
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.PreferencesStore
import com.memoria.mobile.data.remote.ApiProvider
import com.memoria.mobile.data.remote.SessionState
import com.memoria.mobile.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tiny manual dependency graph — no Hilt/Dagger. Built once by [MemoriaApp] and
 * reached through it. Keeps the wiring explicit and the build simple.
 */
class AppGraph(context: Context) {
    private val app = context.applicationContext
    private val prefs = PreferencesStore(app)
    private val session = SessionState()
    private val apiProvider = ApiProvider(session)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: MemoriaRepository = MemoriaRepository(prefs, session, apiProvider)

    val reminderScheduler: ReminderScheduler = ReminderScheduler(app, repository)

    init {
        // Every path that changes a medication or the session re-arms the alarm
        // window here, in one place. Leaving it to each screen meant a forgotten
        // call showed up as a reminder that never fired — the kind of bug nobody
        // notices until a dose is missed.
        repository.onScheduleChanged = {
            scope.launch { reminderScheduler.reschedule() }
        }
    }
}
