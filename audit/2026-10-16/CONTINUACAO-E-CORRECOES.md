# Retomada e correções — 16/10/2026

## Escopo recuperado do chat anterior

Mostrar/ocultar senha no login e cadastro; exigir confirmação da senha no cadastro;
corrigir os problemas do WhatsApp identificados em `AUDITORIA-WHATSAPP.md` e aplicar
as correções na VM do MemorIA.

## Android

- O login já possuía controle de visualização da senha.
- O cadastro agora possui senha e confirmação com controles independentes de
  mostrar/ocultar. Senhas diferentes ou confirmação vazia impedem a chamada à API.
- Os quatro testes de confirmação passaram; os 46 testes Android existentes
  passaram. `testDebugUnitTest assembleDebug` concluiu com `BUILD SUCCESSFUL`.
- Versão de distribuição 5.9.4, código 19, gerada com
  `assembleRelease bundleRelease`: `BUILD SUCCESSFUL` em 7m41s.
- APK verificado por `apksigner`; certificado idêntico ao APK 5.9.2, permitindo
  atualização da instalação existente. Metadados confirmados por `aapt`.
- AAB assinado; `jarsigner -verify` confirmou `jar verified`.
- Arquivos finais: `D:\DEV\MemorIA-Mobile\MemorIA-v5.9.4.apk` (47.470.022 bytes)
  e `D:\DEV\MemorIA-Mobile\MemorIA-v5.9.4.aab` (26.754.518 bytes).
- SHA-256 APK: `e21dc5d545d9ff7a5d3baec7996aebaa7702631d8050de10cea335835c38514b`.
- SHA-256 AAB: `330965bd2c73283237331edf8d940db1b9bf326a34cf85252ba9e2b4a4cf5844`.
- Não houve publicação no Google Play.
- Nenhum aparelho estava conectado ao ADB; não houve teste visual em dispositivo.

## Backend e painel: implantados

A implantação ocorreu às 09:35 BRT, em `memoria-vm-2` (`35.247.217.66`). Foram
atualizados dez arquivos do backend e quatro do painel. O servidor e o índice dos
modelos foram preparados a partir da versão efetiva de produção: a única alteração
do servidor foi reduzir o ciclo do WhatsApp de cinco para um minuto. Não foram
implantados serviços de voz ou outras alterações locais fora do WhatsApp.

- Normalização de telefones brasileiros, remoção do zero de prefixo e preservação
  de números internacionais explicitamente iniciados por `+`.
- Registros duráveis por ocorrência e destinatário em `whatsapp_deliveries`;
  tentativas e estados reais em `whatsapp_events`.
- Envio efetivo pelo transporte compartilhado nos botões administrativos, com
  identificador de requisição para evitar repetir o mesmo envio manual.
- Aceitação pelo provedor não é anunciada como entrega. Entrega/leitura dependem
  do webhook do provedor.
- Janela de recuperação limitada, calendário semanal/alternado e limites de dia
  no fuso `America/Sao_Paulo`. Uma nova tentativa não estende a validade original.
- Tentativas limitadas com espera progressiva. Resposta perdida ou envio
  interrompido fica com resultado não confirmado, sem repetição automática cega.
- Alertas aos cuidadores consultam o estado persistido antes de repetir a dose.
- Painel mostra conexão real, falhas reais, novos estados e fluxo de vinculação
  por QR, protegido pela autenticação administrativa.
- A renderização do histórico também foi corrigida: os detalhes JSON agora são
  serializados antes do template, inclusive quando o PostgreSQL retorna UUIDs.
- Credencial operacional do painel separada da sessão de pacientes, e segredo
  distinto para autenticar webhooks. Valores não são registrados neste relatório.

Foi criada somente a tabela nova, sem `alter` ou exclusão de dados. O backup de
arquivos, configurações e PostgreSQL está na VM em:

`/home/web2a/memoria-whatsapp-backup-20261016T123546Z`

O dump PostgreSQL possui 441.192 bytes. A pasta de backup e os arquivos sensíveis
têm acesso restrito. O pacote e o script de implantação estão em:

`/home/web2a/memoria-whatsapp-fix-20261016`

## Validação

- Suíte completa inicial do backend: 13 suítes, 90 testes aprovados, banco SQLite
  em memória e transporte simulado.
- Após os ajustes finais: 16 testes específicos de WhatsApp aprovados, inclusive
  reconexão, recuperação, expiração original, alerta único ao cuidador e as rotas
  HTTP de envio/reenvio com prevenção de submissões duplicadas.
- Esses testes passaram no pacote preparado com o servidor e os
  modelos de produção, antes da implantação.
- Seis testes do painel aprovados, incluindo histórico com UUID e escape de HTML.
- A página do monitor foi renderizada com os registros reais do PostgreSQL:
  HTTP 200, integração administrativa com o backend confirmada.
- Após implantação: `memoria-backend`, `memoria-admin` e `nginx` ativos;
  `/health` respondeu `status=ok`, `database=connected`.
- Webhook Evolution habilitado para `CONNECTION_UPDATE` e `MESSAGES_UPDATE`, com
  cabeçalho secreto. Conectividade do container ao backend e recebimento
  autenticado de um evento sintético de conexão foram confirmados.
- Rotas de operações e medicamentos sem credencial retornaram HTTP 401.
- Na conferência após implantação: quatro registros de envio pendentes e quatro
  eventos `waiting_connection`; zero erros de ciclo observados desde a atualização.

## Etapa humana pendente

A linha ainda estava desconectada (`close`, depois `connecting`). Foi obtido um
QR real e apresentado ao usuário. O telefone da linha remetente precisa
escaneá-lo em WhatsApp → Dispositivos conectados → Conectar um dispositivo.
O QR expira; o botão **Vincular WhatsApp** do painel permite obter outro.

Monitor administrativo: <https://35.247.217.66.nip.io/memoria-admin/whatsapp>.
O prefixo `/memoria-admin` foi confirmado na configuração da VM.

Ainda não há comprovação de entrega real. Não foi disparada mensagem manual a
pacientes nesta retomada. Para um teste controlado, é necessário que o usuário
indique e autorize o destinatário depois de conectar a linha. Os ciclos automáticos
existentes continuam operando sob as novas regras e a janela de recuperação.

A memória HeraclitusDB local continuava indisponível; nenhuma memória alternativa
foi criada. Este arquivo é o relatório técnico da entrega, não um arquivo de memória.
