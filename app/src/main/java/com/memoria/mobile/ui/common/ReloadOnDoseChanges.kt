package com.memoria.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memoria.mobile.reminders.DoseEvents
import kotlinx.coroutines.flow.drop

/**
 * Reloads a dose list when it may be stale: each time the app comes back to the
 * front (the alert is its own activity, so answering it pauses this screen) and
 * each time a dose is recorded in the background ([DoseEvents]) — the network
 * call can still be running when the user is back on the list. The first resume
 * is skipped: the screen's own LaunchedEffect already does the initial load.
 */
@Composable
fun ReloadOnDoseChanges(onReload: () -> Unit) {
    val reload by rememberUpdatedState(onReload)
    val firstResume = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (firstResume[0]) firstResume[0] = false else reload()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        DoseEvents.changes.drop(1).collect { reload() }
    }
}
