package com.memoria.mobile.ui.common

import android.content.Context
import com.memoria.mobile.MemoriaApp
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.reminders.ReminderScheduler

/** Reach the process-wide repository from any Context. */
fun Context.repository(): MemoriaRepository =
    (applicationContext as MemoriaApp).graph.repository

/** Reach the process-wide reminder scheduler from any Context. */
fun Context.reminderScheduler(): ReminderScheduler =
    (applicationContext as MemoriaApp).graph.reminderScheduler
