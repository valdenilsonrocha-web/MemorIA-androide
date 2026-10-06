<div align="center">

# MemorIA Mobile 📱💊

**Lembrete de medicamentos para idosos — cliente Android nativo**

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/tools/releases/platforms)
[![targetSdk](https://img.shields.io/badge/targetSdk-34-blue)](https://developer.android.com/tools/releases/platforms)
[![version](https://img.shields.io/badge/version-1.0.0-orange)](#)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

</div>

App **Android nativo** (Kotlin + Jetpack Compose, Material 3) do **MemorIA** — um
lembrete de medicamentos pensado para **idosos**. Consome **o mesmo backend** que
o frontend web (PWA), replicando as funções centrais, com **um diferencial: os
avisos vão pelo WhatsApp**, entregues pelo servidor de forma confiável — mesmo
com o telemóvel do idoso desligado ou o app fechado.

> Este repositório é apenas o **cliente móvel nativo**. O backend (Node.js /
> Express) e o frontend web (PWA) vivem noutro repositório e **não são alterados
> por este projeto**.

> ⚠️ O MemorIA é uma ferramenta auxiliar e **não substitui orientação médica**.

---

## 📑 Índice

- [O que o app faz](#-o-que-o-app-faz-paridade-com-o-frontend-web)
- [O diferencial — WhatsApp](#-o-diferencial--notificações-pelo-whatsapp)
- [Stack técnica](#️-stack-técnica)
- [Arquitetura](#️-arquitetura)
- [Como compilar e rodar](#️-como-compilar-e-rodar)
- [Versão iOS](#-versão-ios)
- [Contrato de API](#-contrato-de-api-referência)
- [Escopo desta versão](#-escopo-desta-versão)
- [Contribuir](#-contribuir)
- [Licença](#-licença)

---

## ✨ O que o app faz (paridade com o frontend web)

Todas as 16 páginas do frontend web existem aqui como tela nativa, mais quatro que
só fazem sentido no telemóvel (WhatsApp, privacidade/LGPD, política e recuperação
de senha).

| Recurso | Tela | Origem dos dados |
|---|---|---|
| **Login / Cadastro / Recuperar senha** (CPF + senha, LGPD) | `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen` | `POST /auth/login`, `/auth/register`, `/auth/forgot-password` |
| **Início** — doses de hoje, estatísticas, estoque baixo em vermelho | `DashboardScreen` | `GET /medications`, `GET /history` |
| **Agenda + marcar dose** (tomei / adiar / não tomei) | `MedicationsScreen` | `GET /medications`, `POST /history` |
| **CRUD de medicamentos** (nome, dose, frequência, horários, dias, estoque, **farmácia fornecedora**, duração, uso contínuo, médico prescritor) | `MedicationEditScreen` | `POST/PUT/DELETE /medications` + DataStore |
| **Detalhes do medicamento** + adesão individual | `MedicationDetailsScreen` | `GET /medications`, `GET /history` |
| **Alarme de cada dose** (notificação com 3 respostas, sobrevive a reboot e Doze) | `ReminderScheduler`, `ReminderReceiver` | `AlarmManager` local |
| **Lembrete de consulta** (1 dia antes e 1 hora antes) | `MyDoctorsScreen` → `ReminderScheduler` | DataStore + `AlarmManager` |
| **Aviso de estoque a acabar** (no celular, às 9h) | `ReminderScheduler` → `StockAlarm` | `GET /medications` + `AlarmManager` |
| **Saúde** — sinais vitais, bem-estar, peso/IMC/escala de cuidados | `HealthScreen` | DataStore, espelhado em `PUT /auth/profile` |
| **Histórico + adesão** (30 dias) | `HistoryScreen` | `GET /history`, `GET /history/adherence` |
| **Calendário** mensal com estado de cada dia | `CalendarScreen` | `GET /history` |
| **Relatórios** — adesão, sequências, histórico filtrável, compartilhar, exportar | `ReportsScreen` | `GET /history/adherence` |
| **Relatório automático por e-mail** (semanal/mensal ao cuidador) | `SettingsScreen` | `PUT /auth/profile` |
| **Reposição** — previsão de quando comprar cada remédio | `ReplenishmentScreen` | `GET /medications` |
| **Meus Médicos** — rede de cuidado + consultas marcadas | `MyDoctorsScreen` | DataStore + `PUT /auth/profile` |
| **Minhas Receitas** — fotos das receitas médicas | `PrescriptionsScreen` | `GET/POST/DELETE /prescriptions` |
| **Assistente de voz (Alexa / Echo Dot)** | `SettingsScreen` | `GET/PUT /integrations/voice-assistant/config` |
| **Avisos por WhatsApp** (nº do paciente + cuidadores) | `WhatsAppScreen` | `PUT /auth/profile` |
| **Planos e assinatura** (cartão tokenizado, Pix, boleto) | `PlansScreen` | `POST /payments/subscribe`, `/payments/create-checkout-session` |
| **Perfil** — dados pessoais e contatos de emergência | `ProfileScreen` | `PUT /auth/profile` |
| **Configurações** — som do lembrete, soneca, tema, alerta de emergência, servidor | `SettingsScreen` | DataStore + `GET /auth/me` |
| **Privacidade e LGPD** — consentimento, exportar, apagar conta | `PrivacyScreen`, `PrivacyPolicyScreen` | `PUT /auth/consent`, `GET /auth/export-data`, `DELETE /auth/account` |
| **Otimização do app** — bateria, alarmes exatos, tela bloqueada | `OptimizationGuideScreen` | — |
| **Ajuda e tutorial** | `HelpScreen` | — |
| **Painel do proprietário** (só admin) | `AdminScreen` | `GET /admin/owner-stats` |

**Acessibilidade** é requisito, não enfeite: tipografia maior, alvos de toque
generosos e Material 3 com bom contraste — o público-alvo são pessoas idosas.

---

## 📲 O diferencial — notificações pelo WhatsApp

O frontend web usa **notificações locais** no dispositivo (Capacitor). Aqui, o
canal de aviso é o **WhatsApp**, entregue pelo **servidor**:

1. Na tela **WhatsApp**, regista-se o **número do paciente** e os **cuidadores**
   (nome, telefone, parentesco) — persistidos via `PUT /auth/profile`.
2. O serviço de lembretes do backend envia:
   - o **lembrete de cada dose** ao WhatsApp do paciente, no horário;
   - o **alerta de dose perdida** aos cuidadores, se a dose não for confirmada
     dentro da tolerância configurada.
3. Como a entrega é **server-side**, funciona mesmo com o telemóvel do idoso
   **desligado** — que é exatamente a proposta de valor do MemorIA.

O app **não** depende de notificações locais para os lembretes; configura o canal
e confia no servidor. Há também um atalho *"Abrir conversa no WhatsApp"* via
`https://wa.me/…`.

---

## 🛠️ Stack técnica

| Camada | Tecnologia |
|---|---|
| **Linguagem** | Kotlin 2.0.20 |
| **UI** | Jetpack Compose · Material 3 · Navigation Compose |
| **Estado** | `ViewModel` + `StateFlow`, coletado com `collectAsStateWithLifecycle` |
| **Rede** | Retrofit + OkHttp · **Moshi (codegen via KSP)** · interceptor JWT |
| **Persistência local** | DataStore (token JWT + URL do backend) |
| **DI** | Manual (`AppGraph`) — sem Hilt, um único repositório |
| **Build** | Gradle (Kotlin DSL) · AGP 8.5 · version catalog (`libs.versions.toml`) |
| **SDK** | `compileSdk`/`targetSdk` 34 · `minSdk` 26 (Android 8.0) |

---

## 🏗️ Arquitetura

```
com.memoria.mobile
├── MemoriaApp / MainActivity        # Application (grafo de DI) + host Compose
├── di/AppGraph                      # DI manual (sem Hilt) — 1 repositório
├── data/
│   ├── remote/                      # Retrofit ApiService, DTOs (Moshi codegen),
│   │                                #   ApiProvider (rebuild ao trocar de URL),
│   │                                #   SessionState (token volátil p/ interceptor)
│   ├── local/PreferencesStore       # DataStore: token JWT + URL do backend
│   └── MemoriaRepository            # orquestra API + sessão; ApiResult<T>
└── ui/
    ├── theme/                       # Material 3, tipografia acessível
    ├── nav/                         # NavHost + bottom bar (4 abas)
    ├── auth/ meds/ history/ whatsapp/ settings/   # telas + ViewModels (StateFlow)
    └── common/                      # componentes, helper de ViewModel, extensões
```

- **Base URL:** igual ao web — o app acrescenta `/api` sozinho. Padrão em
  `DEFAULT_API_BASE_URL` (BuildConfig), **sobrescrevível em Ajustes** em runtime.
- **Token JWT:** injetado por um interceptor a partir de um `SessionState`
  volátil; persistido no DataStore entre sessões.
- **Segurança de rede:** HTTPS por padrão; cleartext permitido **apenas** para
  `10.0.2.2`/`localhost` (dev no emulador) via `network_security_config.xml`.

---

## ▶️ Como compilar e rodar

### Pré-requisitos
- **Android Studio** (Ladybug 2024.2+), que já traz o JDK e o Android SDK.
- **JDK 17** para o Gradle na linha de comando.
  ⚠️ AGP 8.5 / Gradle 8.9 podem **não** funcionar com JDK 25+ — aponte
  `JAVA_HOME` para um JDK 17 se compilar fora do Studio.

### Pelo Android Studio (recomendado)
```bash
git clone https://github.com/valdenilsonrocha-web/MemorIA-androide.git
```
1. `File › Open` na pasta clonada. Na primeira sincronização, o Studio **gera o
   Gradle wrapper** e baixa as dependências.
2. Confirme a URL do backend:
   - padrão em `app/build.gradle.kts` → `DEFAULT_API_BASE_URL`;
   - **ou** troque em runtime em **Ajustes › Servidor** (ex.: emulador a apontar
     ao backend local → `http://10.0.2.2:3001`).
3. **Run › app** num emulador ou dispositivo.

### Pela linha de comando
```bash
# Gera o wrapper uma vez, se não abrir pelo Studio:
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug            # APK em app/build/outputs/apk/debug/
```

### Apontar ao backend local (dev)
Com o backend do MemorIA a correr localmente (`http://localhost:3001`), no app
(emulador) use **Ajustes › Servidor** → `http://10.0.2.2:3001`.
O `10.0.2.2` é como o emulador Android alcança o `localhost` da máquina.

##  Versão iOS

O cliente iOS nativo em SwiftUI está em [`ios/`](ios/README.md). Ele usa o
mesmo contrato REST e backend, com autenticação, agenda de doses, medicamentos,
histórico/adesão, saúde, WhatsApp/cuidadores, privacidade LGPD e ajustes de
servidor. Abra [`ios/MemorIA.xcodeproj`](ios/MemorIA.xcodeproj) no Xcode 15+ em
um Mac e execute em um simulador ou iPhone com iOS 17+.

---

## 🔌 Contrato de API (referência)

- **Base:** `<servidor>/api`
- **Auth:** JWT em `Authorization: Bearer <token>`
- **Envelope de resposta:** `{ success, message, data }`

Espelha o `apiService.js` do frontend web. Ver `data/remote/ApiService.kt` para a
lista completa de endpoints e DTOs.

---

## 📌 Escopo desta versão

**Incluído:** tudo o que a tabela acima lista — paridade de telas com o frontend
web, incluindo pagamento/assinatura, receitas, assistente de voz e painel admin.

**Onde o telemóvel difere do web, de propósito:**

- **O alarme é do sistema, não do navegador.** O web agenda notificações locais
  pelo Capacitor; aqui é o `AlarmManager` com `setExactAndAllowWhileIdle`, rearmado
  no arranque, na troca de hora e a cada alteração de medicamento. Um lembrete de
  dose que escorrega meia hora não serve.
- **O som do lembrete são três canais, não uma preferência.** O Android congela o
  som e a importância de um canal no momento em que ele é criado, então "Padrão /
  Suave / Alto" escolhe entre canais em vez de editar um. Ver `ReminderSound`.
- **A consulta avisa antes, não na hora.** O web notifica no horário da consulta,
  que já é tarde para sair de casa; aqui há dois avisos — um dia antes e uma hora
  antes.
- **Alguns campos do medicamento vivem só no telefone.** `duração do tratamento`,
  `uso contínuo`, `médico prescritor` e `farmácia dispensadora` não têm coluna no
  backend (o `pickMedicationPayload()` descarta-os), exatamente como no web vivem
  no `localStorage`. Ficam no DataStore, em `MedicationExtras`.
  O `supplier` (Fornecedor / Farmácia, Premium) **é** coluna do servidor e viaja
  com o medicamento — é esse contacto que o servidor avisa por WhatsApp quando o
  estoque fica crítico.
- **Exportar e apagar dados vivem em "Privacidade e dados"**, não em
  Configurações, que é onde a LGPD espera encontrá-los. Configurações liga para lá.
- **Modo offline com sincronização** continua fora do escopo: as telas leem o
  servidor a cada abertura. Os registos que o backend não guarda (saúde, rede de
  cuidado, consultas) já funcionam offline porque o telefone é a fonte deles.

---

## 🤝 Contribuir

1. Faça um *fork* e crie um branch: `git checkout -b feat/minha-melhoria`.
2. Siga o estilo existente (uma tela = `Screen` + `ViewModel` com `StateFlow`).
3. Garanta que compila: `./gradlew assembleDebug`.
4. Abra um *Pull Request* descrevendo a mudança.

---

## 📄 Licença

**MIT** — ver [LICENSE](LICENSE).

## Google Play Billing

A versão 5.6 usa Google Play para assinaturas no Android. Consulte [GOOGLE_PLAY_BILLING.md](GOOGLE_PLAY_BILLING.md) para catálogo, configuração GCP/backend e testes internos.
