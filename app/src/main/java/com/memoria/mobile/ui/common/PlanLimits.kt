package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User

/**
 * What the free plan allows, mirroring the web app and `services/freePlan.js` on the server:
 * 1 medication with several times, or 2 medications with 1 time each; 1 emergency contact;
 * no prescription photos, AI assistant or interaction check. The server refuses the same.
 *
 * The messages are the web's `showToast` strings, emoji included, so they can go
 * straight to [MemoriaToast.show].
 */
object PlanLimits {

    /**
     * Interruptor de emergência: `true` dá a toda conta gratuita os recursos do Premium.
     * Desligado desde 28/10/2026, como no servidor e no site.
     */
    const val FREE_HAS_ALL_FEATURES = false

    /** A regra de medicamentos do plano gratuito, para os textos do app. */
    const val FREE_MEDICATION_RULE = "1 medicamento com vários horários ou 2 medicamentos com 1 horário cada"

    /** Contatos de emergência: 1 no gratuito, 3 no Premium. */
    const val FREE_CONTACTS = 1
    const val PREMIUM_CONTACTS = 3

    fun hasFullAccess(user: User?): Boolean = FREE_HAS_ALL_FEATURES || user?.isPremium == true

    /** Cópia usada nas respostas do servidor, para os ecrãs que leem `user.isPremium` directo. */
    fun User.withTemporaryFullAccess(): User =
        if (FREE_HAS_ALL_FEATURES) copy(isPremium = true, trialExpired = false) else this

    fun maxContacts(user: User?): Int =
        if (hasFullAccess(user)) PREMIUM_CONTACTS else FREE_CONTACTS

    /** Horários por dia de cada medicamento ativo. */
    fun fitsFreePlan(timesPerMedication: List<Int>): Boolean =
        timesPerMedication.size <= 1 ||
            (timesPerMedication.size == 2 && timesPerMedication.all { it <= 1 })

    /**
     * Pode gravar um medicamento com [nextTimes] horários? [others] são os horários dos outros
     * medicamentos ativos; [previousTimes], os deste antes da edição (null se é novo ou estava
     * inativo). Quem já passou do limite pode editar e reduzir, mas não acrescentar.
     */
    fun allowsMedication(user: User?, others: List<Int>, previousTimes: Int?, nextTimes: Int): Boolean =
        hasFullAccess(user) || fitsFreePlan(others + nextTimes) ||
            (previousTimes != null && nextTimes <= previousTimes)

    /** A assistente de IA (que também faz a triagem de interações) é do Premium e dos 7 dias de teste. */
    fun canUseAssistant(user: User?): Boolean = hasFullAccess(user)

    /** Fotos de receitas liberadas durante o período de 7 dias ou com assinatura paga ativa. */
    fun canStorePrescriptions(user: User?): Boolean = hasFullAccess(user)

    /** O `403` do backend (`middleware/auth.js`) quando o teste acaba. */
    const val TRIAL_EXPIRED_MESSAGE =
        "Período de teste de 7 dias finalizado. Assine o plano para continuar com acesso total a todos os recursos."

    /** app.js `handleMedicationSubmit`. */
    @Suppress("UNUSED_PARAMETER")
    fun medicationLimitMessage(user: User? = null): String =
        "⭐ No plano gratuito você pode ter $FREE_MEDICATION_RULE. " +
            "Ative o Premium para cadastrar sem limite."

    /** app.js `handlePrescriptionUpload` / the prescriptions page. */
    @Suppress("UNUSED_PARAMETER")
    fun prescriptionLimitMessage(user: User? = null): String =
        "⭐ Recurso premium: ative o Premium para salvar receitas."

    /** app.js `saveContact` when the cap is reached. */
    fun contactLimitMessage(user: User? = null): String =
        if (hasFullAccess(user)) {
            "⭐ No plano Premium é permitido cadastrar até $PREMIUM_CONTACTS contatos de emergência."
        } else {
            "⭐ No plano gratuito é permitido $FREE_CONTACTS contato. Ative o Premium para cadastrar até $PREMIUM_CONTACTS contatos."
        }

    /** app.js, subscription check after the trial ends. */
    const val TRIAL_ENDED_TOAST =
        "⏰ Seu teste grátis terminou e a assinatura não está ativa. Reative nos Planos."
}
