package com.memoria.mobile.ui.nav

/** Navigation destinations. Medication edit carries an optional id argument. */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val MEDS = "meds"
    const val HISTORY = "history"
    const val WHATSAPP = "whatsapp"
    const val SETTINGS = "settings"

    const val MED_EDIT = "med_edit"
    fun medEdit(id: String?): String = if (id == null) "$MED_EDIT/new" else "$MED_EDIT/$id"
}
