package de.autoknigge.hauskontor

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Abgleich zwischen Mitbewohnern: verschlüsselte Datei, die nur mit dem gemeinsamen WG-Code lesbar ist. Kein Server. */
object Wg {
    private val MAGIC = "HKWG1".toByteArray()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun code(ctx: Context): String = ctx.getSharedPreferences("wg", Context.MODE_PRIVATE).getString("code", "") ?: ""
    fun setCode(ctx: Context, c: String) { ctx.getSharedPreferences("wg", Context.MODE_PRIVATE).edit().putString("code", normalize(c)).apply() }
    fun normalize(c: String): String = c.uppercase().filter { it in ALPHABET }.chunked(4).joinToString("-")
    fun valid(c: String): Boolean = c.replace("-", "").length >= 12

    fun newCode(): String {
        val r = SecureRandom()
        return (1..12).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("").chunked(4).joinToString("-")
    }

    private fun key(code: String, salt: ByteArray): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(code.toCharArray(), salt, 120_000, 256))
        return SecretKeySpec(k.encoded, "AES")
    }

    fun write(ctx: Context, d: Data, code: String): File {
        val plain = d.copy(readings = d.readings.map { it.copy(photo = null) }).toJson().toString().toByteArray(Charsets.UTF_8)
        val r = SecureRandom()
        val salt = ByteArray(16).also { r.nextBytes(it) }
        val iv = ByteArray(12).also { r.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        val enc = c.doFinal(plain)
        val f = File(File(ctx.cacheDir, "teilen").also { it.mkdirs() }, "Hauskontor-WG-Abgleich.hkwg")
        f.writeBytes(MAGIC + salt + iv + enc)
        return f
    }

    /** null = keine WG-Datei, Fehler = falscher Code oder beschädigt (erkennbar am Ergebnis [Result]). */
    sealed class Result {
        class Ok(val data: Data) : Result()
        object WrongCode : Result()
        object NotWg : Result()
    }

    fun read(ctx: Context, uri: Uri, code: String): Result {
        val b = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null } ?: return Result.NotWg
        if (b.size < MAGIC.size + 28 + 16 || !b.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return Result.NotWg
        return try {
            val salt = b.copyOfRange(5, 21)
            val iv = b.copyOfRange(21, 33)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
            val plain = c.doFinal(b, 33, b.size - 33)
            Result.Ok(Data.fromJson(JSONObject(String(plain, Charsets.UTF_8))))
        } catch (e: Exception) {
            Result.WrongCode
        }
    }
}
