**Auditoria do WhatsApp — MemorIA**

Data: 16/10/2026. Coleta na VM aproximadamente 08:57–08:59 (America/Sao_Paulo).
Solicitação: investigar por que o MemorIA-Mobile não envia mensagens aos pacientes.

**Conclusão**

O envio automático é responsabilidade do backend em `/home/web2a/MemorIA/backend`; o aplicativo Android em `D:\DEV\MemorIA-Mobile` cadastra os dados. O bloqueio operacional confirmado é a instância `memoria` da Evolution API sem conexão aberta com o WhatsApp. Há também um defeito concreto no tratamento dos telefones da versão implantada e ações administrativas que simulam sucesso sem enviar mensagem.

O backend e o banco estão disponíveis. Isso não significa que o WhatsApp esteja conectado. Não há evidência suficiente para atribuir a desconexão a bloqueio da conta, expiração específica da sessão ou incompatibilidade de versão.

**Evidências operacionais**

| Verificação | Resultado observado |
|---|---|
| VM do MemorIA | `memoria-vm-2`, `35.247.217.66` |
| Backend / painel / nginx | Serviços ativos |
| Saúde do backend | HTTP 200; `database=connected` |
| Provedor configurado | `evolution`; `http://localhost:8081`; instância `memoria` |
| Chave do backend versus chave do provedor | Iguais; valores omitidos |
| Evolution API | Container ativo; versão 2.3.7; raiz responde HTTP 200 |
| Conexão ao WhatsApp | `GET /instance/connectionState/memoria` → `state=close` |
| Cadastro da instância | `connectionStatus=connecting`; contagem Message/Contact/Chat igual a zero |
| Webhook da instância | `GET /webhook/find/memoria` → `null` |
| Tentativas com falha desde 13/10 às 17:18:23 BRT | 29 para pacientes + 258 para cuidadores = **287**, todas registradas como HTTP 500 |
| Logs de sucesso nesse intervalo | Zero lembretes ao paciente; zero alertas sequenciais ao cuidador |
| Usuários | 16 ativos; 14 com telefone preenchido |
| Medicamentos | 7 ativos; todos diários; pertencentes a 4 pacientes elegíveis ao envio |
| Telefones dos 14 usuários com número | 13 têm 11 dígitos sem DDI; 1 tem 12 dígitos com zero inicial e sem DDI |
| Tabela `whatsapp_events` do backend | Vazia no momento da consulta |

Os contadores representam tentativas e logs, não quantidade de doses ou pacientes sem atendimento. A ausência de registros no banco não permite provar todo o histórico de entregas.

**Achados, por prioridade**

1. **Crítico — instância de WhatsApp desconectada.** A API HTTP funciona e aceita a chave, mas a sessão consultada retorna `close`. A listagem mostra `connecting`, também sem estado aberto. Isso confirma um bloqueio no canal de transporte. O serviço continua tentando enviar e recebe HTTP 500. Recuperação: verificar o estado da sessão; obter o QR pelo fluxo administrativo existente; vincular a linha pelo telefone responsável; confirmar `state=open`. Se o pareamento falhar, investigar os logs da sessão antes de substituir versão ou recriar a instância. Os logs atuais usam `LOG_LEVEL=ERROR` e não forneceram a causa original da desconexão.

2. **Alto — telefones enviados sem normalização brasileira na produção.** O `formatPhone()` implantado apenas remove caracteres não numéricos. Ele não acrescenta `55` nem remove o zero de prefixo. Assim, números locais cadastrados pelo Android seguem ao provedor em formato inadequado para um destinatário brasileiro internacional. Afeta também os cuidadores e fornecedores que passam pela mesma função. A cópia local do backend já possui uma correção, mas ela não está na VM. Correção: centralizar a normalização e validação para os formatos brasileiros aceitos; conservar números internacionais válidos; aplicar ao envio automático e manual. Não foi feito envio com sessão autenticada para medir separadamente a rejeição desses números.

   Fonte de produção: [whatsappReminderService.js:111](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:111). Correção existente localmente: [whatsappReminderService.js:131](D:/DEV/MemorIA/backend/src/services/whatsappReminderService.js:131).

3. **Crítico — “Disparar teste” e “Reenviar” do painel não enviam WhatsApp.** Na versão copiada da VM, `/whatsapp/send-test` cria diretamente um evento com `status=delivered` e `provider=z-api`, sem chamada de rede. `/{event_id}/resend` apenas altera o evento para `sent` e limpa o erro. O template afirma que o envio aconteceu ou que houve encaminhamento para fila; essas rotas não implementam esse envio ou fila. É um defeito confirmado no código; os botões não foram acionados nesta auditoria. Correção: integrar essas ações ao transporte real; registrar aceitação e falha reais; reservar `delivered` à confirmação do provedor.

   Fontes: [whatsapp.py:65](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-admin-source/app/routers/whatsapp.py:65), [whatsapp.py:84](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-admin-source/app/routers/whatsapp.py:84) e [list.html:16](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-admin-source/app/templates/whatsapp/list.html:16).

4. **Alto — monitoramento não acompanha os lembretes automáticos nem comprova entrega.** O serviço automático registra no console e em um `Set` na memória, sem persistir suas tentativas em `WhatsAppEvent`. O webhook da Evolution está ausente e o controller atual simplesmente reconhece webhooks de provedores diferentes de Meta sem processá-los. O painel consulta eventos do banco, portanto não mostra as 287 falhas observadas no journal. O selo verde do gateway no template é estático. Correção: persistir tentativas com identificador de mensagem; processar eventos de conexão e entrega da Evolution; exibir estado real da linha. Aceitação HTTP pelo provedor não é confirmação de entrega.

   Fontes: [whatsappReminderService.js:250](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:250), [integrationController.js:208](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/controllers/integrationController.js:208), [whatsapp.py:27](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-admin-source/app/routers/whatsapp.py:27) e [list.html:45](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-admin-source/app/templates/whatsapp/list.html:45).

5. **Alto — sem recuperação de doses após a janela e sem deduplicação durável.** A VM executa o ciclo a cada cinco minutos e só considera o horário atual até cinco minutos no futuro. Depois do minuto da dose, a rotina não a recupera. Uma falha na última tentativa, indisponibilidade ou reconexão posterior deixa o lembrete para trás. A deduplicação é apenas um `Set`, perdido em reinícios; isso também pode repetir envios. Correção: registrar cada ocorrência de dose e seu estado; aplicar tentativas limitadas e uma janela explícita de recuperação. Evitar disparar indiscriminadamente lembretes antigos ao reconectar a linha.

   Fontes: [server.js:212](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/server.js:212), [whatsappReminderService.js:194](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:194) e [whatsappReminderService.js:13](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:13).

6. **Médio — regras de calendário e fuso inconsistentes.** O envio ao paciente não consulta `isScheduledToday()`; envia também em dias não programados para medicamentos semanais/alternados. Esse cenário foi reproduzido com medicamento sintético, mas os sete medicamentos atuais são diários. A verificação de dose resolvida usa limites de dia do host, enquanto os horários usam São Paulo; na configuração observada em UTC, esses limites diferem em três horas. A seleção por dia da semana também usa o relógio do host na produção. Correção: compartilhar a regra de ocorrência da dose e os limites de dia no fuso da aplicação.

   Fontes: [whatsappReminderService.js:244](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:244), [whatsappReminderService.js:358](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:358) e [whatsappReminderService.js:375](D:/DEV/MemorIA-Mobile/audit/2026-10-16/vm-source/src/services/whatsappReminderService.js:375).

**Diferença entre computador e produção**

A VM contém fontes de setembro e não tem metadados Git no diretório implantado. O hash SHA-256 original do serviço auditado é `1bb8f25644d321ff79084fc21e1deb92b880ebe3570f672de3d0e15288c7eb37`.

A cópia local em `D:\DEV\MemorIA\backend` já inclui normalização do telefone, tratamento de limites do dia no fuso e ciclo de um minuto. A VM continua com a implementação anterior. A atualização local, isoladamente, não reconecta a linha nem corrige os botões fictícios do painel.

**Ordem de correção e critérios de aceite**

1. Corrigir o painel para mostrar desconexão e resultados reais; preparar o backend com normalização de telefone, persistência de tentativas e revisão das regras de agendamento.
2. Implantar apenas as alterações revisadas e reiniciar os serviços envolvidos. Não fazer atualização ampla de dependências como tentativa de diagnóstico.
3. Vincular a linha via QR e verificar `state=open`. O escaneamento exige acesso ao telefone que será a linha remetente.
4. Com autorização para o destinatário escolhido, enviar uma única mensagem controlada e acompanhar aceitação, identificador do provedor e confirmação de entrega. Não usar o botão atual como prova.
5. Validar uma dose futura de teste, um alerta ao cuidador e o comportamento em reconexão/reinício; garantir ausência de duplicações e sucesso fictício.

**Validação e limites desta auditoria**

Foram feitas consultas SSH, leitura de fontes, logs, endpoints GET de diagnóstico e consultas agregadas no PostgreSQL. Não houve alterações nos serviços ou nas configurações de produção, pareamento, execução dos botões administrativos ou disparo de mensagens a pacientes.

Foram executadas seis verificações locais sobre o fonte copiado da produção, com transporte simulado: telefone sem DDI; envio em dia semanal incorreto; payload não normalizado; perda da deduplicação após reinício; limites de dia em UTC; ausência de recuperação após o minuto da dose. Todas reproduziram os defeitos descritos. Ver [verification-results.json](D:/DEV/MemorIA-Mobile/audit/2026-10-16/verification-results.json).

As evidências agregadas e os snapshots ficam nesta pasta. Não foram copiados arquivos `.env`, chaves ou listas de pacientes. O diretório graphify-out existente contém extração parcial, sem graph.json; por isso a auditoria foi fundamentada diretamente nos fontes e na execução observada, sem inferir relações de um grafo inexistente.

A tentativa de persistir o contexto pelo Codex-mem falhou porque o HeraclitusDB local estava indisponível. Nenhum arquivo de memória substituto foi criado.
