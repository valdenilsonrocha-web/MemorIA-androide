package com.memoria.mobile.di

import android.content.Context
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.PreferencesStore
import com.memoria.mobile.data.remote.ApiProvider
import com.memoria.mobile.data.remote.SessionState

/**
 * Tiny manual dependency graph — no Hilt/Dagger. Built once by [MemoriaApp] and
 * reached through it. Keeps the wiring explicit and the build simple.
 */
class AppGraph(context: Context) {
    private val prefs = PreferencesStore(context.applicationContext)
    private val session = SessionState()
    private val apiProvider = ApiProvider(session)

    val repository: MemoriaRepository = MemoriaRepository(prefs, session, apiProvider)
}
