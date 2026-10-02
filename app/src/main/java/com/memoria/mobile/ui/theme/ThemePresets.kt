package com.memoria.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The web app's "Tema do Aplicativo" palettes, value for value from
 * `getThemePalettes()` in `app.js`, so the phone and the browser look like the
 * same product for the same account.
 *
 * No modo CLARO o tema pinta também o fundo e as bordas ([background] e
 * [border]), como na web — é o que faz o tema parecer aplicado e não apenas
 * decorativo; os tons de `bgSecondary` são quase brancos, por isso o texto
 * escuro por cima mantém o contraste.
 *
 * [dark] é o `primaryDark` da web (`--primary-dark`, o hover dos botões).
 * A web não tem modo escuro, por isso a app também fica sempre no claro.
 */
enum class ThemePreset(
    val id: String,
    val label: String,
    val primary: Color,
    val dark: Color,
    val light: Color,
    val secondary: Color,
    /** `bgSecondary` da paleta da web — o fundo da página com o tema aplicado. */
    val background: Color,
    /** `border` da paleta da web. */
    val border: Color,
) {
    PADRAO(
        "default", "Padrão (Atual)",
        primary = BluePrimary, dark = BluePrimaryDark, light = BluePrimaryLight, secondary = BlueSecondary,
        background = Color(0xFFF3F4F6), border = Color(0xFFD1D5DB),
    ),
    PASTEL_AZUL(
        "pastel-azul", "Pastel Azul",
        // O azul da marca: é a paleta por defeito da web e a do :root do main.css.
        primary = Color(0xFF0565F1), dark = Color(0xFF0349B2),
        light = Color(0xFFE6F0FF), secondary = Color(0xFF61A3FF),
        background = Color(0xFFF1F6FF), border = Color(0xFFCFDCF6),
    ),
    PASTEL_VERDE(
        "pastel-verde", "Pastel Verde",
        primary = Color(0xFF7FC8A9), dark = Color(0xFF5C9F84),
        light = Color(0xFFC6E8D9), secondary = Color(0xFFA8DDB5),
        background = Color(0xFFF2FBF6), border = Color(0xFFCFE9DA),
    ),
    PASTEL_ROSA(
        "pastel-rosa", "Pastel Rosa",
        primary = Color(0xFFE8A7C2), dark = Color(0xFFC77F9F),
        light = Color(0xFFF6D6E3), secondary = Color(0xFFF3BFD3),
        background = Color(0xFFFFF4F8), border = Color(0xFFF0D5E1),
    ),
    PASTEL_LILAS(
        "pastel-lilas", "Pastel Lilás",
        primary = Color(0xFFB9A7E8), dark = Color(0xFF947EC8),
        light = Color(0xFFDFD7F3), secondary = Color(0xFFD1C4F2),
        background = Color(0xFFF8F5FF), border = Color(0xFFDFD7F3),
    ),
    PASTEL_PESSEGO(
        "pastel-pessego", "Pastel Pêssego",
        primary = Color(0xFFF2B89B), dark = Color(0xFFCF9376),
        light = Color(0xFFF9DCCC), secondary = Color(0xFFFFD1B3),
        background = Color(0xFFFFF7F2), border = Color(0xFFF2DDCF),
    ),
    PASTEL_MENTA(
        "pastel-menta", "Pastel Menta",
        primary = Color(0xFF8FD3C1), dark = Color(0xFF68AB9A),
        light = Color(0xFFD3ECE6), secondary = Color(0xFFB7E8DA),
        background = Color(0xFFF2FBF9), border = Color(0xFFD3ECE6),
    ),
    PASTEL_AREIA(
        "pastel-areia", "Pastel Areia",
        primary = Color(0xFFD8B98F), dark = Color(0xFFB2926A),
        light = Color(0xFFECDCC6), secondary = Color(0xFFEAD3AE),
        background = Color(0xFFFCF8F1), border = Color(0xFFE7DCCD),
    ),
    PASTEL_CORAL(
        "pastel-coral", "Pastel Coral",
        primary = Color(0xFFF29C9C), dark = Color(0xFFCC7676),
        light = Color(0xFFF8D3D3), secondary = Color(0xFFF7B7AA),
        background = Color(0xFFFFF5F2), border = Color(0xFFF0D6D0),
    );

    companion object {
        /**
         * Unknown or missing id falls back to the web's default, `pastel-azul`,
         * whose palette is the brand blue (#0565f1).
         */
        fun from(id: String?): ThemePreset =
            entries.firstOrNull { it.id == id } ?: PASTEL_AZUL
    }
}
