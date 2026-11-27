package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What a picked file has to be before it can become the label font: an
// sfnt font's own header and the size cap, both checked without a device.
class FontFileFormatTest {

    private fun header(tag: String) = tag.toByteArray(Charsets.US_ASCII)

    @Test
    fun `a trueType or openType header is a font`() {
        assertTrue(FontFileFormat.looksLikeFont(byteArrayOf(0, 1, 0, 0) + ByteArray(4)))
        assertTrue(FontFileFormat.looksLikeFont(header("true") + ByteArray(4)))
        assertTrue(FontFileFormat.looksLikeFont(header("OTTO") + ByteArray(4)))
        assertTrue(FontFileFormat.looksLikeFont(header("ttcf") + ByteArray(4)))
    }

    @Test
    fun `anything else is not`() {
        assertFalse(FontFileFormat.looksLikeFont(ByteArray(0)))
        assertFalse(FontFileFormat.looksLikeFont(header("tru")))
        assertFalse(FontFileFormat.looksLikeFont(header("PK\u0003\u0004") + ByteArray(4)))
        assertFalse(FontFileFormat.looksLikeFont(header("GIF8") + ByteArray(4)))
        assertFalse(FontFileFormat.looksLikeFont(header("wOFF") + ByteArray(4)))
    }

    @Test
    fun `the cap is four megabytes`() {
        assertEquals(4 * 1024 * 1024, FontFileFormat.MAX_BYTES)
        assertTrue(FontFileFormat.withinCap(1))
        assertTrue(FontFileFormat.withinCap(FontFileFormat.MAX_BYTES.toLong()))
        assertFalse(FontFileFormat.withinCap(FontFileFormat.MAX_BYTES.toLong() + 1))
        // Nothing at all isn't a font.
        assertFalse(FontFileFormat.withinCap(0))
    }
}
