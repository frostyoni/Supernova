package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ChipEditTest {

    private fun chip(at: Float = 0.2f, to: Float = 0.6f) = CustomTransition.Chip(
        at = at,
        to = to,
        channel = CustomTransition.Channel.ALPHA,
        from = 1f,
        value = 0f
    )

    @Test
    fun movingKeepsTheLengthAndStaysInside() {
        val moved = ChipEdit.move(chip(), 0.5f)
        assertEquals(0.5f, moved.at, 0.0001f)
        assertEquals(0.9f, moved.to, 0.0001f)
        val clamped = ChipEdit.move(chip(), 0.95f)
        assertEquals(0.6f, clamped.at, 0.0001f)
        assertEquals(1f, clamped.to, 0.0001f)
    }

    @Test
    fun anEdgeCannotCrossTheOther() {
        val start = ChipEdit.resizeStart(chip(), 0.9f)
        assertEquals(0.58f, start.at, 0.0001f)
        assertEquals(0.6f, start.to, 0.0001f)
        val end = ChipEdit.resizeEnd(chip(), 0.05f)
        assertEquals(0.2f, end.at, 0.0001f)
        assertEquals(0.22f, end.to, 0.0001f)
    }

    @Test
    fun timesSnapToHundredths() {
        assertEquals(0.37f, ChipEdit.snap(0.3712f), 0.0001f)
        assertEquals(1.4f, ChipEdit.snap(1.4f), 0.0001f)
    }
}
