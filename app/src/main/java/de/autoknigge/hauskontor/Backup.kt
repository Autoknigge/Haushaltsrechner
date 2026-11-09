package de.autoknigge.hauskontor

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Sicherung als ZIP-Datei (Daten und Fotos). Importiert werden außerdem reine JSON-Dateien älterer Sicherungen. */
object Backup {
    class Loaded(val data: Data, val photos: Map<String, ByteArray>)

    fun write(ctx: Context, store: Store, d: Data): File {
        val dir = File(ctx.cacheDir, "teilen").also { it.mkdirs() }
        val f = File(dir, "Hauskontor-Sicherung.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("hauskontor.json"))
            z.write(d.toJson().toString(2).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            d.readings.mapNotNull { it.photo }.distinct().forEach { name ->
                val p = store.photoFile(name)
                if (p.exists()) {
                    z.putNextEntry(ZipEntry("belege/$name"))
                    z.write(p.readBytes())
                    z.closeEntry()
                }
            }
        }
        return f
    }

    fun read(ctx: Context, uri: Uri): Loaded? {
        return try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            var json: String? = null
            val photos = HashMap<String, ByteArray>()
            var total = 0L
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val buf = ByteArrayOutputStream()
                    z.copyTo(buf)
                    total += buf.size()
                    if (total > 300_000_000L) return null
                    if (e.name == "hauskontor.json") json = buf.toString("UTF-8")
                    else if (e.name.startsWith("belege/") && !e.isDirectory) photos[e.name.substringAfterLast('/')] = buf.toByteArray()
                    e = z.nextEntry
                }
            }
            json?.let { Loaded(Data.fromJson(JSONObject(it)), photos) }
        } else {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            if (!o.has("entries") && !o.has("meters") && !o.has("recurrings")) null else Loaded(Data.fromJson(o), emptyMap())
        }
        } catch (e: Exception) {
            null
        }
    }

    /** Fügt Daten aus der Sicherung hinzu, ohne vorhandene zu überschreiben (gleiche Kennung = schon da). */
    fun merge(a: Data, b: Data): Data {
        fun <T> add(x: List<T>, y: List<T>, id: (T) -> String): List<T> {
            val have = x.map(id).toSet()
            return x + y.filter { id(it) !in have }
        }
        val persons = add(a.persons, b.persons) { it.id }.take(Person.MAX)
        return Data(
            add(a.entries, b.entries) { it.id },
            add(a.recurrings, b.recurrings) { it.id },
            add(a.meters, b.meters) { it.id },
            add(a.readings, b.readings) { it.id },
            persons,
            if (a.persons.isNotEmpty()) a.split else b.split,
            a.household.ifBlank { b.household },
            a.address.ifBlank { b.address },
            add(a.rules, b.rules) { it.key + it.income }
        )
    }
}
