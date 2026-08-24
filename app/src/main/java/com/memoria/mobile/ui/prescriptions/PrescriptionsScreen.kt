package com.memoria.mobile.ui.prescriptions

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * "Minhas Receitas" — the web `prescriptionsPage`: photos of prescriptions kept
 * on the server so they survive a lost phone.
 *
 * The camera path uses `TakePicturePreview`, which returns a thumbnail without
 * any permission or FileProvider. For a legible prescription the gallery path is
 * the better one, so both are offered side by side.
 */
@Composable
fun PrescriptionsScreen(onBack: () -> Unit, onOpenPlans: () -> Unit) {
    val vm = repoViewModel { PrescriptionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.upload(encodeImageFromUri(context, uri))
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) vm.upload(encodeBitmap(bitmap))
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        if (state.prescriptions.isNotEmpty() || !state.loading) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Minhas Receitas", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        when {
            state.loading && state.prescriptions.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.prescriptions.isEmpty() && !state.loading ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isPremium) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Ative o Premium para armazenar fotos das receitas no histórico.",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                OutlinedButton(onClick = onOpenPlans) { Text("Ver planos") }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { takePicture.launch(null) },
                            enabled = !state.uploading && state.isPremium,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Text("  Tirar foto")
                        }
                        OutlinedButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.uploading && state.isPremium,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            Text("  Galeria")
                        }
                    }
                }

                if (state.uploading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text("  Enviando receita...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                if (state.prescriptions.isEmpty()) {
                    item {
                        Text(
                            "Nenhuma receita guardada ainda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.prescriptions, key = { it.id ?: it.fileName }) { prescription ->
                        PrescriptionCard(prescription) { vm.delete(prescription) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrescriptionCard(prescription: Prescription, onDelete: () -> Unit) {
    // Decoding is keyed on the payload so scrolling does not redo the Base64 work.
    val bitmap = remember(prescription.imageData) { decodeDataUrl(prescription.imageData) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Schedule.localDateOf(prescription.capturedAt ?: prescription.createdAt)
                            ?.format(DATE_BR)
                            ?: "Receita",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        prescription.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover receita", tint = RedMiss)
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto da receita",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            } else {
                Text(
                    "Não foi possível exibir esta imagem.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
