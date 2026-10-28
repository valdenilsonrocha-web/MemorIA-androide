package com.memoria.mobile.ui.plans

/** One line of the Premium list: the title in bold, then the description. */
data class PremiumBenefit(val title: String, val description: String, val isNew: Boolean = false)

/** What Premium unlocks, in the order and with the text of the web page's `premium-benefits` list. */
val PREMIUM_FEATURES = listOf(
    PremiumBenefit("Cadastro ilimitado de medicações", "Cadastre quantos remédios precisar, com dose, horário e foto. Sem limite."),
    PremiumBenefit("Relatórios avançados", "Veja sua adesão ao tratamento em gráficos e leve um resumo nas consultas."),
    PremiumBenefit("Múltiplos contatos de emergência", "Cadastre filhos, cuidadores e vizinhos para serem avisados se algo faltar."),
    PremiumBenefit("Alertas aos contatos via WhatsApp e e-mail", "Se uma dose for esquecida, seus contatos recebem o aviso automaticamente."),
    PremiumBenefit("Receitas guardadas por foto no histórico", "Fotografe a receita e consulte quando quiser, na farmácia ou no consultório."),
    PremiumBenefit("Interação medicamentosa", "Saiba se dois remédios podem ser tomados juntos antes de misturar.", isNew = true),
    PremiumBenefit("Verificadores de segurança", "Interação entre remédios, perda de nutrientes e outros alertas de proteção.", isNew = true),
    PremiumBenefit("Assistente de IA", "Tire dúvidas sobre remédios e horários conversando em linguagem simples.", isNew = true),
    PremiumBenefit("Agendamento de consultas médicas", "Marque e acompanhe suas consultas no app, com lembrete antes da data."),
    PremiumBenefit("Sem anúncios", "Nenhum anúncio interrompendo. Só o que importa: seus remédios."),
    PremiumBenefit("Histórico de consultas", "Todas as visitas médicas reunidas, prontas para a próxima consulta."),
    PremiumBenefit("Reposição automática de estoque", "Acompanha seu estoque e avisa a farmácia escolhida quando está na hora de repor."),
)
