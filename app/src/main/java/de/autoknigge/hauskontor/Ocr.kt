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

    /** Letzte Fehlermeldung beim Einlesen (für die Anzeige). */
    var error: String? = null

    /** Womit zuletzt gelesen wurde (für die Anzeige). */
    var method: String = ""

    private fun isPdf(ctx: Context, uri: Uri): Boolean {
        if (ctx.contentResolver.getType(uri) == "application/pdf") return true
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> val b = ByteArray(5); i.read(b) == 5 && String(b, Charsets.ISO_8859_1).startsWith("%PDF") } ?: false
        } catch (e: Exception) { false }
    }

    suspend fun scan(ctx: Context, uri: Uri): Scan? {
        error = null
        method = ""
        return try {
            if (isPdf(ctx, uri)) scanPdf(ctx, uri) else recognize(InputImage.fromFilePath(ctx, uri))
        } catch (e: SecurityException) {
            error = "Das PDF ist passwortgeschützt. Öffne es in einem PDF-Programm, speichere es ohne Passwort oder mache einen Screenshot davon."
            null
        } catch (e: Throwable) {
            error = "Das PDF konnte nicht gelesen werden (" + (e.message ?: e.javaClass.simpleName) + ")."
            null
        }
    }

    /** PDF-Seiten (höchstens 15) als Bilder darstellen und wie Fotos lesen. Auch hier läuft alles auf dem Gerät. */
    private suspend fun scanPdf(ctx: Context, uri: Uri): Scan? {
        val tmp = java.io.File(ctx.cacheDir, "import.pdf")
        ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: return null
        // Digitale PDFs (Online-Kontoauszug) haben eine Textschicht: die ist genauer als Texterkennung.
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(tmp).use { doc ->
                if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                val st = com.tom_roush.pdfbox.text.PDFTextStripper()
                st.sortByPosition = true
                val text = st.getText(doc)
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.count { Regex("\\d{1,2}\\.\\d{1,2}\\.").containsMatchIn(it) } >= 2) {
                    tmp.delete()
                    method = "Textschicht des PDFs"
                    return Scan(text, lines)
                }
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            error = "Das PDF ist passwortgeschützt. Speichere es ohne Passwort oder gib es bei der Bank ohne Schutz aus."
        } catch (e: Throwable) {
            // weiter mit Texterkennung
        }
        val rows = ArrayList<String>()
        val text = StringBuilder()
        android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { pr ->
            for (p in 0 until minOf(pr.pageCount, 15)) {
                pr.openPage(p).use { page ->
                    val w = 2000
                    val h = (w.toFloat() * page.height / page.width).toInt()
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    recognize(InputImage.fromBitmap(bmp, 0))?.let { rows.addAll(it.rows); text.append(it.text).append('\n') }
                    bmp.recycle()
                }
            }
        }
        tmp.delete()
        method = "Texterkennung (Bild), PDF ohne lesbare Textschicht"
        return if (rows.isEmpty()) null else Scan(text.toString(), rows)
    }

    private suspend fun recognize(image: InputImage): Scan? = suspendCancellableCoroutine { cont ->
        try {
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

    // ---------- Zähler ablesen: Ausschnitt, mehrere Bildvarianten, Plausibilität ----------

    class MeterRead(val values: List<String>, val numbers: List<String>)

    /** Foto laden (EXIF-Drehung beachtet), höchstens [maxSide] Pixel an der langen Seite. */
    fun loadBitmap(ctx: Context, src: Uri, maxSide: Int = 3000): android.graphics.Bitmap? = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(src)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(src)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) null else {
            val rot = ctx.contentResolver.openInputStream(src)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rot == 0f) bmp else {
                val m = android.graphics.Matrix().apply { postRotate(rot) }
                android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        }
    } catch (e: Throwable) {
        null
    }

    /** Bildvarianten: 0 Original, 1 Graustufen mit Kontrast, 2 invertiert (helle Ziffern auf dunklem Display), 3 Schwarzweiß, 4 Schwarzweiß invertiert. */
    private fun variant(src: android.graphics.Bitmap, mode: Int): android.graphics.Bitmap {
        var b = src
        val sc = (1200f / src.width).coerceIn(1f, 6f)
        if (sc > 1.05f) {
            var w = (src.width * sc).toInt()
            var h = (src.height * sc).toInt()
            val big = maxOf(w, h)
            if (big > 3200) { w = w * 3200 / big; h = h * 3200 / big }
            b = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        }
        if (mode == 0) return b
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(px.size) { i ->
            val p = px[i]
            ((p shr 16 and 255) * 30 + (p shr 8 and 255) * 59 + (p and 255) * 11) / 100
        }
        val hist = IntArray(256)
        gray.forEach { hist[it]++ }
        val total = gray.size
        var acc = 0
        var lo = 0
        while (lo < 255 && acc + hist[lo] < total * 0.02) { acc += hist[lo]; lo++ }
        acc = 0
        var hi = 255
        while (hi > 0 && acc + hist[hi] < total * 0.02) { acc += hist[hi]; hi-- }
        if (hi - lo < 10) { lo = 0; hi = 255 }
        val out = IntArray(px.size)
        for (i in gray.indices) {
            var g = ((gray[i] - lo) * 255 / (hi - lo)).coerceIn(0, 255)
            if (mode >= 3) g = if (g > 128) 255 else 0
            if (mode == 2 || mode == 4) g = 255 - g
            out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val res = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    /** Ziffernfolge ohne Komma bekommt die Nachkommastellen des Zählwerks. */
    private fun withDecimals(c: String, dec: Int): String {
        if (c.contains('.')) return c.trimStart('0').let { if (it.startsWith(".") || it.isEmpty()) "0$it" else it }
        return if (dec > 0 && c.length > dec) c.dropLast(dec).trimStart('0').ifEmpty { "0" } + "." + c.takeLast(dec)
        else c.trimStart('0').ifEmpty { "0" }
    }

    private val obisRx = Regex("(?i)1[.,]8[.,]0[^\\d]{0,12}(\\d{3,9}(?:[.,]\\d{1,3})?)")

    /** Zählernummern im Text: nach Stichwort (Zähler-Nr., Serien-Nr.), im Format 1EMH0012345678 oder als lange Ziffernfolge. */
    fun meterNumbers(text: String): List<String> {
        val flat = text.replace('\n', ' ')
        val out = LinkedHashSet<String>()
        Regex("(?i)(?:z[äa]hler[- ]?(?:nr|nummer|no)|ser(?:ien)?[- ]?(?:nr|nummer|no)|\\bnr|\\bno)\\.?\\s*[:.]?\\s*([A-Z0-9][A-Z0-9 ]{5,17})")
            .findAll(flat).forEach { m ->
                val g = m.groupValues[1].trim().split(' ').takeWhile { it.length >= 2 || it.all { c -> c.isDigit() } }.joinToString("")
                if (g.length in 6..16 && g.any { it.isDigit() }) out.add(g.uppercase())
            }
        Regex("\\b\\d[A-Z]{2,4}\\d{6,12}\\b").findAll(flat).forEach { out.add(it.value) }
        Regex("(?<![\\d.,])\\d{8,12}(?![\\d])").findAll(flat).forEach { out.add(it.value) }
        return out.take(5)
    }

    /**
     * Zählerstand und Zählernummer aus dem Foto. [crop] ist der gewählte Ausschnitt mit den Ziffern, [full] das ganze Foto.
     * Der Ausschnitt wird in mehreren Bildvarianten gelesen; Treffer, die in mehreren Varianten vorkommen und zum letzten
     * Stand passen (nicht kleiner, nicht unglaublich viel größer), stehen vorn.
     */
    suspend fun readMeter(full: android.graphics.Bitmap, crop: android.graphics.Bitmap, prev: Double?, decimals: Int): MeterRead {
        val score = LinkedHashMap<String, Double>()
        val texts = ArrayList<String>()
        for (mode in 0..4) {
            val b = variant(crop, mode)
            val sc = recognize(InputImage.fromBitmap(b, 0)) ?: continue
            texts.add(sc.text)
            val flat = sc.text.replace('\n', ' ')
            obisRx.findAll(flat).forEach { m -> val k = withDecimals(m.groupValues[1].replace(',', '.'), decimals); score[k] = (score[k] ?: 0.0) + 3.0 }
            extract(sc.text).forEachIndexed { i, c ->
                val k = withDecimals(c, decimals)
                score[k] = (score[k] ?: 0.0) + 1.0 + (if (i == 0) 0.5 else 0.0)
            }
        }
        // Zählernummer aus dem ganzen Foto und den Ausschnitten
        val fullText = recognize(InputImage.fromBitmap(variant(full, 0), 0))?.text ?: ""
        val numbers = meterNumbers(fullText + "\n" + texts.joinToString("\n"))
        val ranked = score.entries.map { (k, s) ->
            val v = k.toDoubleOrNull()
            var x = s
            val intDigits = k.substringBefore('.').length
            if (intDigits in 4..9) x += 0.5
            if (v != null && prev != null) {
                x += if (v >= prev && v - prev <= maxOf(prev * 0.25, 300.0)) 3.0 else if (v < prev) -3.0 else -0.5
            }
            k to x
        }.sortedByDescending { it.second }.map { it.first }
        return MeterRead(ranked.take(6), numbers)
    }
}
