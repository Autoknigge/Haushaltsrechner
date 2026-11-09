package de.autoknigge.hauskontor

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Zählerstand per Foto lesen. Die Erkennung läuft auf dem Gerät, das Foto wird nicht hochgeladen. */
object Ocr {
    suspend fun candidates(ctx: Context, uri: Uri): List<String> = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(ctx, uri)
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            rec.process(image)
                .addOnSuccessListener {
                    rec.close()
                    if (cont.isActive) cont.resume(extract(it.text))
                }
                .addOnFailureListener {
                    rec.close()
                    if (cont.isActive) cont.resume(emptyList())
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    /** Zahlen aus dem erkannten Text, die wie ein Zählerstand aussehen: 4 bis 8 Ziffern zuerst, dann nach Länge. */
    fun extract(text: String): List<String> {
        val flat = text.replace('\n', ' ')
        val joined = flat.replace(Regex("(?<=\\d) (?=\\d)"), "")
        val rx = Regex("\\d{3,9}(?:[.,]\\d{1,3})?")
        val all = (rx.findAll(flat) + rx.findAll(joined)).map { it.value.replace(',', '.') }.distinct().toList()
        return all.sortedWith(
            compareBy<String> { digits(it) !in 4..8 }.thenByDescending { digits(it) }
        ).take(6)
    }

    private fun digits(s: String) = s.substringBefore('.').length
}
