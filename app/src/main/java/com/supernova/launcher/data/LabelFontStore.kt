package com.supernova.launcher.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

// The file a label font of CUSTOM draws with. A picked document is copied
// into files/fonts and built into a Typeface once, kept per file state: a
// file that no longer loads (deleted, truncated, unreadable) reads as the
// default family, so a bind never crashes on it.
object LabelFontStore {

    // What storing a file came to, for the caller's message.
    enum class Result { STORED, TOO_BIG, NOT_A_FONT, FAILED }

    private const val DIR = "fonts"
    private const val FILE = "custom"
    private const val NAME_FILE = "custom.name"
    private const val NAME_MAX = 120

    // A read chunk, for the capped copy below.
    private const val CHUNK = 64 * 1024

    // The face in use and the file state it was built from; a replaced or
    // removed file is noticed without a restart.
    @Volatile
    private var cached: Typeface? = null

    @Volatile
    private var cachedStamp: String? = null

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    private fun file(context: Context): File = File(dir(context), FILE)

    private fun nameFile(context: Context): File = File(dir(context), NAME_FILE)

    private fun stamp(file: File): String = "${file.length()}:${file.lastModified()}"

    fun hasFont(context: Context): Boolean = file(context).isFile

    // The picked file's own name, for the settings row; null while none is
    // known (a font restored from a backup carries bytes only).
    fun fileName(context: Context): String? = runCatching {
        nameFile(context).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // The stored font, or null while none is stored or it no longer loads.
    fun typeface(context: Context): Typeface? {
        val stored = file(context)
        if (!stored.isFile) {
            cached = null
            cachedStamp = null
            return null
        }
        val now = stamp(stored)
        cached?.let { if (now == cachedStamp) return it }
        val face = load(stored) ?: return null
        cached = face
        cachedStamp = now
        return face
    }

    // Copies the picked document in as the custom font. The one in use stays
    // when the new file is too big or doesn't load as a font.
    fun store(context: Context, uri: Uri, displayName: String?): Result {
        val bytes = runCatching { read(context, uri) }.getOrNull() ?: return Result.FAILED
        return storeBytes(context, bytes, displayName)
    }

    // Drops the file, its name and the face built from it.
    fun clear(context: Context) {
        file(context).delete()
        nameFile(context).delete()
        cached = null
        cachedStamp = null
    }

    // The stored file for a backup; null while none is stored or it can't be
    // read.
    fun exportBase64(context: Context): String? {
        val stored = file(context)
        if (!stored.isFile) return null
        return runCatching { Base64.encodeToString(stored.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    // Puts a backup's font back. Blank removes the file; bytes that aren't a
    // loadable font are left out rather than stored.
    fun importBase64(context: Context, base64: String): Result {
        if (base64.isBlank()) {
            clear(context)
            return Result.STORED
        }
        val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull() ?: return Result.FAILED
        // The name isn't in the backup: the row falls back to a plain one.
        return storeBytes(context, bytes, null)
    }

    // The document's own name, as the picker reports it.
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.let(::shortName)

    private fun storeBytes(context: Context, bytes: ByteArray, displayName: String?): Result {
        if (!FontFileFormat.looksLikeFont(bytes)) return Result.NOT_A_FONT
        if (!FontFileFormat.withinCap(bytes.size.toLong())) return Result.TOO_BIG
        return if (write(context, bytes, shortName(displayName))) Result.STORED else Result.NOT_A_FONT
    }

    // Stops one chunk past the cap; withinCap then rejects it.
    private fun read(context: Context, uri: Uri): ByteArray? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK)
            while (out.size() <= FontFileFormat.MAX_BYTES) {
                val count = input.read(chunk)
                if (count < 0) break
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        }
    }

    // Written beside the live file and swapped in only once it builds a
    // Typeface, so a bad file leaves the font that worked.
    private fun write(context: Context, bytes: ByteArray, displayName: String?): Boolean = runCatching {
        val folder = dir(context)
        folder.mkdirs()
        val target = File(folder, FILE)
        val temp = File(folder, "$FILE.tmp")
        temp.writeBytes(bytes)
        val face = load(temp)
        if (face == null) {
            temp.delete()
            return@runCatching false
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@runCatching false
        }
        cached = face
        cachedStamp = stamp(target)
        if (displayName != null) nameFile(context).writeText(displayName) else nameFile(context).delete()
        true
    }.getOrDefault(false)

    private fun load(file: File): Typeface? = runCatching { Typeface.Builder(file).build() }.getOrNull()

    // A name for the row: one line, its own file name, held to a length.
    private fun shortName(raw: String?): String? = raw
        ?.substringAfterLast('/')
        ?.replace('\n', ' ')
        ?.trim()
        ?.take(NAME_MAX)
        ?.takeIf { it.isNotEmpty() }
}

// The rule a picked file has to pass before it can become the label font,
// kept free of Android so the rule itself is testable.
internal object FontFileFormat {

    // A ceiling on what a picked file may be, not on what a font is: a Latin
    // text face runs well under a megabyte, a full CJK one to a few.
    const val MAX_BYTES = 4 * 1024 * 1024

    // sfnt's version tags: TrueType outlines (0x00010000, or the 'true' tag
    // Apple wrote), OpenType's CFF ones ('OTTO'), and a collection of faces
    // ('ttcf'), of which Typeface builds the first.
    private val TAGS = setOf("true", "OTTO", "ttcf")

    fun looksLikeFont(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val version = bytes[0] == 0.toByte() && bytes[1] == 1.toByte() &&
            bytes[2] == 0.toByte() && bytes[3] == 0.toByte()
        return version || String(bytes, 0, 4, Charsets.US_ASCII) in TAGS
    }

    fun withinCap(size: Long): Boolean = size in 1..MAX_BYTES.toLong()
}
