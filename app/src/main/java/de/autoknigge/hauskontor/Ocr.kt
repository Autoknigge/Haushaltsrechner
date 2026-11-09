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

    /** Erkannter Text und Zeilen. Zeilen, die auf gleicher Höhe liegen (Tabellenspalten), werden zu einer Zeile verbunden. */
    class Scan(val text: String, val rows: List<String>)

    suspend fun scan(ctx: Context, uri: Uri): Scan? = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(ctx, uri)
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            rec.process(image)
                .addOnSuccessListener { t ->
                    rec.close()
                    val lines = t.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
                        .sortedBy { it.boundingBox!!.centerY() }
                    val rows = ArrayList<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                    var cy = -1
                    for (l in lines) {
                        val b = l.boundingBox!!
                        if (rows.isNotEmpty() && Math.abs(b.centerY() - cy) < b.height() * 0.6) rows.last().add(l)
                        else {
                            rows.add(mutableListOf(l))
                            cy = b.centerY()
                        }
                    }
                    val out = rows.map { r -> r.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
                    if (cont.isActive) cont.resume(Scan(t.text, out))
                }
                .addOnFailureListener {
                    rec.close()
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
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
