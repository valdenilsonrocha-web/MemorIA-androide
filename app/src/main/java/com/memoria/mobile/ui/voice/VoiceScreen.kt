package com.memoria.mobile.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.memoria.mobile.ui.common.repository
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.MemoriaApp
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.PlanLimits
import com.memoria.mobile.ui.nav.Routes
import com.memoria.mobile.ui.common.systemViewModel
import com.memoria.mobile.voice.AndroidVoiceAudio

// v2: the conversation text is now stored on the server, so everyone sees the notice again.
private const val VOICE_CONSENT_KEY = "voice_consent_v2"

@Composable
fun VoiceScreen(onBack: () -> Unit, onOpenPage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val repo = context.repository()
    val uiScope = rememberCoroutineScope()
    var attemptedAutoStart by rememberSaveable { mutableStateOf(false) }
    var consentAccepted by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    // null enquanto confere o plano; a assistente de IA é do Premium e dos 7 dias de teste.
    var assistantAllowed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val vm = systemViewModel { _, app, scheduler ->
        VoiceViewModel(AndroidVoiceAudio(app), onMedicationSaved = {
            com.memoria.mobile.reminders.DoseEvents.notifyChanged()
            scheduler.reschedule()
        }) { (app as MemoriaApp).graph.voiceConnection() }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.start()
        else if (!granted) vm.permissionDenied()
    }
    fun beginVoice() {
        if (!consentAccepted) { showDisclosure = true; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) vm.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!attemptedAutoStart) {
                attemptedAutoStart = true
                val allowed = PlanLimits.canUseAssistant((repo.me() as? ApiResult.Ok)?.value)
                assistantAllowed = allowed
                if (allowed) {
                    consentAccepted = repo.local.uiSetting(VOICE_CONSENT_KEY) == "1"
                    beginVoice()
                }
            }
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false; onBack() },
            title = { Text("Conheça sua assistente MemorIA") },
            text = { Text("Ao entrar no app, a assistente pode conversar com você, ajudar a cadastrar remédios e consultar alertas de interação. Sua voz e os dados necessários da sua conta serão enviados pelo MemorIA ao Google Gemini. O texto da conversa (o que você e a assistente disserem) fica guardado na sua conta no MemorIA e pode ser lido pela equipe do MemorIA para suporte e segurança; o áudio não é guardado. O microfone para ao encerrar, sair desta tela ou colocar o app em segundo plano.") },
            confirmButton = { TextButton(onClick = {
                uiScope.launch {
                    repo.local.setUiSetting(VOICE_CONSENT_KEY, "1")
                    consentAccepted = true
                    showDisclosure = false
                    beginVoice()
                }
            }) { Text("Ativar assistente") } },
            dismissButton = { TextButton(onClick = { showDisclosure = false; onBack() }) { Text("Agora não") } },
        )
    }
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.stop()
        }
    }

    if (assistantAllowed == false) {
        Scaffold(topBar = { BackTopBar("Conversar com o MemorIA", onBack) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Assistente de IA", style = MaterialTheme.typography.headlineMedium)
                Text("⭐ A assistente de IA é um recurso Premium. Tire dúvidas sobre remédios e horários " +
                    "conversando em linguagem simples.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { onOpenPage(Routes.PLANS) }, modifier = Modifier.fillMaxWidth()) { Text("Ver planos") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
        }
        return
    }

    Scaffold(topBar = { BackTopBar("Conversar com o MemorIA", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Text("Converse por voz", style = MaterialTheme.typography.headlineMedium)
                    Text("Fale naturalmente e ouça as respostas do MemorIA em tempo real.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ao iniciar, sua voz será enviada ao servidor do MemorIA e ao Google Gemini " +
                            "para responder e acessar os dados necessários da sua conta. O texto da conversa fica guardado " +
                            "na sua conta; o áudio não. O microfone é desligado ao sair desta tela " +
                            "ou colocar o app em segundo plano.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Posso consultar seus remédios e preparar um cadastro para você confirmar. " +
                            "Para orientações sobre seu tratamento, fale com seu profissional de saúde.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.status == VoiceStatus.CONNECTING) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            } else {
                                Icon(if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            Text(when {
                                state.status == VoiceStatus.CONNECTING -> "Conectando à voz…"
                                state.muted -> "Microfone silenciado"
                                state.status == VoiceStatus.SPEAKING -> "MemorIA respondendo"
                                state.status == VoiceStatus.LISTENING -> "Pode falar. Estou ouvindo."
                                else -> "Pronto para conversar"
                            }, style = MaterialTheme.typography.titleLarge)
                        }
                        if (!state.active) {
                            Button(
                                onClick = { beginVoice() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) { Text("Iniciar conversa") }
                        } else {
                            if (state.status != VoiceStatus.CONNECTING) {
                                OutlinedButton(
                                    onClick = { vm.toggleMute() },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                ) { Text(if (state.muted) "Ligar microfone" else "Silenciar microfone") }
                            }
                            Button(
                                onClick = { vm.stop() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) {
                                Icon(Icons.Filled.CallEnd, contentDescription = null)
                                Text("  Encerrar conversa")
                            }
                        }
                    }
                }
            }
            state.draft?.let { draft ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Confira antes de cadastrar", style = MaterialTheme.typography.titleLarge)
                            Text("Remédio: ${draft.name}\nDose: ${draft.dosage}\nHorários: ${draft.times.joinToString(", ")}")
                            val frequency = when (draft.frequency) { "weekly" -> "Semanal"; "alternate" -> "Dias alternados"; else -> "Todos os dias" }
                            Text("Frequência: $frequency\nEstoque: ${draft.stock}")
                            if (draft.weekDays.isNotEmpty()) Text("Dias: " + draft.weekDays.joinToString { listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")[it.coerceIn(0, 6)] })
                            if (draft.instructions.isNotBlank()) Text("Instruções: ${draft.instructions}")
                            draft.warnings.forEach { warning -> Text(warning, color = MaterialTheme.colorScheme.error) }
                            Text(draft.limitation, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { vm.confirmMedication(true) }, enabled = !state.confirming,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(if (state.confirming) "Salvando…" else "Confirmar cadastro")
                            }
                            OutlinedButton(onClick = { vm.confirmMedication(false) }, enabled = !state.confirming,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cancelar cadastro") }
                        }
                    }
                }
            }
            state.notice?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyLarge) } }
            state.destination?.let { destination ->
                item {
                    OutlinedButton(onClick = { vm.stop(); onOpenPage(destination) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        val pageName = mapOf("home" to "Início", "meds" to "Medicamentos", "health" to "Saúde",
                            "history" to "Histórico", "calendar" to "Calendário", "doctors" to "Meus Médicos",
                            "prescriptions" to "Receitas", "reports" to "Relatórios", "settings" to "Configurações", "help" to "Ajuda")
                        Text("Abrir " + (pageName[destination] ?: "tela"))
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            }
            if (state.lines.isNotEmpty() || state.userText.isNotBlank() || state.assistantText.isNotBlank()) {
                item { Text("Nesta conversa", style = MaterialTheme.typography.titleLarge) }
                items(state.lines) { line -> TranscriptCard(line.role, line.text) }
                if (state.userText.isNotBlank()) item { TranscriptCard("user", state.userText) }
                if (state.assistantText.isNotBlank()) item { TranscriptCard("assistant", state.assistantText) }
                item { Text("A transcrição pode conter erros. O texto fica guardado na sua conta no MemorIA; o áudio não.",
                    style = MaterialTheme.typography.bodySmall) }
            }
            item { Spacer(Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun TranscriptCard(role: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (role == "user")
            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (role == "user") "Você" else "MemorIA", style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
