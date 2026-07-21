package com.memoria.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.memoria.mobile.data.MemoriaRepository

/** Build a ViewModel wired to the shared [MemoriaRepository]. */
@Composable
inline fun <reified VM : ViewModel> repoViewModel(
    crossinline create: (MemoriaRepository) -> VM,
): VM {
    val repo = LocalContext.current.repository()
    return viewModel(factory = viewModelFactory { initializer { create(repo) } })
}
