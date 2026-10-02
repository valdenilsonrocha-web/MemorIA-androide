package com.memoria.mobile.ui.prescriptions


import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.home.AdOnlyFreeBanner
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.PremiumIcon
import com.memoria.mobile.ui.common.PremiumIconSize
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.health.formatFullDateTime
import com.memoria.mobile.ui.theme.RedMiss

/**
 * "Minhas Receitas" — the web `prescriptionsPage`: photos of prescriptions kept
 * on the server so they survive a lost phone.
 *
 * The camera path takes a full-resolution photo through the app FileProvider
 * (falling back to the `TakePicturePreview` thumbnail only if the provider is
 * missing); the gallery sits beside it because the web's single
 * `<input type="file" capture>` offers the same two sources on a phone.
 */
@Composable
fun PrescriptionsScreen(onBack: () -> Unit, onOpenPlans: () -> Unit) {
    val vm = repoViewModel { PrescriptionsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Prescription?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            vm.uploadEncoded(fileName = displayNameOf(context, uri)) { encodeImageFromUri(context, uri) }
        }
    }

    // Foto em resolução total: a câmara grava num ficheiro de `cacheDir/images`
    // exposto pelo FileProvider (`${applicationId}.fileprovider`,
    // `res/xml/file_paths.xml`). A miniatura do `TakePicturePreview` fica só
    // como recurso se o provider não estiver configurado nesta build.
    // O caminho sobrevive à recriação da Activity enquanto a câmara está aberta.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pendingPhotoPath?.let(::File)
        pendingPhotoPath = null
        if (saved && file != null && file.length() > 0) {
            vm.uploadEncoded {
                try {
                    encodeImageFromUri(context, Uri.fromFile(file))
                } finally {
                    file.delete()
                }
            }
        } else {
            file?.delete()
        }
    }
    val takePicturePreview = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) vm.uploadEncoded { encodeBitmap(bitmap) }
    }
    val launchCamera: () -> Unit = {
        val target = runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "receita-${System.currentTimeMillis()}.jpg")
            file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (target != null) {
            pendingPhotoPath = target.first.absolutePath
            runCatching { takePicture.launch(target.second) }.onFailure {
                pendingPhotoPath = null
                target.first.delete()
                takePicturePreview.launch(null)
            }
        } else {
            takePicturePreview.launch(null)
        }
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
        snackbarHost = { SnackbarHost(snackbar) { MemoriaSnackbar(it) } },
    ) { inner ->
        when {
            state.loading && state.prescriptions.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.isPremium && state.error != null && state.prescriptions.isEmpty() && !state.loading ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PageTopGuide(PageGuide.PRESCRIPTIONS) }
                // `#prescriptionsPage > h2` (bare h2 with `fa-file-medical`), under the image.
                item { InlinePageTitle("Minhas Receitas", topTrim = 12.dp) }

                item {
                    Text(
                        if (state.isPremium) {
                            "Armazene fotos de receitas no histórico para consultar quando precisar."
                        } else {
                            "Ative o Premium para armazenar fotos de receitas no histórico."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = launchCamera,
                            enabled = !state.uploading && state.isPremium,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Text("  Tirar foto da receita")
                        }
                        OutlinedButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.uploading && state.isPremium,
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Escolher da galeria")
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

                when {
                    // Sem Premium a web nem carrega o histórico: mostra só o convite.
                    !state.isPremium -> item {
                        EmptyBlock(message = "Função disponível no plano Premium.") {
                            PremiumIcon(size = PremiumIconSize.Empty)
                        }
                    }
                    state.prescriptions.isEmpty() -> item {
                        EmptyBlock(message = "Nenhuma receita salva ainda.") {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                    else -> items(state.prescriptions, key = { it.id ?: it.fileName }) { prescription ->
                        PrescriptionCard(prescription) { toDelete = prescription }
                    }
                }

                if (!state.isPremium) {
                    item { PremiumTipBanner(onOpenPlans) }
                }
            }
        }
    }

    toDelete?.let { prescription ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text("Deseja excluir esta receita do histórico?") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    vm.delete(prescription)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyBlock(message: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        // `.empty-state p`: `--font-size-large`.
        Text(
            message,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** `.prescription-card`: foto, "Arquivo:", "Salva em:" e o botão "Excluir". */
@Composable
private fun PrescriptionCard(prescription: Prescription, onDelete: () -> Unit) {
    // Decoding is executed asynchronously on Dispatchers.IO so scrolling does not jank.
    var bitmap by remember(prescription.imageData) { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(prescription.imageData) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodeDataUrl(prescription.imageData)
        }
    }
    val savedAt = formatFullDateTime(prescription.capturedAt ?: prescription.createdAt) ?: "Data não informada"

    // `.prescription-card`: borda 2px `--border-color`, raio 16px, padding 12px,
    // fundo `--bg-secondary`; a foto com 180px de altura em `object-fit: cover`,
    // raio 10px e borda 1px.
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Receita médica salva",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                )
            }
            Column(Modifier.padding(top = 4.dp)) {
                LabeledLine("Arquivo:", prescription.fileName.ifBlank { "receita.jpg" })
                LabeledLine("Salva em:", savedAt)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = RedMiss, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(" Excluir")
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            // `.prescription-meta`: texto `--text-secondary`; o `strong` em `--text-primary`.
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                append(label)
            }
            append(" $value")
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A `<section class="ad-banner ad-only-free">` da página de receitas. */
@Composable
private fun PremiumTipBanner(onOpenPlans: () -> Unit) = AdOnlyFreeBanner(
    title = "Quer mais recursos?",
    body = "Com o Premium você salva receitas por foto, sincroniza na nuvem e recebe alertas de emergência automáticos.",
    buttonText = "Assinar Premium",
    onOpenPlans = onOpenPlans,
)
