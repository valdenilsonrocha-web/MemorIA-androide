# Google Play Billing — ativação

O Android usa Google Play Billing 9.1.0. O checkout não recebe cartão nem envia compras para Mercado Pago.
O backend valida o token via subscriptionsv2.get, confere o produto, plano, estado, validade e o ID SHA-256 da conta MemorIA. Confirma a compra somente depois de salvar o acesso.

## Play Console

1. Aplicativo com package com.memoria.mobile, conta de desenvolvedor e perfil de pagamentos habilitados.
2. Criar assinatura memoria_premium com planos básicos auto-renováveis monthly (P1M) e annual (P1Y).
3. Definir os preços no Console (referência anterior: R$ 18,90 mensal e R$ 154,80 anual), disponibilizar no Brasil e ativar ambos. O app exibe os valores retornados pela loja, sem preços fixos.
4. Esta implementação seleciona as ofertas básicas, sem teste grátis. Os 7 dias de acesso gratuito já oferecidos pelo MemorIA continuam independentes. Ofertas promocionais precisam de uma integração adicional antes de serem anunciadas no app.
5. Enviar AAB assinado para testes internos, cadastrar testadores e testadores de licença, aceitar o convite e instalar pela Play Store.

## GCP e backend

1. Habilitar Google Play Android Developer API no projeto GCP. Criar conta de serviço e conceder no Play Console acesso ao aplicativo e permissões para consultar pedidos/assinaturas e gerenciar pedidos/assinaturas.
2. Guardar o JSON da conta de serviço fora do Git, montado no servidor com acesso restrito (por exemplo, via Secret Manager). Nunca incluir no APK, mandar no chat ou publicar.
3. Configurar GOOGLE_PLAY_SERVICE_ACCOUNT_FILE com esse caminho. Ver backend/.env.example para package, produto e planos. Se alterar IDs, alterar também os campos PLAY_* em app/build.gradle.kts.
4. Antes de iniciar a nova versão do servidor, executar npm run migrate: adiciona users.google_play_subscription. Fazer backup da base antes da migração em produção.
5. Publicar o backend e agendar node scripts/reconcile-google-play.mjs a cada 5 minutos. Cada requisição autenticada também atualiza a assinatura quando a última consulta tem mais de 5 minutos. Renovações, reembolsos, suspensões e cancelamentos são conciliados por consulta periódica e pelo endpoint RTDN autenticado. Mesmo com o Google indisponível, o acesso nunca é estendido além da última validade verificada.
6. Criar um tópico Pub/Sub para RTDN; conceder roles/pubsub.publisher a google-play-developer-notifications@system.gserviceaccount.com. Cadastrar o tópico nas configurações de monetização da Play Console.
7. Criar assinatura push autenticada para https://SEU_BACKEND/api/payments/google-play/notifications usando uma conta de serviço dedicada. Configurar GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT com o e-mail dessa conta e GOOGLE_PLAY_RTDN_AUDIENCE com a audiência exata configurada na assinatura push. Conceder ao agente de serviço Pub/Sub permissão para gerar tokens OIDC nessa conta e testar uma notificação pelo Console. O endpoint verifica assinatura RSA Google, emissor, audiência, e-mail e expiração; dados do evento nunca liberam acesso sem consultar a API Google.
8. Não remover o backend Mercado Pago: ele continua atendendo clientes web e assinaturas existentes.

## Verificação antes da produção

- Compra mensal e anual com conta de licença e instalação pelo track de testes.
- Cancelamento do checkout; compra pendente sem liberação de Premium.
- Restauração após reinstalação, na mesma conta MemorIA; outra conta deve ser recusada.
- Backend indisponível: a compra pode ser restaurada posteriormente, sem confirmação antecipada pelo cliente.
- Confirmar acknowledgement, renovação acelerada, término da assinatura, suspensão e reembolso no ambiente de testes.
- Cancelar renovação em Gerenciar assinatura na Google Play e manter acesso somente até o fim do período pago.

## Compilação

Com JDK 17+ e Android SDK configurados: gradlew.bat testDebugUnitTest assembleDebug bundleRelease.
O AAB deve usar a mesma chave de upload do aplicativo no Play Console. Não publicar sem testar a compra completa: os testes locais não substituem a validação de licença e catálogo da loja.

## Ambiente localizado em 05/10/2026

Projeto: memoria-499818. VM: memoria-vm-2, zona southamerica-east1-a, IP público 35.247.217.66. Backend: /home/web2a/MemorIA/backend, executado pelo serviço systemd memoria-backend.service, usuário web2a, Node 22.23.1.

A conta atualmente anexada é 934106579663-compute@developer.gserviceaccount.com. Os escopos OAuth atuais não permitem Android Publisher nem gerenciamento de serviços GCP. Não há variáveis GOOGLE_PLAY_* na configuração do backend. O serviço em produção não foi alterado.

É necessário configurar uma conta de serviço adequada pelo GCP e autorizá-la no Play Console. Esta implementação aceita um JSON montado com caminho GOOGLE_PLAY_SERVICE_ACCOUNT_FILE; manter acesso de leitura somente para o usuário do backend e nunca versionar o arquivo. Configurar Pub/Sub conforme as instruções anteriores. Não executar deploy_to_vm.sh: esse script também interfere em outros serviços da máquina e não é apropriado para esta atualização.

Publicação deve aplicar somente os arquivos do backend desta integração, fazer backup do banco, executar a migração antes do restart de memoria-backend.service, testar health e auth, testar a notificação Pub/Sub e instalar o AAB pelo track interno da Play Store.
