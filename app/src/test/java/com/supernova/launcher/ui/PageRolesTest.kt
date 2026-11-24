package com.supernova.launcher.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageRolesTest {

    @Test
    fun aPageGettingCloserIsArriving() {
        val state = PageRoles.State(previousDistance = 0.8f, distance = 0.4f)
        assertTrue(state.moveTo(0.2f))
    }

    @Test
    fun aPageGettingFurtherIsDeparting() {
        val state = PageRoles.State(previousDistance = 0.4f, distance = 0.8f)
        assertFalse(state.moveTo(0.9f))
    }

    // The bug this exists for: held still, the pager repeats the distance, and
    // "not closer" used to mean departing - flipping the lanes with the finger
    // down.
    @Test
    fun aHeldPageKeepsItsRole() {
        val arriving = PageRoles.State(previousDistance = 0.8f, distance = 0.4f)
        assertTrue(arriving.moveTo(0.4f))
        assertTrue(arriving.moveTo(0.4f))

        val departing = PageRoles.State(previousDistance = 0.4f, distance = 0.8f)
        assertFalse(departing.moveTo(0.8f))
        assertFalse(departing.moveTo(0.8f))
    }

    @Test
    fun aFingersJitterIsNotADirectionChange() {
        val state = PageRoles.State(previousDistance = 0.8f, distance = 0.4f)
        assertTrue(state.moveTo(0.401f))
        assertTrue(state.moveTo(0.399f))
        assertTrue(state.moveTo(0.4f))
    }

    @Test
    fun aDeliberateReversalFlipsTheRole() {
        val state = PageRoles.State(previousDistance = 0.8f, distance = 0.4f)
        assertTrue(state.moveTo(0.3f))
        // Let go of the halfway point and swipe back: more than the epsilon.
        assertFalse(state.moveTo(0.5f))
    }

    @Test
    fun aPageSeenFirstFarAwayIsArriving() {
        assertTrue(PageRoles.State(previousDistance = null, distance = 0.99f).arriving)
        assertFalse(PageRoles.State(previousDistance = null, distance = 0.1f).arriving)
    }
}
