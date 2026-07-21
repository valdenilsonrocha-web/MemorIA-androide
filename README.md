# MemorIA Mobile 📱💊

App **Android nativo** (Kotlin + Jetpack Compose) para o [MemorIA](../MemorIA) —
lembrete de medicamentos para idosos. Consome **o mesmo backend** que o frontend
web (PWA), replicando as funções centrais, com **um diferencial: os avisos vão
pelo WhatsApp**, entregues pelo servidor de forma confiável (mesmo com o celular
do idoso desligado ou o app fechado).

> Este projeto é o cliente móvel nativo. O backend (Node.js/Express) e o frontend
> web continuam em `D:\DEV\MemorIA`. Nada aqui altera o backend.

---

## ✨ O que o app faz (paridade com o frontend web)

| Recurso | Tela | Endpoint(s) do backend |
|---|---|---|
| **Login / Cadastro** (CPF + senha, LGPD) | `LoginScreen`, `RegisterScreen` | `POST /auth/login`, `POST /auth/register` |
| **Agenda de hoje** + marcar dose (tomei / adiar / não tomei) | `MedicationsScreen` | `GET /medications`, `POST /history` |
| **CRUD de medicamentos** (nome, dose, frequência, horários, dias, estoque) | `MedicationEditScreen` | `POST/PUT/DELETE /medications` |
| **Histórico + adesão** (30 dias) | `HistoryScreen` | `GET /history`, `GET /history/adherence` |
| **Avisos por WhatsApp** (nº do paciente + cuidadores) | `WhatsAppScreen` | `PUT /auth/profile` (`phone`, `caregivers`) |
| **Ajustes** (perfil, URL do servidor, testar conexão, sair) | `SettingsScreen` | `GET /auth/me`, `GET /health` |

### O diferencial — notificações pelo WhatsApp
O frontend web usa **notificações locais** no dispositivo (Capacitor). Aqui, o
canal de notificação é o **WhatsApp**, entregue pelo servidor:

1. Na tela **WhatsApp**, o utilizador regista o **número do paciente** e os
   **cuidadores** (nome, telefone, parentesco) — persistidos via `PUT /auth/profile`.
2. O serviço de lembretes do backend (`whatsappReminderService.js`) envia:
   - o **lembrete de cada dose** ao WhatsApp do paciente, no horário;
   - o **alerta de dose perdida** aos cuidadores, se a dose não for confirmada
     dentro da tolerância (`WHATSAPP_MISSED_GRACE_MINUTES`).
3. Como a entrega é **server-side**, funciona mesmo com o telemóvel do idoso
   desligado — que é exatamente a proposta de valor do MemorIA.

O app **não** depende de notificações locais para os lembretes; ele configura o
canal e confia no servidor. (Há também um atalho "Abrir conversa no WhatsApp"
via `https://wa.me/…`.)

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
    ├── theme/                       # Material 3, tipografia acessível (fontes maiores)
    ├── nav/                         # NavHost + bottom bar (4 abas)
    ├── auth/ meds/ history/ whatsapp/ settings/   # telas + ViewModels (StateFlow)
    └── common/                      # componentes, helper de ViewModel, extensões
```

- **Rede:** Retrofit + OkHttp + **Moshi (codegen via KSP)**. O token JWT é
  injetado por um interceptor a partir de um `SessionState` volátil.
- **Base URL:** igual ao web — o app acrescenta `/api` sozinho. Padrão em
  `DEFAULT_API_BASE_URL` (BuildConfig), **sobrescrevível em Ajustes** em runtime.
- **Estado:** cada tela tem um `ViewModel` com um `StateFlow<UiState>`, coletado
  com `collectAsStateWithLifecycle`.
- **Segurança de rede:** HTTPS por padrão; cleartext permitido **apenas** para
  `10.0.2.2`/`localhost` (dev no emulador) via `network_security_config.xml`.

---

## ▶️ Como compilar e rodar

### Pré-requisitos
- **Android Studio** (Ladybug 2024.2+), que já traz o JDK e o Android SDK.
- **JDK 17** recomendado para o Gradle (o Android Studio usa o dele embutido).
  ⚠️ AGP 8.5 / Gradle 8.9 podem **não** funcionar com JDK 25 — se compilar pela
  linha de comando, aponte `JAVA_HOME` para um JDK 17.

### Passos
1. **Abra a pasta** `D:\DEV\MemorIA-Mobile` no Android Studio
   (`File › Open`). Na primeira sincronização, o Studio **gera o Gradle wrapper**
   (o `gradle-wrapper.jar`) e baixa as dependências.
2. Confirme a URL do backend:
   - padrão em `app/build.gradle.kts` → `DEFAULT_API_BASE_URL`
     (hoje `https://memoria-api.onrender.com`);
   - **ou** troque em runtime na tela **Ajustes › Servidor** (ex.: para o
     emulador apontar ao backend local, use `http://10.0.2.2:3001`).
3. **Rode** num emulador ou dispositivo (`Run › app`).

### Backend local (dev)
```bash
cd D:\DEV\MemorIA\backend
npm install && npm run dev      # http://localhost:3001
```
No app (emulador), em **Ajustes › Servidor**, use `http://10.0.2.2:3001`
(o `10.0.2.2` é como o emulador Android alcança o `localhost` da máquina).

### Linha de comando (se tiver o SDK + JDK 17)
```bash
# Gera o wrapper (uma vez, se não abrir pelo Studio):
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug            # APK em app/build/outputs/apk/debug/
```

---

## 🔌 Contrato de API (referência)

Base: `<servidor>/api`, JWT em `Authorization: Bearer <token>`. Envelope de
resposta: `{ success, message, data }`. Espelha
`frontend/public/src/services/apiService.js`. Ver `data/remote/ApiService.kt`.

---

## 📌 Escopo desta versão

Incluído: autenticação, medicamentos (agenda + CRUD + marcação de dose),
histórico/adesão, configuração de WhatsApp (paciente + cuidadores), ajustes de
servidor. **Fora do escopo v1** (existem no web, podem ser adicionados depois):
pagamento/assinatura (Mercado Pago/Stripe), receitas (prescriptions), assistente
de voz, painel admin e o modo offline com sincronização. A arquitetura
(`MemoriaRepository` + `ApiService`) já está pronta para recebê-los.

> **Nota:** o MemorIA é uma ferramenta auxiliar e **não substitui orientação
> médica**.
