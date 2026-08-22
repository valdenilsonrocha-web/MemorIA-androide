package com.memoria.mobile.ui.common

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.reminders.ReminderScheduler

/** Build a ViewModel wired to the shared [MemoriaRepository]. */
@Composable
inline fun <reified VM : ViewModel> repoViewModel(
    crossinline create: (MemoriaRepository) -> VM,
): VM {
    val repo = LocalContext.current.repository()
    return viewModel(factory = viewModelFactory { initializer { create(repo) } })
}

/**
 * Same as [repoViewModel] for the screens that also need the Application and the
 * alarm scheduler — Settings has to read notification permission and re-arm
 * reminders, neither of which the repository owns.
 */
@Composable
inline fun <reified VM : ViewModel> systemViewModel(
    crossinline create: (MemoriaRepository, Application, ReminderScheduler) -> VM,
): VM {
    val context = LocalContext.current
    val repo = context.repository()
    val scheduler = context.reminderScheduler()
    val application = context.applicationContext as Application
    return viewModel(
        factory = viewModelFactory { initializer { create(repo, application, scheduler) } },
    )
}
