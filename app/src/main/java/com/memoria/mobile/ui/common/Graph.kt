package com.memoria.mobile.ui.common

import android.content.Context
import com.memoria.mobile.MemoriaApp
import com.memoria.mobile.data.MemoriaRepository

/** Reach the process-wide repository from any Context. */
fun Context.repository(): MemoriaRepository =
    (applicationContext as MemoriaApp).graph.repository
