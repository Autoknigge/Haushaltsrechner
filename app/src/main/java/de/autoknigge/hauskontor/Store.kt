package de.autoknigge.hauskontor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Lokale Speicherung. Es verlässt nichts das Gerät. */
class Store(context: Context) {
    private val ctx = context.applicationContext
    private val file = File(ctx.filesDir, "hauskontor.json")

    fun load(): Data = try {
        if (file.exists()) Data.fromJson(JSONObject(file.readText())) else Data()
    } catch (e: Exception) {
        Data()
    }

    fun save(d: Data) {
        val tmp = File(ctx.filesDir, "hauskontor.json.tmp")
        tmp.writeText(d.toJson().toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun photoFile(name: String): File = File(File(ctx.filesDir, "belege"), name)

    /** Verkleinert das Foto auf höchstens 1600 Pixel, dreht es richtig und legt es im App-Speicher ab. Gibt den Dateinamen zurück. */
    fun savePhoto(src: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) null else {
            val rot = ctx.contentResolver.openInputStream(src)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = 1600f / maxOf(bmp.width, bmp.height)
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rot != 0f) m.postRotate(rot)
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val fin: Bitmap = bmp
            val dir = File(ctx.filesDir, "belege").also { it.mkdirs() }
            val name = UUID.randomUUID().toString() + ".jpg"
            File(dir, name).outputStream().use { fin.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }
    } catch (e: Exception) {
        null
    }

    /** Foto aus einer Sicherung zurückspielen. Nur einfache Dateinamen sind erlaubt. */
    fun writePhoto(name: String, bytes: ByteArray) {
        if (!Regex("^[A-Za-z0-9._-]{1,80}$").matches(name) || name.contains("..")) return
        val dir = File(ctx.filesDir, "belege").also { it.mkdirs() }
        File(dir, name).writeBytes(bytes)
    }

    fun clearPhotos() {
        File(ctx.filesDir, "belege").deleteRecursively()
    }

    fun deletePhoto(name: String?) {
        if (name != null) photoFile(name).delete()
    }

    fun wipe() {
        file.delete()
        File(ctx.filesDir, "belege").deleteRecursively()
    }
}
