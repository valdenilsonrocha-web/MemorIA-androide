package com.memoria.mobile.ui.prescriptions

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device Latin OCR (ML Kit). Portuguese prescriptions use the Latin script,
 * so the bundled model reads them without a network call and without sending
 * the photo anywhere.
 */
object PrescriptionOcr {

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result.text.trim())
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume("")
            }
            .addOnCompleteListener { recognizer.close() }
    }
}
