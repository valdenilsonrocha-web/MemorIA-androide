package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User

/**
 * What the free plan allows, mirroring `getPlanLimits()` in the web app:
 * `{ maxContacts: 1, maxMedications: 2 }` free, `{ maxContacts: 3, maxMedications: Infinity }` Premium.
 *
 * The backend enforces the caregiver cap but NOT the medication one, so without
 * this the phone quietly handed out unlimited medications and prescription
 * photos that the website charges for.
 *
 * The messages are the web's `showToast` strings, emoji included, so they can go
 * straight to [MemoriaToast.show].
 */
object PlanLimits {

    /**
     * Interruptor temporário: com isto ligado, uma conta gratuita tem os mesmos
     * recursos que o Premium (medicamentos, contatos, receitas, relatórios,
     * farmácia, Alexa). Voltar a `false` restaura os limites do plano.
     */
    const val FREE_HAS_ALL_FEATURES = true

    /** Contas gratuitas (após o período de teste de 7 dias) guardam 2 medicamentos; Premium/Trial é ilimitado. */
    const val FREE_MEDICATIONS = 2

    /** Contatos de emergência: 1 no gratuito, 3 no Premium. */
    const val FREE_CONTACTS = 1
    const val PREMIUM_CONTACTS = 3

    fun hasFullAccess(user: User?): Boolean = FREE_HAS_ALL_FEATURES || user?.isPremium == true

    /** Cópia usada nas respostas do servidor, para os ecrãs que leem `user.isPremium` directo. */
    fun User.withTemporaryFullAccess(): User =
        if (FREE_HAS_ALL_FEATURES) copy(isPremium = true, trialExpired = false) else this

    fun maxMedications(user: User?): Int =
        if (hasFullAccess(user)) Int.MAX_VALUE else FREE_MEDICATIONS

    fun maxContacts(user: User?): Int =
        if (hasFullAccess(user)) PREMIUM_CONTACTS else FREE_CONTACTS

    fun canAddMedication(user: User?, activeCount: Int): Boolean =
        activeCount < maxMedications(user)

    /** Fotos de receitas liberadas durante o período de 7 dias ou com assinatura paga ativa. */
    fun canStorePrescriptions(user: User?): Boolean = hasFullAccess(user)

    /** O `403` do backend (`middleware/auth.js`) quando o teste acaba. */
    const val TRIAL_EXPIRED_MESSAGE =
        "Período de teste de 7 dias finalizado. Assine o plano para continuar com acesso total a todos os recursos."

    /** app.js `handleMedicationSubmit`. */
    @Suppress("UNUSED_PARAMETER")
    fun medicationLimitMessage(user: User? = null): String =
        "⭐ No plano gratuito você pode cadastrar até $FREE_MEDICATIONS medicamentos. " +
            "Ative o Premium para medicamentos ilimitados."

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
