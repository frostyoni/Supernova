package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomTransitionTest {

    @Test
    fun exampleRoundTrips() {
        val spec = CustomTransition.parse(CustomTransition.encode(CustomTransition.example))
            ?: error("the example did not parse")
        assertEquals("Nod", spec.name)
        assertEquals(CustomTransition.example.incoming.size, spec.incoming.size)
        assertEquals(CustomTransition.example.outgoing.size, spec.outgoing.size)
    }

    @Test
    fun movesAreFractionsAndReachTheirValues() {
        val spec = CustomTransition(
            incoming = listOf(
                CustomTransition.Chip(
                    0f, 1f, CustomTransition.Channel.MOVE_X, 1f, 0f, CustomTransition.Ease.LINEAR
                )
            )
        )
        assertEquals(1f, spec.sample(incomingLane = true, progress = 0f).moveX)
        assertEquals(0.5f, spec.sample(incomingLane = true, progress = 0.5f).moveX)
        assertEquals(0f, spec.sample(incomingLane = true, progress = 1f).moveX)
    }

    @Test
    fun aChipDoesNothingBeforeItStartsAndHoldsItsEnd() {
        val spec = CustomTransition(
            outgoing = listOf(
                CustomTransition.Chip(
                    0.4f, 0.8f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.LINEAR
                )
            )
        )
        assertEquals(1f, spec.sample(incomingLane = false, progress = 0.2f).alpha)
        assertEquals(0f, spec.sample(incomingLane = false, progress = 1f).alpha)
    }

    @Test
    fun easingShapesTheMiddle() {
        val spec = CustomTransition(
            incoming = listOf(
                CustomTransition.Chip(
                    0f, 1f, CustomTransition.Channel.ROLL, 0f, 40f, CustomTransition.Ease.EASE_OUT
                )
            )
        )
        // Ease out reaches three quarters at the half, not one half.
        assertEquals(30f, spec.sample(incomingLane = true, progress = 0.5f).roll)
    }

    @Test
    fun additiveChipsStack() {
        val spec = CustomTransition(
            incoming = listOf(
                CustomTransition.Chip(
                    0f, 1f, CustomTransition.Channel.MOVE_X, 0.2f, 0.2f, add = true
                ),
                CustomTransition.Chip(
                    0f, 1f, CustomTransition.Channel.MOVE_X, 0.3f, 0.3f, add = true
                )
            )
        )
        assertEquals(0.5f, spec.sample(incomingLane = true, progress = 0.7f).moveX, 0.0001f)
    }

    @Test
    fun theExampleSettlesAtRest() {
        // The incoming page must end upright and in place; the outgoing one
        // gone, so the wrapper's reset at the neighbour slot is invisible.
        val arriving = CustomTransition.example.sample(incomingLane = true, progress = 1f)
        assertEquals(0f, arriving.moveX, 0.0001f)
        assertEquals(0f, arriving.roll, 0.0001f)
        assertEquals(1f, arriving.scale, 0.0001f)
        assertEquals(0f, CustomTransition.example.sample(incomingLane = false, progress = 1f).alpha, 0.0001f)
    }

    @Test
    fun invalidSpecsAreRejected() {
        assertNull(CustomTransition.parse("{"))
        assertNull(
            CustomTransition.parse(
                """{"name":"Bad","incoming":[{"at":0.5,"to":0.5,"channel":"ALPHA","from":1,"value":0}]}"""
            )
        )
        assertNull(
            CustomTransition.parse(
                """{"name":"Bad","incoming":[{"at":0.2,"to":1.5,"channel":"ALPHA","from":1,"value":0}]}"""
            )
        )
    }
}
