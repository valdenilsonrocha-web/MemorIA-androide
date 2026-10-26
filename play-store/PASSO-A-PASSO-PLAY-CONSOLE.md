# MemorIA no Google Play — passo a passo (copiar e colar)

Conta: **web2ajax@gmail.com** · Play Console → app **MemorIA** (`com.memoria.mobile`)
Link: https://play.google.com/console/u/0/developers/8549461186814607283/app-list

Já está feito (não mexer):
- AAB **v5.9 (versionCode 15)** está na biblioteca do app (a v5.8 também, mas use a 5.9).
- Assinatura **memoria_premium** ativa: `monthly` R$ 14,90 e `annual` R$ 154,80 (Brasil).
- Conta do servidor `934106579663-compute@developer.gserviceaccount.com` convidada.

---

## 1. Ficha principal da loja
**Crescer → Presença na loja → Ficha principal da loja**

**Nome do app** (29/30):
```
MemorIA: Lembrete de Remédios
```

**Descrição curta** (71/80):
```
Alarme na hora do remédio, histórico de doses e avisos para cuidadores.
```

**Descrição completa**: copiar o texto do ficheiro `descricao-completa.txt` desta pasta.

**Imagens** (desta pasta `D:\DEV\MemorIA-Mobile\play-store`):
- Ícone do app: `icone-512x512.png`
- Gráfico de recurso: `grafico-de-recurso-1024x500.png`
- Capturas de tela do telefone: **mínimo 2**. As capturas antigas mostram a versão com os botões partidos, por isso tire 4 capturas novas no celular com a v5.8:
  1. Início
  2. Medicamentos
  3. Alerta "Hora do Medicamento!"
  4. Relatórios

## 2. Detalhes do app
**Presença na loja → Configurações da loja**
- Categoria: **Medicina**
- E-mail de contato: `contato@brasmaster.com.br`
- Site: `https://35.247.217.66.nip.io`

## 3. Conteúdo do app
**Política → Conteúdo do app** (cada item tem o botão "Iniciar")

| Item | Resposta |
|---|---|
| Política de privacidade | `https://35.247.217.66.nip.io/privacidade.html` |
| Anúncios | **Não**, o app não contém anúncios |
| ID de publicidade | **Não** usa |
| Acesso ao app | **Todas ou algumas funcionalidades estão restritas**. Ver secção 4 |
| Classificação do conteúdo | Categoria **"Todos os outros tipos de app"**. Responder **Não** a violência, sexo, linguagem, drogas, jogos de azar, interação entre usuários e localização. **Sim** a compras digitais |
| Público-alvo | Só **18 anos ou mais**. O app **não** é atraente para crianças |
| Apps de notícias | **Não** |
| Apps governamentais | **Não** |
| Recursos financeiros | **Nenhum** |
| Apps de saúde | Marcar: **Gerenciamento de medicamentos e tratamentos**; **Gerenciamento de doenças e condições** (pressão, glicose, SpO₂); **Nutrição e gerenciamento de peso**; **Atividade física**; **Sono**; **Serviços e gestão de saúde** (consultas). O app **não** é dispositivo médico |
| Permissão de intent de tela cheia | Uso: **alarme**. Justificativa: *"O MemorIA é um app de lembrete de medicamentos: na hora de cada dose mostra um alarme em tela inteira com os botões Tomei e Soneca, também com o celular bloqueado. É a função principal do app, usada por idosos."* |
| Alarmes exatos | Justificativa: *"Lembrete de dose de medicamento no minuto exato: o alarme é a função principal do app."* |

### Segurança dos dados
- Coleta ou compartilha dados: **Sim**
- Criptografados em trânsito: **Sim**
- Os usuários podem pedir a exclusão: **Sim**. Link: `https://35.247.217.66.nip.io/excluir-conta.html`

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidades |
|---|---|---|---|---|
| Informações pessoais → Nome | Sim | Sim (WhatsApp, e-mail) | Obrigatório | Funcionalidade do app; Gerenciamento da conta |
| Informações pessoais → Endereço de e-mail | Sim | Sim (e-mail) | Opcional | Gerenciamento da conta; Funcionalidade |
| Informações pessoais → IDs do usuário (CPF) | Sim | Não | Obrigatório | Gerenciamento da conta; Segurança |
| Informações pessoais → Número de telefone | Sim | Sim (WhatsApp) | Opcional | Funcionalidade do app |
| Informações pessoais → Endereço (cidade/UF) | Sim | Não | Opcional | Funcionalidade do app |
| Informações pessoais → Outras (Cartão SUS, sexo) | Sim | Não | Opcional | Funcionalidade do app |
| Saúde e condicionamento físico → Informações de saúde | Sim | Sim (WhatsApp, e-mail) | Obrigatório | Funcionalidade do app |
| Fotos e vídeos → Fotos (receitas) | Sim | Não | Opcional | Funcionalidade do app |
| Contatos → Contatos (cuidadores, digitados) | Sim | Sim (WhatsApp) | Opcional | Funcionalidade do app |
| Informações financeiras → Histórico de compras | Sim | Não | Opcional | Funcionalidade; Gerenciamento da conta |
| Atividade no app → Interações no app | Sim | Não | Obrigatório | Funcionalidade do app |

## 4. Acesso ao app (conta para a revisão do Google)
1. Crie no app uma conta nova de teste, com CPF de 11 dígitos e uma senha. **Não** use a sua conta nem uma conta admin.
2. Em **Acesso ao app**, adicione as instruções:
   - Nome: `Conta de teste`
   - Usuário: o CPF
   - Senha: a senha
   - Instruções: *"Entre com o CPF e a senha. Os recursos Premium ficam liberados por 7 dias após o primeiro login. Exclusão de conta: Mais > Configurações > Privacidade e exclusão da conta."*

## 5. Preço e países
**Monetizar → Preço do app**: **Gratuito**. **Produção → Países/regiões**: **Brasil**.

## 6. Publicar
**Produção → Criar nova versão → Adicionar da biblioteca** → escolher a versão **15 (5.9)** → notas da versão (texto abaixo) → **Avançar → Salvar → Enviar para revisão**.

Notas da versão:
```
Primeira versão do MemorIA na Google Play.
• Lembretes de medicamentos com alarme, "Tomei" e "Soneca"
• Agenda do dia, histórico, calendário e relatórios de adesão
• Registro de saúde, consultas médicas e controle de estoque
• Avisos pelo WhatsApp para paciente e cuidadores
• MemorIA Premium opcional, por assinatura na Google Play (mensal ou anual)
```

> Se o painel pedir **"teste fechado com 12 testadores por 14 dias"** (conta pessoal nova), a Produção só abre depois disso. Mande um print.
