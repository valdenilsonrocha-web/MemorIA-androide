package com.memoria.mobile.ui.meds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memoria.mobile.R
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.data.remote.Medication
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Everything the web draws for a medication: the pharmaceutical form picker
// (`getMedFormDefs()`), pill shapes and colour swatches (`index.html`,
// `#pillShapeOpts` / `#pillColorSwatches`) and `getMedicationFormMarkup()`.
// ---------------------------------------------------------------------------

/** One SVG primitive of a form icon, in the web's 32×32 viewBox. */
data class SvgEl(
    val d: String,
    val strokeWidth: Float = 2f,
    val strokeAlpha: Float = 1f,
    /** Alpha of a `fill="currentColor"`; 0 = `fill="none"`. */
    val fillAlpha: Float = 0f,
    val stroke: Boolean = true,
    /** `transform="rotate(deg cx cy)"`. */
    val rotate: Triple<Float, Float, Float>? = null,
)

data class MedFormDef(
    val value: String,
    val label: String,
    val hasColor: Boolean,
    val hasShape: Boolean,
    val hasDualColor: Boolean = false,
    val svg: List<SvgEl>,
)

private fun circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
    "M${cx - rx},${cy} a$rx,$ry 0 1,0 ${2 * rx},0 a$rx,$ry 0 1,0 ${-2 * rx},0 Z"
private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = "M$x1,$y1 L$x2,$y2"
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): String {
    if (r <= 0f) return "M$x,$y h$w v$h h${-w} Z"
    return "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r " +
        "h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r} Z"
}
private fun poly(vararg p: Float): String =
    p.toList().chunked(2).mapIndexed { i, (x, y) -> "${if (i == 0) "M" else "L"}$x,$y" }.joinToString(" ") + " Z"

/** Same list, order, labels and flags as `getMedFormDefs()` in app.js. */
val MED_FORM_DEFS: List<MedFormDef> = listOf(
    MedFormDef("comprimido", "Comprimido", hasColor = true, hasShape = true, svg = listOf(
        SvgEl(circle(16f, 16f, 11f)), SvgEl(line(6f, 16f, 26f, 16f)),
    )),
    MedFormDef("capsula", "Cápsula", hasColor = false, hasShape = false, hasDualColor = true, svg = listOf(
        SvgEl(rect(4f, 10f, 24f, 12f, 6f)), SvgEl(line(16f, 10f, 16f, 22f)),
        SvgEl("M4 10a6 6 0 0 1 0 12z", fillAlpha = .22f, stroke = false),
    )),
    MedFormDef("liquido", "Líquido", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M12 4h8v5l5 8v11a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V17l5-8V4z"),
        SvgEl(line(7f, 21f, 25f, 21f), strokeAlpha = .45f),
    )),
    MedFormDef("locao", "Loção", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(9f, 14f, 14f, 16f, 3f)), SvgEl("M13 14V9h6v5"), SvgEl("M19 9h5v5"),
    )),
    MedFormDef("spray", "Spray", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(8f, 17f, 13f, 13f, 3f)), SvgEl("M15 17v-5h9"), SvgEl("M24 12h4M26 9l-2 3"),
    )),
    MedFormDef("pomada", "Pomada", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(4f, 19f, 20f, 11f, 2f)), SvgEl(line(4f, 23f, 24f, 23f), strokeAlpha = .4f),
        SvgEl("M24 22v-8c0-2 2-3 3-1"),
    )),
    MedFormDef("gotas", "Gotas", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M16 5L24 19a8 8 0 1 1-16 0Z"),
    )),
    MedFormDef("gel", "Gel", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(9f, 14f, 14f, 16f, 4f)), SvgEl(rect(12f, 8f, 8f, 8f, 2f)),
        SvgEl("M18 7Q22 8 26 5", strokeWidth = 1.5f),
    )),
    MedFormDef("supositorio", "Supositório", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M16 4L24 14v16a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V14Z"),
    )),
    MedFormDef("injecao", "Injeção", hasColor = true, hasShape = false, svg = listOf(
        // Agulha fina chanfrada
        SvgEl(line(3f, 29f, 9f, 23f), strokeWidth = 1.8f),
        // Corpo cilíndrico graduado da seringa
        SvgEl(rect(9f, 9f, 13f, 8f, 1.2f), strokeWidth = 2f, rotate = Triple(45f, 15.5f, 13f)),
        // Graduações da escala de medição da seringa
        SvgEl(line(12f, 17f, 14.5f, 14.5f), strokeWidth = 1.4f),
        SvgEl(line(14f, 19f, 16.5f, 16.5f), strokeWidth = 1.4f),
        SvgEl(line(16f, 21f, 18.5f, 18.5f), strokeWidth = 1.4f),
        // Haste do êmbolo
        SvgEl(line(20f, 11f, 25f, 6f), strokeWidth = 2.2f),
        // Flange / anel de apoio do êmbolo
        SvgEl(line(23f, 4f, 27f, 8f), strokeWidth = 2.5f),
    )),
    MedFormDef("creme", "Creme", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(ellipse(16f, 28f, 10f, 3.5f)), SvgEl(rect(8f, 16f, 16f, 14f, 4f)), SvgEl("M12 16Q16 8 20 16"),
    )),
    MedFormDef("po", "Pó", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(6f, 18f, 20f, 12f, 2f)), SvgEl("M10 18l-2-9h16l-2 9"),
        SvgEl(circle(12f, 23f, 1.5f), fillAlpha = 1f), SvgEl(circle(16f, 26f, 1.5f), fillAlpha = 1f),
        SvgEl(circle(20f, 23f, 1.5f), fillAlpha = 1f),
    )),
    MedFormDef("adesivo", "Adesivo", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(4f, 4f, 24f, 24f, 4f)), SvgEl(line(16f, 10f, 16f, 22f), strokeWidth = 2.5f),
        SvgEl(line(10f, 16f, 22f, 16f), strokeWidth = 2.5f),
    )),
    MedFormDef("espuma", "Espuma", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(10f, 20f, 12f, 10f, 2f)), SvgEl("M14 20v-5h4v5"),
        SvgEl(circle(12f, 13f, 2.5f), fillAlpha = .35f), SvgEl(circle(20f, 10f, 3.5f), fillAlpha = .35f),
        SvgEl(circle(16f, 7f, 2.5f), fillAlpha = .35f),
    )),
    MedFormDef("inalador", "Inalador", hasColor = false, hasShape = false, svg = listOf(
        SvgEl(rect(12f, 18f, 16f, 12f, 3f)), SvgEl(rect(17f, 10f, 7f, 10f, 2f)),
        SvgEl("M6 26Q12 22 12 26", strokeWidth = 2.5f),
    )),
    MedFormDef("goma", "Goma", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(rect(4f, 12f, 24f, 10f, 5f)), SvgEl(line(4f, 17f, 28f, 17f), strokeAlpha = .4f),
    )),
    MedFormDef("anel", "Anel", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(circle(16f, 16f, 10f), strokeWidth = 4f),
    )),
    MedFormDef("cha", "Chá", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M6 16Q6 28 16 28Q26 28 26 16L24 14H8Z"), SvgEl("M26 20Q31 18 30 24Q29 27 26 24"),
        SvgEl(line(12f, 14f, 12f, 10f)), SvgEl(line(16f, 14f, 16f, 8f)), SvgEl(line(20f, 14f, 20f, 10f)),
    )),
    MedFormDef("erva", "Erva", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M16 30Q16 18 10 10Q5 6 5 6Q10 14 14 20"), SvgEl("M16 30Q16 16 24 10Q29 6 29 6Q24 14 20 20"),
        SvgEl(line(16f, 10f, 16f, 30f)),
    )),
    MedFormDef("ampola", "Ampola", hasColor = true, hasShape = false, svg = listOf(
        SvgEl("M13 4h6v5l4 9v8a2 2 0 0 1-2 2H11a2 2 0 0 1-2-2v-8l4-9V4z"),
        SvgEl(line(11f, 21f, 21f, 21f), strokeAlpha = .4f), SvgEl(line(12f, 4f, 20f, 4f)),
    )),
    // Formas que no site só existem como imagem (`getMedicationFormImage`).
    MedFormDef("adesivo_bucal", "Adesivo bucal", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("caneta", "Caneta", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("colirio", "Colírio", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("comprimido_mastigavel", "Comprimido mastigável", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("efervescente", "Efervescente", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("spray_nasal", "Spray nasal", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("supositorio_infantil", "Supositório infantil", hasColor = false, hasShape = false, svg = emptyList()),
    MedFormDef("softgel", "Softgel", hasColor = true, hasShape = false, svg = listOf(
        SvgEl(ellipse(16f, 16f, 9f, 12f)), SvgEl(ellipse(13f, 12f, 2.5f, 3.5f), fillAlpha = .22f, stroke = false),
        SvgEl("M10 20 Q16 23 22 20", strokeAlpha = .35f),
    )),
)

/** `normalizeMedicationFormValue()`: known value, legacy alias, or `comprimido`. */
fun normalizeMedicationForm(value: String?): String {
    val v = value.orEmpty().lowercase()
    if (MED_FORM_DEFS.any { it.value == v }) return v
    return mapOf(
        "pills" to "comprimido", "capsule" to "capsula", "tablet" to "comprimido",
        "syringe" to "injecao", "heart" to "comprimido", "solucao" to "liquido", "outro" to "comprimido",
    )[v] ?: "comprimido"
}

fun medFormDef(value: String?): MedFormDef =
    MED_FORM_DEFS.firstOrNull { it.value == normalizeMedicationForm(value) } ?: MED_FORM_DEFS.first()

/**
 * `getMedicationFormImage()`: the illustrated icon the site shows for every form
 * except comprimido and cápsula, which are drawn. Same files as
 * frontend/public/assets/medication-icons, renamed to resource names.
 */
fun medicationFormImage(value: String?): Int? = when (normalizeMedicationForm(value)) {
    "liquido" -> R.drawable.med_xarope
    "locao" -> R.drawable.med_locao
    "spray" -> R.drawable.med_spray
    "pomada" -> R.drawable.med_pomada
    "gotas" -> R.drawable.med_gotas
    "gel" -> R.drawable.med_gel
    "supositorio" -> R.drawable.med_supositorio
    "injecao" -> R.drawable.med_injecao
    "creme" -> R.drawable.med_creme
    "po" -> R.drawable.med_po
    "adesivo" -> R.drawable.med_adesivo
    "espuma" -> R.drawable.med_espuma
    "inalador" -> R.drawable.med_inalador
    "goma" -> R.drawable.med_goma
    "anel" -> R.drawable.med_anel
    "cha" -> R.drawable.med_cha
    "erva" -> R.drawable.med_erva
    "ampola" -> R.drawable.med_ampola
    "softgel" -> R.drawable.med_softgel
    "adesivo_bucal" -> R.drawable.med_adesivo_bucal
    "caneta" -> R.drawable.med_caneta
    "colirio" -> R.drawable.med_colirio
    "comprimido_mastigavel" -> R.drawable.med_comprimido_mastigavel
    "efervescente" -> R.drawable.med_efervescente
    "spray_nasal" -> R.drawable.med_spray_nasal
    "supositorio_infantil" -> R.drawable.med_supositorio_infantil
    else -> null
}

/** The form's icon for pickers and lists: the illustration when there is one, else the outline. */
@Composable
fun MedicationFormGlyph(def: MedFormDef, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val image = medicationFormImage(def.value)
    if (image != null) {
        Image(painterResource(image), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    } else {
        FormSvgCanvas(def, color, size, modifier)
    }
}

/** `getMedicationFormLabel()`. */
fun medicationFormLabel(value: String?): String = medFormDef(value).label

/** `#pillShapeOpts`, in the same order and with the same titles. */
val PILL_SHAPES: List<Pair<String, String>> = listOf(
    "round" to "Redondo",
    "oval" to "Oval",
    "oblong" to "Oblongo",
    "rectangular" to "Retangular",
    "diamond" to "Losango",
    "hexagon" to "Hexagonal",
    "scored" to "Ranhura central",
)

/** `#pillColorSwatches` (also used for both capsule halves). */
val PILL_COLORS: List<Pair<String, String>> = listOf(
    "#ffffff" to "Branco",
    "#e5e7eb" to "Cinza claro",
    "#f5e6c8" to "Creme",
    "#fbbf24" to "Amarelo",
    "#f97316" to "Laranja",
    "#fda4af" to "Rosa claro",
    "#ec4899" to "Rosa",
    "#ef4444" to "Vermelho",
    "#a855f7" to "Roxo",
    "#2f7dff" to "Azul",
    "#7dd3fc" to "Azul claro",
    "#22c55e" to "Verde",
    "#15803d" to "Verde escuro",
    "#0891b2" to "Turquesa",
    "#9ca3af" to "Cinza",
    "#92400e" to "Marrom",
    "#d97706" to "Dourado",
    "#f87171" to "Coral",
)

/** `sanitizePillColor()`: `#rrggbb` or the default light grey. */
fun sanitizePillColor(value: String?, fallback: String = "#e5e7eb"): String {
    val v = value.orEmpty().trim().lowercase()
    return if (Regex("^#[0-9a-f]{6}$").matches(v)) v else fallback
}

fun hexColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(sanitizePillColor(hex)))
}.getOrDefault(Color(0xFFE5E7EB))

private val PrimaryDark = Color(0xFF003B9A) // --primary-dark
private val IconBgStart = Color(0xFFD1FAE5)
private val IconBgEnd = Color(0xFFA7F3D0)

/**
 * `getMedicationFormMarkup()` — the medication's icon. [size] is the web's
 * 48px pill / 64px tile; the inline variant (`medication-inline-icon`) is 28.
 */
@Composable
fun MedicationIcon(look: MedicationLook?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val form = normalizeMedicationForm(look?.medicationForm)
    val def = medFormDef(form)
    // `.medication-icon` is a 64px tile while `.medication-pill-icon` overrides
    // it to 48px; the inline variant (`.medication-inline-icon`) is 28px for both.
    val inline = size <= 28.dp
    val tile = if (inline) size else size * (64f / 48f)
    val radius = if (inline) 9.dp else 24.dp
    when (form) {
        "comprimido" -> PillShapeIcon(
            shape = look?.pillShape ?: "round",
            color = hexColor(look?.pillColor ?: "#e5e7eb"),
            size = size,
            modifier = modifier,
        )
        // `.medication-capsule-icon svg` is 48×24 (viewBox 32×32, so the 24px
        // height sets the scale) — i.e. 3/8 of the 64px tile.
        "capsula" -> IconTile(tile, radius, modifier) {
            CapsuleCanvas(
                hexColor(look?.capsuleColor1 ?: "#e5e7eb"),
                hexColor(look?.capsuleColor2 ?: "#2f7dff"),
                if (inline) tile * 0.75f else tile * 0.375f,
            )
        }
        // `.medication-image-icon img`: 56px inside the 64px tile; 28px inline.
        else -> if (medicationFormImage(form) != null) IconTile(tile, radius, modifier) {
            MedicationFormGlyph(def, PrimaryDark, if (inline) tile else tile * (56f / 64f))
        }
        // `.medication-svg-icon svg`: 32px inside the 64px tile, in the chosen
        // colour for coloured forms and `--primary-dark` for the others.
        else IconTile(tile, radius, modifier) {
            FormSvgCanvas(
                def = def,
                color = if (def.hasColor) hexColor(look?.pillColor ?: "#e5e7eb") else PrimaryDark,
                size = if (inline) tile * 0.6f else tile * 0.5f,
            )
        }
    }
}

/** The green rounded tile behind non-pill icons (`.medication-icon`). */
@Composable
private fun IconTile(size: Dp, radius: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(IconBgStart, IconBgEnd))),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Outline icon for [def] in `currentColor` = [color]. */
@Composable
fun FormSvgCanvas(def: MedFormDef, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val paths = remember(def.value) { def.svg.map { it to PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 32f
        scale(k, k, pivot = Offset.Zero) {
            paths.forEach { (el, path) ->
                val draw: DrawScope.() -> Unit = {
                    if (el.fillAlpha > 0f) drawPath(path, color.copy(alpha = el.fillAlpha))
                    if (el.stroke) {
                        drawPath(
                            path,
                            color.copy(alpha = el.strokeAlpha),
                            style = Stroke(el.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
                val r = el.rotate
                if (r != null) rotate(r.first, pivot = Offset(r.second, r.third)) { draw() } else draw()
            }
        }
    }
}

/** The two-colour capsule SVG from `getMedicationFormMarkup('capsula')`. */
@Composable
private fun CapsuleCanvas(c1: Color, c2: Color, size: Dp) {
    val left = remember { PathParser().parsePathString("M16 10 L10 10 A6 6 0 0 0 10 22 L16 22 Z").toPath() }
    val right = remember { PathParser().parsePathString("M16 10 L22 10 A6 6 0 0 1 22 22 L16 22 Z").toPath() }
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 32f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(left, c1)
            drawPath(right, c2)
            drawRoundRect(
                Color.Black.copy(alpha = .22f),
                topLeft = Offset(4f, 10f),
                size = Size(24f, 12f),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(1.5f),
            )
            drawLine(Color.Black.copy(alpha = .18f), Offset(16f, 10f), Offset(16f, 22f), strokeWidth = 1f)
        }
    }
}

/**
 * `.medication-pill-icon` + `.medication-pill-<shape>`: a 3-D looking tablet.
 * Dimensions are the CSS ones (48px box) scaled to [size].
 */
@Composable
fun PillShapeIcon(shape: String, color: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val key = shape.lowercase().takeIf { s -> PILL_SHAPES.any { it.first == s } } ?: "round"
    // Wider shapes (oval, oblong, rectangular) overflow a square box on the web too.
    val widthFactor = when (key) { "rectangular" -> 78f / 48f; "oblong" -> 68f / 48f; "oval" -> 56f / 48f; else -> 1f }
    Canvas(modifier.size(width = size * widthFactor, height = size)) {
        val k = this.size.height / 48f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val border = Color.Black.copy(alpha = .18f)
        val bw = 1.5f * k
        fun body(w: Float, h: Float, radius: Float): RoundRect =
            RoundRect(Rect(Offset(cx - w * k / 2, cy - h * k / 2), Size(w * k, h * k)), CornerRadius(radius * k))

        fun fillAndBorder(path: Path) {
            drawPath(path, color)
            drawPath(path, border, style = Stroke(bw))
        }

        fun highlight(w: Float, h: Float) {
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .75f), Color.Transparent),
                    center = Offset(cx - w * k * 0.15f, cy - h * k * 0.15f),
                    radius = (w * k * 0.2f).coerceAtLeast(1f),
                ),
                topLeft = Offset(cx - w * k * 0.35f, cy - h * k * 0.35f),
                size = Size(w * k * 0.4f, h * k * 0.35f),
            )
        }

        fun score(h: Float) {
            drawLine(Color.Black.copy(alpha = .25f), Offset(cx, cy - h * k / 2 + bw), Offset(cx, cy + h * k / 2 - bw), strokeWidth = k)
            drawLine(Color.White.copy(alpha = .5f), Offset(cx + k, cy - h * k / 2 + bw), Offset(cx + k, cy + h * k / 2 - bw), strokeWidth = k)
        }

        when (key) {
            "diamond" -> rotate(45f, pivot = Offset(cx, cy)) {
                fillAndBorder(Path().apply { addRoundRect(body(26f, 26f, 4f)) })
                highlight(26f, 26f)
            }
            "hexagon" -> {
                val s = 36f * k
                val path = Path().apply {
                    moveTo(cx, cy - s / 2); lineTo(cx + s / 2, cy - s / 4); lineTo(cx + s / 2, cy + s / 4)
                    lineTo(cx, cy + s / 2); lineTo(cx - s / 2, cy + s / 4); lineTo(cx - s / 2, cy - s / 4); close()
                }
                fillAndBorder(path)
                highlight(36f, 36f)
            }
            else -> {
                val (w, h, r) = when (key) {
                    "oval" -> Triple(56f, 32f, 16f)
                    "rectangular" -> Triple(78f, 30f, 20f)
                    "oblong" -> Triple(68f, 24f, 12f)
                    else -> Triple(48f, 48f, 24f) // round, scored
                }
                val path = Path().apply {
                    if (key == "oval") addOval(Rect(Offset(cx - w * k / 2, cy - h * k / 2), Size(w * k, h * k)))
                    else addRoundRect(body(w, h, r.coerceAtMost(h / 2)))
                }
                fillAndBorder(path)
                if (key == "scored" || key == "rectangular") score(h)
                highlight(w, h)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Treatment helpers — `getTreatmentEndDate()` / `getTreatmentSummary()`.
// ---------------------------------------------------------------------------

private val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatDateBr(date: LocalDate): String = date.format(DATE_BR)

/** Start = the medication's creation date (the web's `treatmentStartDate || createdAt`). */
fun treatmentEndDate(med: Medication, durationDays: Int?): LocalDate? {
    val duration = durationDays?.takeIf { it > 0 } ?: return null
    val start = com.memoria.mobile.ui.common.Schedule.localDateOf(med.createdAt) ?: return null
    return start.plusDays((duration - 1).toLong())
}

/** "Uso contínuo", "7 dias (até dd/MM/aaaa)", "7 dias", or null. */
fun treatmentSummary(med: Medication, continuousUse: Boolean, durationDays: Int?): String? {
    if (continuousUse) return "Uso contínuo"
    val duration = durationDays?.takeIf { it > 0 } ?: return null
    val end = treatmentEndDate(med, duration) ?: return "$duration dias"
    return "$duration dias (até ${formatDateBr(end)})"
}

// ---------------------------------------------------------------------------
// Dose status — `getStatusLabel()` and the `.status-*` / `.medication-card.*`
// colours of main.css.
// ---------------------------------------------------------------------------

/** upcoming | pending | taken | missed — `reminderService.getMedicationStatus()`. */
fun doseStatusLabel(status: String): String = when (status) {
    "upcoming" -> "Próximo"
    "pending" -> "Pendente"
    "taken" -> "Tomado"
    "missed" -> "Atrasado"
    else -> status
}

/** Badge (background, text) for `.medication-status.status-*`. */
fun doseStatusColors(status: String): Pair<Color, Color> = when (status) {
    "pending" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
    "taken" -> Color(0xFFD1FAE5) to Color(0xFF065F46)
    "missed" -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
    // upcoming: main.css has no `.status-upcoming` rule — no background, the
    // card's own text colour (--text-primary).
    else -> Color.Transparent to Color(0xFF1F2937)
}

/** Left stripe of `.medication-card.<status>`. */
fun doseStatusStripe(status: String, primary: Color): Color = when (status) {
    "pending" -> Color(0xFFF59E0B)
    "taken" -> Color(0xFF66A9FF) // --secondary-color
    "missed" -> Color(0xFFEF4444)
    else -> primary
}

/** `getFrequencyLabel()`. */
fun frequencyLabel(frequency: String): String = when (frequency) {
    "daily" -> "Todos os dias"
    "alternate" -> "Dias alternados"
    "weekly" -> "Dias específicos da semana"
    else -> frequency
}

