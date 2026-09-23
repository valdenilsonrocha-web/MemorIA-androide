package com.memoria.mobile.ui.prescriptions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
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
fun encodeImageFromUri(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    // A full-resolution camera shot (12+ MP) would need ~50 MB as a bitmap, so
    // it is subsampled while decoding to roughly MAX_DIMENSION first.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
    // Cameras store the picture sideways and flag it in EXIF; the browser
    // honours that flag, so the phone must too or the prescription is rotated.
    val degrees = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    val bitmap = if (degrees != 0f) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
            .also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }
    try {
        encodeBitmap(bitmap)
    } finally {
        bitmap.recycle()
    }
}.getOrNull()

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

/** The picked file's name (`file.name` on the web), or null when unknown. */
fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

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
