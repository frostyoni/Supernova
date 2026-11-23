package com.supernova.launcher.data

import com.supernova.launcher.ui.LabelFont
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The custom label font's part in a backup, decided without a device: the
// bytes ride in the payload, and a backup that predates the field reads as
// none, which restore treats as "leave the installed font alone".
class LabelFontStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun decode(raw: String): BackupPayload = json.decodeFromString<BackupPayload>(raw)

    @Test
    fun `a custom font rides in the backup and comes back whole`() {
        val payload = BackupPayload(
            exportedAtEpochMs = 1,
            model = LauncherModel(),
            prefs = LauncherPrefs(labelFont = LabelFont.CUSTOM),
            customFontBase64 = "AAABAAEAEBAAAAEAIABoBAAAFgAAACgAA"
        )
        val decoded = decode(json.encodeToString(BackupPayload.serializer(), payload))
        assertEquals(payload, decoded)
        assertEquals("AAABAAEAEBAAAAEAIABoBAAAFgAAACgAA", decoded.customFontBase64)
        assertEquals(LabelFont.CUSTOM, decoded.prefs.labelFont)
    }

    @Test
    fun `a backup from before the font decodes with none`() {
        val raw = """
            {"version":1,"exportedAtEpochMs":1,
             "model":{"version":5,"items":[],"pageCount":1,"defaultPage":0},
             "prefs":{"labelFont":"SERIF"}}
        """.trimIndent()
        val decoded = decode(raw)
        assertNull(decoded.customFontBase64)
        assertEquals(LabelFont.SERIF, decoded.prefs.labelFont)
    }

    // An export of an install with no font writes the field explicitly null,
    // which restore reads the same way as a missing one.
    @Test
    fun `an export with no font stores a null`() {
        val encoded = json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(exportedAtEpochMs = 1, model = LauncherModel(), prefs = LauncherPrefs())
        )
        assertNull(decode(encoded).customFontBase64)
    }
}
