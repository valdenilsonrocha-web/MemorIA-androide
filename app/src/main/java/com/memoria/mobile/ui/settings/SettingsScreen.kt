package com.memoria.mobile.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.reminders.ReminderSound
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTitle
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.PremiumIcon
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.systemViewModel
import com.memoria.mobile.ui.legal.LgpdPolicyDialog
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.TextPrimary
import com.memoria.mobile.ui.theme.ThemePreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Cores de estado da web (`voice-toggle-status`, `autoReportStatusMessage`).
private val STATUS_ON = Color(0xFF0060F8)
private val STATUS_OFF = Color(0xFF9CA3AF)
private val STATUS_ERROR = Color(0xFFDC2626)

private val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * "Configurações" — the web `settingsPage`, section for section and in the same
 * order: Som de Alerta, Tempo de Soneca, Tema, Estilo da Ilustração de
 * Relatórios, Envio Automático de Relatório, Ativar Notificações, Alerta de
 * Emergência, Privacidade e LGPD, Assistente de Voz, Sincronização Online,
 * Dados e Atualização do Aplicativo.
 *
 * What only the phone needs (stock alerts, server URL, saved password) comes
 * after the web sections.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onLoggedOut: () -> Unit,
    onOpenPlans: () -> Unit,
    onBack: () -> Unit,
    /** "Editar Cadastro" — shown only when the navigation provides it. */
    onOpenProfile: (() -> Unit)? = null,
    /** Privacidade e dados (exportar/excluir conta) — exigido pela Google Play. */
    onOpenPrivacy: (() -> Unit)? = null,
) {
    val vm = systemViewModel { repo, app, scheduler -> SettingsViewModel(repo, app, scheduler) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingExport by vm.pendingExport.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPolicy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onNotificationPermissionResult(granted) }

    // Storage Access Framework: the user picks where the backup goes.
    val saveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.writeExportTo(uri) else vm.discardExport() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(pendingExport) {
        if (pendingExport != null) saveExport.launch("memoria-backup-${LocalDate.now()}.json")
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(com.memoria.mobile.ui.common.errorToast(it)); vm.clearError() }
    }

    val openIntent: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Nada instalado para abrir: fica como está.
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { com.memoria.mobile.ui.common.MemoriaSnackbar(it) } },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // `showPageTopGuide()` prepends the illustration to the page, ABOVE its h2.
            Column {
                PageTopGuide(PageGuide.SETTINGS)
                PageTitle("Configurações")
            }

            // ---- Som de Alerta ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Som de Alerta",
                subtitle = "Escolha o toque que será usado nos lembretes de medicamentos.",
                icon = Icons.Filled.Notifications,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Exactly the nine `<option>`s of `#notificationSound`, in order. An id
                    // outside them (the default "default") shows an empty select, as on the web.
                    SettingsSelect(
                        options = ReminderSound.webOptions.map { it.id to it.label },
                        selected = state.reminderSound.id,
                        onSelect = { id -> vm.setReminderSound(ReminderSound.from(id)) },
                        modifier = Modifier.weight(1f),
                        blankWhenUnmatched = true,
                    )
                    // `.btn-test-sound`: 2px primary border, primary bold 14px text, 45% when disabled.
                    OutlinedButton(
                        onClick = vm::testSound,
                        enabled = !state.soundTesting,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(if (state.soundTesting) 0.45f else 1f)
                            .semantics { contentDescription = "Testar som selecionado" },
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Testar", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- Tempo de Soneca ----
            SectionCard(titleColor = TextPrimary, title = "Tempo de Soneca (minutos)", icon = Icons.Filled.Schedule) {
                SettingsSelect(
                    options = listOf("5" to "5 minutos", "10" to "10 minutos", "15" to "15 minutos"),
                    selected = state.snoozeMinutes.toString(),
                    onSelect = { vm.setSnoozeMinutes(it.toInt()) },
                )
            }

            // ---- Tema do Aplicativo ----
            // The web hides the `<select>` (`.theme-preset-native { display: none }`)
            // and shows only the colour swatches.
            SectionCard(
                titleColor = TextPrimary,
                title = "Tema do Aplicativo",
                subtitle = "Escolha uma paleta em tons pastéis para melhor visualização.",
                icon = Icons.Filled.Palette,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    ThemePreset.entries.forEach { preset ->
                        ThemeSwatch(
                            preset = preset,
                            selected = state.themePreset == preset.id,
                            onClick = { vm.setThemePreset(preset.id) },
                        )
                    }
                }
            }

            // ---- Estilo da Ilustração de Relatórios ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Estilo da Ilustração de Relatórios",
                subtitle = "Escolha o visual da imagem de Relatórios: A (suave) ou B (alto contraste).",
                icon = Icons.Filled.AreaChart,
            ) {
                SettingsSelect(
                    options = listOf("a" to "Variante A (suave)", "b" to "Variante B (alto contraste)"),
                    selected = state.reportsGuideVariant,
                    onSelect = vm::setReportsGuideVariant,
                )
            }

            // ---- Envio Automático de Relatório por E-mail ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Envio Automático de Relatório por E-mail",
                subtitle = "Ative para receber relatório semanal ou mensal no e-mail de contato cadastrado.",
                icon = Icons.Filled.Drafts,
            ) {
                StatusSwitch(
                    checked = state.reportEnabled,
                    enabled = !state.reportSaving,
                    onLabel = "Ativado",
                    offLabel = "Desativado",
                    onCheckedChange = vm::onReportEnabled,
                )
                Text("Frequência", style = MaterialTheme.typography.labelLarge)
                SettingsSelect(
                    options = listOf("weekly" to "Semanal", "monthly" to "Mensal"),
                    selected = state.reportFrequency,
                    onSelect = vm::onReportFrequency,
                    enabled = !state.reportSaving,
                )
                var emailFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.reportContactEmail,
                    onValueChange = vm::onReportContactEmail,
                    label = { Text("E-mail de contato") },
                    placeholder = { Text("contato@exemplo.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        // The web saves on the field's `change` (blur): same here.
                        .onFocusChanged {
                            if (emailFocused && !it.isFocused) vm.saveReportSettings()
                            emailFocused = it.isFocused
                        },
                )
                // Web `updateAutoReportStatusUI` reads the SAVED e-mail, not the field being typed.
                val validEmail = SettingsViewModel.isValidEmail(state.reportSavedEmail)
                val (reportStatus, reportColor) = when {
                    state.reportEnabled && !validEmail ->
                        "Informe um e-mail válido para habilitar o envio automático." to STATUS_ERROR
                    !state.reportEnabled ->
                        "Envio automático desativado." to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> {
                        val freq = if (state.reportFrequency == "monthly") "mensal" else "semanal"
                        "Relatório $freq será enviado para ${state.reportSavedEmail}." to STATUS_ON
                    }
                }
                Text(reportStatus, style = MaterialTheme.typography.bodyLarge, color = reportColor)
            }

            // ---- Ativar Notificações ----
            SectionCard(titleColor = TextPrimary, title = "Ativar Notificações", icon = Icons.Filled.Notifications) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openIntent(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                    enabled = !state.notificationsAllowed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.notificationsAllowed) "Notificações Ativadas" else "Ativar Notificações")
                }
                if (state.notificationsAllowed && !state.exactAlarmsAllowed) {
                    Hint(
                        "Sem permissão de alarme exato, alguns lembretes podem atrasar alguns minutos.",
                    )
                }
            }

            // ---- Alerta de Emergência (Premium) ----
            SectionCard(titleColor = TextPrimary, title = "Alerta de Emergência (Premium)", icon = Icons.Filled.Warning) {
                Hint(
                    if (state.isPremium) {
                        "No Premium, escolha se o alerta deve ser enviado automaticamente ou com confirmação."
                    } else {
                        "Ative o Premium para configurar alerta automático de emergência."
                    },
                )
                SettingsSelect(
                    options = listOf(
                        "auto" to "Automático imediato",
                        "confirm" to "Pedir confirmação antes de enviar",
                    ),
                    selected = state.emergencyAlertMode,
                    onSelect = vm::setEmergencyAlertMode,
                    enabled = state.isPremium,
                )
            }

            // ---- Privacidade e LGPD ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Privacidade e LGPD",
                subtitle = "Gerencie seu consentimento de tratamento de dados pessoais e leia nossa " +
                    "política de privacidade.",
                icon = Icons.Filled.Shield,
            ) {
                val consentDate = Schedule.localDateOf(state.consentDate)?.format(DATE_BR)
                StatusSwitch(
                    checked = state.consentToggle,
                    enabled = !state.consentSaving,
                    onLabel = if (consentDate != null) "Consentimento concedido em $consentDate" else "Consentimento não concedido",
                    offLabel = if (consentDate != null) "Consentimento concedido em $consentDate" else "Consentimento não concedido",
                    onCheckedChange = vm::onConsentToggle,
                    // `#lgpdConsentStatus` keeps the plain --text-secondary colour.
                    neutral = true,
                )
                Button(
                    onClick = vm::saveConsent,
                    enabled = !state.consentSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("  Salvar Consentimento LGPD")
                }
                OutlinedButton(onClick = { showPolicy = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text("  Ver Política de Privacidade")
                }
            }

            // ---- Assistente de Voz (Alexa) - Premium ----
            SectionCard(titleColor = TextPrimary, title = "Assistente de Voz (Alexa) - Premium", icon = Icons.Filled.Mic) {
                if (!state.isPremium) {
                    // `.voice-assistant-locked`: #f9fafb panel, 2px dashed #d1d5db border, #6b7280 text.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                            .dashedBorder(Color(0xFFD1D5DB), 12.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PremiumIcon()
                            Text(
                                "  Recurso disponível apenas no Premium.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF6B7280),
                            )
                        }
                        OutlinedButton(onClick = onOpenPlans, modifier = Modifier.fillMaxWidth()) {
                            PremiumIcon()
                            Text("  Ver planos")
                        }
                    }
                } else {
                    Text("Ativar integração de voz", style = MaterialTheme.typography.labelLarge)
                    StatusSwitch(
                        checked = state.voiceEnabled,
                        enabled = !state.voiceSaving && !state.voiceLoading,
                        onLabel = "Ligado",
                        offLabel = "Desligado",
                        onCheckedChange = vm::onVoiceEnabled,
                    )

                    Text("Provedor", style = MaterialTheme.typography.labelLarge)
                    SettingsSelect(
                        options = VOICE_PROVIDERS,
                        selected = state.voiceProvider,
                        onSelect = vm::onVoiceProvider,
                    )

                    OutlinedTextField(
                        value = state.voiceDeviceName,
                        onValueChange = vm::onVoiceDeviceName,
                        label = { Text("Nome do dispositivo") },
                        placeholder = { Text("Ex: Echo Sala") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.voiceWebhookUrl,
                        onValueChange = vm::onVoiceWebhookUrl,
                        label = { Text("URL do webhook") },
                        placeholder = { Text("https://seu-webhook.exemplo.com/alexa") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("Necessário para Alexa/Google/Webhook. No modo Voz do navegador, não precisa.")

                    Button(
                        onClick = vm::saveVoiceAssistant,
                        enabled = !state.voiceSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.voiceSaving) {
                            CircularProgressIndicator(
                                Modifier.padding(end = 8.dp).size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null)
                        }
                        Text("  Salvar Alexa")
                    }

                    Hint(
                        if (state.voiceSavedEnabled) {
                            val provider = if (state.voiceSavedProvider == "browser-tts") {
                                "Voz do navegador"
                            } else {
                                state.voiceSavedProvider
                            }
                            "✅ Integração ativa — $provider"
                        } else {
                            "Integração de voz inativa no momento."
                        },
                    )
                }
            }

            // ---- Sincronização Online ----
            SectionCard(titleColor = TextPrimary, title = "Sincronização Online", icon = Icons.Filled.Cloud) {
                // `.sync-status`: --bg-secondary box; "Conectado" is --secondary-color bold,
                // "Desconectado" --text-secondary.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabeledLine(
                        "Status:",
                        if (state.hasToken) "Conectado" else "Desconectado",
                        if (state.hasToken) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        valueBold = state.hasToken,
                    )
                    LabeledLine("Última sincronização:", formatLastSync(state.lastSyncAtMillis), null)
                }
                if (!state.hasToken) {
                    Button(onClick = onLoggedOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Text("  Fazer Login")
                    }
                    OutlinedButton(onClick = onLoggedOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Text("  Cadastrar")
                    }
                }
                if (state.hasToken) {
                    Button(
                        onClick = vm::syncNow,
                        enabled = !state.syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.syncing) {
                            CircularProgressIndicator(
                                Modifier.padding(end = 8.dp).size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                        }
                        Text("  Sincronizar Agora")
                    }
                    if (onOpenProfile != null) {
                        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("  Editar Cadastro")
                        }
                    }
                    Button(
                        onClick = { confirmLogout = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Text("  Sair")
                    }
                }
            }

            // ---- Dados ----
            SectionCard(titleColor = TextPrimary, title = "Dados", icon = Icons.Filled.Download) {
                OutlinedButton(
                    onClick = vm::requestExport,
                    enabled = !state.exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.exporting) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                    Text("Exportar Dados")
                }
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Limpar Todos os Dados")
                }
                if (onOpenPrivacy != null) {
                    // Só no app nativo: a Google Play exige excluir a conta dentro do app.
                    OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                        Text("Privacidade e exclusão da conta")
                    }
                }
            }

            // ---- Atualização do Aplicativo ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Atualização do Aplicativo",
                subtitle = "Use este botão para buscar a versão mais nova e reiniciar o app automaticamente.",
                icon = Icons.Filled.Refresh, // fa-rotate-right
            ) {
                Button(
                    onClick = {
                        // No Android a versão nova vem da loja: abre a página do app.
                        val pkg = context.packageName
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                        } catch (_: ActivityNotFoundException) {
                            openIntent(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null) // fa-arrows-rotate
                    Text("  Atualizar Aplicativo Agora")
                }
            }

            // ======== Só no app nativo ========

            // ---- Estoque ----
            SectionCard(titleColor = TextPrimary, title = "Estoque de medicamentos", icon = Icons.Filled.Inventory) {
                ToggleRow(
                    label = "Avisar quando um remédio estiver a acabar",
                    checked = state.lowStockAlerts,
                    onCheckedChange = vm::setLowStockAlerts,
                )
                Hint(
                    "O aviso chega no seu celular às 9h. O servidor avisa também o cuidador " +
                        "pelo WhatsApp — e a farmácia, se você tiver cadastrado uma no medicamento.",
                )
            }

            // ---- Servidor ----
            SectionCard(
                titleColor = TextPrimary,
                title = "Servidor (backend)",
                subtitle = "URL pública da API MemorIA. O app adiciona /api automaticamente.",
            ) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = vm::onBaseUrl,
                    label = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = vm::saveBaseUrl, modifier = Modifier.fillMaxWidth()) {
                    Text("Salvar servidor")
                }
                OutlinedButton(
                    onClick = vm::testConnection,
                    enabled = !state.checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                    Text("Testar conexão")
                }
                when (state.reachable) {
                    true -> Text("Servidor online ✅", color = GreenOk, style = MaterialTheme.typography.bodyLarge)
                    false -> Text("Sem resposta do servidor ❌", color = RedMiss, style = MaterialTheme.typography.bodyLarge)
                    null -> {}
                }
            }

            // ---- Acesso ----
            SectionCard(titleColor = TextPrimary, title = "Acesso") {
                Hint(
                    if (state.hasSavedCredentials) {
                        "O CPF e a senha estão salvos neste celular, protegidos pelo cofre do " +
                            "Android. A tela de login já vem preenchida."
                    } else {
                        "O CPF e a senha não estão salvos. Marque \"Salvar meu CPF e senha\" " +
                            "na tela de login para não precisar digitar de novo."
                    },
                )
                OutlinedButton(
                    onClick = vm::forgetCredentials,
                    enabled = state.hasSavedCredentials,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.LockReset, contentDescription = null)
                    Text("  Esquecer CPF e senha salvos")
                }
            }

            Text(
                "MemorIA Mobile 4.0 · não substitui orientação médica.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPolicy) LgpdPolicyDialog(onDismiss = { showPolicy = false })

    if (confirmLogout) {
        // The web's `confirm()` before "Sair".
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            text = { Text("Tem certeza que deseja sair? Os dados continuarão salvos localmente.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout(onLoggedOut) }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }

    if (confirmClear) {
        // The web's `confirmDangerModal`; "Apagar tudo" runs `storageService.clearAllData()`,
        // i.e. what the DEVICE keeps — the account on the server is untouched, as on the web.
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = RedMiss)
                    Text("  Apagar todos os dados")
                }
            },
            text = {
                Text(
                    buildAnnotatedString {
                        append("Todos os medicamentos, histórico e configurações serão apagados permanentemente. ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Esta ação não pode ser desfeita.")
                        }
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; vm.clearLocalData() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("  Apagar tudo")
                }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

/** The four providers the backend accepts, with the web select's labels. */
private val VOICE_PROVIDERS = listOf(
    "alexa" to "Alexa",
    "google" to "Google Assistant",
    "webhook" to "Webhook personalizado",
    "browser-tts" to "Voz do navegador (teste local)",
)

/** The web's `formatLastSync`. */
private fun formatLastSync(millis: Long?): String {
    if (millis == null) return "Nunca"
    val mins = ((System.currentTimeMillis() - millis) / 60_000).toInt()
    if (mins < 1) return "Agora mesmo"
    if (mins < 60) return "$mins minuto${if (mins > 1) "s" else ""} atrás"
    val hours = mins / 60
    if (hours < 24) return "$hours hora${if (hours > 1) "s" else ""} atrás"
    val days = hours / 24
    return "$days dia${if (days > 1) "s" else ""} atrás"
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabeledLine(label: String, value: String, valueColor: Color?, valueBold: Boolean = false) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label ") }
            withStyle(
                SpanStyle(
                    color = valueColor ?: Color.Unspecified,
                    fontWeight = if (valueBold) FontWeight.Bold else null,
                ),
            ) { append(value) }
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** The web's `.voice-toggle-row`: a checkbox with its status word beside it. */
@Composable
private fun StatusSwitch(
    checked: Boolean,
    onLabel: String,
    offLabel: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    neutral: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            if (checked) onLabel else offLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                checked -> STATUS_ON
                else -> STATUS_OFF
            },
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * The web's `<select class="setting-select">`: a read-only field that opens the
 * option list. Built on [DropdownMenu] so it compiles on any Material 3 BOM.
 */
@Composable
private fun SettingsSelect(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    /** A `<select>` whose value matches no option shows nothing. */
    blankWhenUnmatched: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val matched = options.firstOrNull { it.first == selected }?.second
    val current = matched ?: if (blankWhenUnmatched) "" else options.firstOrNull()?.second.orEmpty()

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true }
                .semantics { contentDescription = accessibilityLabel ?: current },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/**
 * A colour circle per palette, as the web renders its swatch row. The label is
 * carried by `contentDescription` because a colour alone says nothing to a screen
 * reader.
 */
@Composable
private fun ThemeSwatch(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    // `.theme-preset-swatch`: 52px circle, 3px rgba(15,23,42,.15) border; `.is-active`
    // turns the border #0f172a, adds a 4px rgba(15,23,42,.18) ring and scales 1.08.
    Box(
        Modifier
            .size(60.dp)
            .semantics {
                contentDescription = "Tema ${preset.id}"
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = preset.primary,
            shape = CircleShape,
            border = BorderStroke(3.dp, if (selected) Color(0xFF0F172A) else Color(0x260F172A)),
            modifier = Modifier
                .scale(if (selected) 1.08f else 1f)
                .then(if (selected) Modifier.border(4.dp, Color(0x2E0F172A), CircleShape).padding(4.dp) else Modifier.padding(4.dp))
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {}
    }
}

/** A dashed rounded border, as CSS `border: 2px dashed`. */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier =
    drawBehind {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
            ),
        )
    }
