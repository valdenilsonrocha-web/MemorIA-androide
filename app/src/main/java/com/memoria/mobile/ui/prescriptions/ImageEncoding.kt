package com.memoria.mobile.ui.prescriptions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * The backend rejects anything over 5 MB of decoded image bytes, and a modern
 * phone camera clears that with one shot. Pictures are therefore downscaled to
 * [MAX_DIMENSION] and re-encoded as JPEG, dropping quality step by step until
 * the payload fits.
 */
private const val MAX_DIMENSION = 1600
private const val MAX_BYTES = 5 * 1024 * 1024
private val QUALITY_STEPS = intArrayOf(85, 70, 55, 40)

/** Reads [uri] and returns a `data:image/jpeg;base64,...` URL, or null on failure. */
fun encodeImageFromUri(context: Context, uri: Uri): String? {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    return try {
        encodeBitmap(bitmap)
    } finally {
        bitmap.recycle()
    }
}

/** Same as [encodeImageFromUri], for a bitmap already in memory (camera preview). */
fun encodeBitmap(source: Bitmap): String? {
    val scaled = downscale(source)
    for (quality in QUALITY_STEPS) {
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        if (bytes.size <= MAX_BYTES) {
            if (scaled !== source) scaled.recycle()
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
    if (scaled !== source) scaled.recycle()
    return null
}

/** Decodes a stored `data:` URL back to a bitmap for display. */
fun decodeDataUrl(dataUrl: String): Bitmap? {
    val base64 = dataUrl.substringAfter(",", missingDelimiterValue = "")
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun downscale(bitmap: Bitmap): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= MAX_DIMENSION) return bitmap
    val ratio = MAX_DIMENSION.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).toInt().coerceAtLeast(1),
        (bitmap.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}
