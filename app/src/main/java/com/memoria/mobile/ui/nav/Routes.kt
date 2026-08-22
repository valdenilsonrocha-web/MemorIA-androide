package com.memoria.mobile.ui.nav

/**
 * Navigation destinations, mirroring the web app's pages.
 *
 * The first block is the bottom bar (the web's `bottom-nav`); the second is
 * everything reached from "Mais" or from a card, which shows no bottom bar and
 * carries a back arrow instead.
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Bottom bar
    const val HOME = "home"
    const val MEDS = "meds"
    const val HEALTH = "health"
    const val HISTORY = "history"
    const val MORE = "more"

    // Secondary
    const val MED_EDIT = "med_edit"
    const val MED_DETAILS = "med_details"
    const val CALENDAR = "calendar"
    const val REPORTS = "reports"
    const val REPLENISHMENT = "replenishment"
    const val DOCTORS = "doctors"
    const val PRESCRIPTIONS = "prescriptions"
    const val PROFILE = "profile"
    const val PLANS = "plans"
    const val HELP = "help"
    const val OPTIMIZATION = "optimization"
    const val ADMIN = "admin"
    const val WHATSAPP = "whatsapp"
    const val SETTINGS = "settings"

    fun medEdit(id: String?): String = if (id == null) "$MED_EDIT/new" else "$MED_EDIT/$id"

    fun medDetails(id: String): String = "$MED_DETAILS/$id"
}
