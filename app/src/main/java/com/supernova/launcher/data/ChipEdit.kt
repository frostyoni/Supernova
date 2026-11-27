package com.supernova.launcher.data

import kotlin.math.roundToInt

// The timeline's edits, kept out of the view so they can be tested: a chip
// moved by its body keeps its length, and dragging an edge changes one end
// without crossing the other or leaving 0..1.
object ChipEdit {

    const val MIN_DURATION = 0.02f

    fun snap(time: Float): Float = (time * 100f).roundToInt() / 100f

    fun move(chip: CustomTransition.Chip, toStart: Float): CustomTransition.Chip {
        val duration = chip.to - chip.at
        val start = snap(toStart).coerceIn(0f, (1f - duration).coerceAtLeast(0f))
        return chip.copy(at = start, to = start + duration)
    }

    fun resizeStart(chip: CustomTransition.Chip, toStart: Float): CustomTransition.Chip {
        val start = snap(toStart).coerceIn(0f, (chip.to - MIN_DURATION).coerceAtLeast(0f))
        return chip.copy(at = start)
    }

    fun resizeEnd(chip: CustomTransition.Chip, toEnd: Float): CustomTransition.Chip {
        val end = snap(toEnd).coerceIn((chip.at + MIN_DURATION).coerceAtMost(1f), 1f)
        return chip.copy(to = end)
    }
}
