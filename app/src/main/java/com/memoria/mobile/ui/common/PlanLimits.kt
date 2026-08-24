package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User

/**
 * What the free plan allows, mirroring `getPlanLimits()` in the web app.
 *
 * The backend enforces the caregiver cap but NOT the medication one, so without
 * this the phone quietly handed out unlimited medications and prescription
 * photos that the website charges for.
 */
object PlanLimits {

    /** Free accounts keep 2 active medications; Premium is unlimited. */
    const val FREE_MEDICATIONS = 2

    fun maxMedications(user: User?): Int =
        if (user?.isPremium == true) Int.MAX_VALUE else FREE_MEDICATIONS

    fun canAddMedication(user: User?, activeCount: Int): Boolean =
        activeCount < maxMedications(user)

    /** Storing prescription photos is a paid feature, as on the web. */
    fun canStorePrescriptions(user: User?): Boolean = user?.isPremium == true

    fun medicationLimitMessage(): String =
        "O plano gratuito guarda $FREE_MEDICATIONS medicamentos. " +
            "Ative o Premium para cadastrar quantos precisar."

    fun prescriptionLimitMessage(): String =
        "Guardar fotos de receitas é um recurso do Premium."
}
