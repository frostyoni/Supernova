package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitionMathTest {

    private val fadeOut = CustomTransition(
        outgoing = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.ALPHA, 1f, 0f)
        )
    )

    // The bug this exists for: a page leaving on a backward swipe used to read
    // the incoming lane, keep its alpha, and be cut off at the neighbour slot.
    @Test
    fun aDepartingPageIsGoneByTheNeighbourSlotEitherWay() {
        listOf(1f, -1f).forEach { position ->
            val applied = TransitionMath.apply(fadeOut, position * 0.999f, arriving = false)
            assertTrue(applied.alpha < 0.01f, "alpha at $position was ${applied.alpha}")
        }
    }

    @Test
    fun anArrivingPageKeepsItsOwnAlpha() {
        val spec = CustomTransition(
            incoming = listOf(CustomTransition.Chip(0f, 1f, CustomTransition.Channel.ALPHA, 1f, 1f))
        )
        listOf(0.9f, -0.9f).forEach { position ->
            assertEquals(1f, TransitionMath.apply(spec, position, arriving = true).alpha, 0.0001f)
        }
    }

    @Test
    fun bothLanesAreIdentityAtRest() {
        TransitionPresets.all.forEach { preset ->
            listOf(true, false).forEach { arriving ->
                val applied = TransitionMath.apply(preset, 0f, arriving)
                assertEquals(0f, applied.moveX, 0.0001f, preset.name)
                assertEquals(0f, applied.moveY, 0.0001f, preset.name)
                assertEquals(1f, applied.scale, 0.0001f, preset.name)
                assertEquals(1f, applied.alpha, 0.0001f, preset.name)
            }
        }
    }

    // X is mirrored by the page's side, so one spec reads the same whichever
    // way the swipe goes: a crossfade holds still in both directions.
    @Test
    fun xIsRelativeToThePagesOwnSide() {
        val crossfade = TransitionPresets.crossfade
        val right = TransitionMath.apply(crossfade, 1f, arriving = true)
        val left = TransitionMath.apply(crossfade, -1f, arriving = true)
        assertEquals(-1f, right.moveX, 0.0001f)
        assertEquals(1f, left.moveX, 0.0001f)
        // Both cancel the pager's own slide, so the page stays centred.
        assertEquals(0f, 1f + right.moveX, 0.0001f)
        assertEquals(0f, -1f + left.moveX, 0.0001f)
    }

    @Test
    fun yIsNotMirrored() {
        val spec = CustomTransition(
            incoming = listOf(CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_Y, 0f, 0.1f))
        )
        assertEquals(0.1f, TransitionMath.apply(spec, 1f, arriving = true).moveY, 0.0001f)
        assertEquals(0.1f, TransitionMath.apply(spec, -1f, arriving = true).moveY, 0.0001f)
    }
}
