package com.supernova.launcher.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageRolesTest {

    @Test
    fun aPageGettingCloserIsArriving() {
        assertTrue(PageRoles.arriving(previousDistance = 0.8f, distance = 0.4f))
        assertTrue(PageRoles.arriving(previousDistance = 0.02f, distance = 0.01f))
    }

    @Test
    fun aPageGettingFurtherIsDeparting() {
        assertFalse(PageRoles.arriving(previousDistance = 0.4f, distance = 0.8f))
        assertFalse(PageRoles.arriving(previousDistance = 0.01f, distance = 0.02f))
    }

    @Test
    fun theSameDistanceDoesNotFlip() {
        assertFalse(PageRoles.arriving(previousDistance = 0.5f, distance = 0.5f))
    }

    @Test
    fun aPageSeenFirstFarAwayIsArriving() {
        assertTrue(PageRoles.arriving(previousDistance = null, distance = 0.99f))
        assertFalse(PageRoles.arriving(previousDistance = null, distance = 0.1f))
    }
}
