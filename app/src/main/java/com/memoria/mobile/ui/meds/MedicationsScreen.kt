package com.memoria.mobile.ui.meds

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.home.InlinePageTitle

/**
 * "Medicamentos" tab — the web's `addMedicationPage`: the bottom-bar button
 * opens the "Adicionar Medicamento" form directly (page-top illustration,
 * `<h2>`, the form). There is no list here; the medications are listed on the
 * dashboard ("Início").
 *
 * As on the web, "Salvar Medicamento" and "Cancelar" reset the form and go back
 * to the dashboard (`navigateToPage('dashboardPage')`). [onGoHome] does that;
 * until the navigation passes it, the system back action is used — the tab's
 * back stack is always [Início, Medicamentos], so it lands on the dashboard.
 *
 * [onAdd], [onEdit] and [onOpenDetails] are kept for the caller's signature;
 * this page itself only adds.
 */
@Composable
fun MedicationsScreen(
    contentPadding: PaddingValues,
    @Suppress("UNUSED_PARAMETER") onAdd: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onEdit: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDetails: (String) -> Unit,
    onGoHome: (() -> Unit)? = null,
) {
    val vm = repoViewModel { MedicationEditViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val goHome: () -> Unit = {
        vm.start(null) // resetMedicationForm()
        if (onGoHome != null) onGoHome() else backDispatcher?.onBackPressed()
    }

    // Once per page instance: switching tabs keeps what was typed, as the web does.
    LaunchedEffect(Unit) { vm.startIfIdle() }
    MedicationFormEffects(state, vm, onSaved = goHome)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTopGuide(PageGuide.MEDICATIONS)
        // `#addMedicationPage > h2`: a bare h2 (24px, bold, `--text-primary`).
        InlinePageTitle("Adicionar Medicamento", topTrim = 16.dp)
        MedicationFormBody(state = state, vm = vm, onCancel = goHome)
    }
}
