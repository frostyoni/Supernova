package com.supernova.launcher.ui

import kotlin.math.abs

// Which role a page is playing right now: arriving toward the viewport, or
// departing from it.
//
// Neither the page's position sign nor the pager's direction says this. A
// page on the right is arriving on a forward swipe and departing on a
// backward one, and the pager reports a backward drag as a *growing*
// positionOffset. The page's own distance from the viewport does say it:
// arriving pages get closer, departing ones further.
//
// It needs hysteresis. Held still, the pager keeps calling with the same
// position, and a finger resting on the glass still jitters by a pixel.
// Treating "no closer" as departing flipped the lanes on a pause, and the
// jitter flipped them back - a glitch at the halfway point with nothing
// moving. Below MOVE_EPSILON the role stays as it was.
object PageRoles {

    // About three pixels on a 1080-wide page: well under a deliberate move,
    // well over a resting finger's jitter.
    const val MOVE_EPSILON = 0.003f

    class State(previousDistance: Float?, distance: Float) {

        var distance: Float = distance
            private set

        var arriving: Boolean = previousDistance?.let { distance < it } ?: (distance > 0.5f)
            private set

        fun moveTo(distance: Float): Boolean {
            val delta = distance - this.distance
            if (abs(delta) > MOVE_EPSILON) arriving = delta < 0f
            this.distance = distance
            return arriving
        }

        // The page has gone a whole page away: remember where, leave the role
        // alone. It comes back as arriving from wherever it was parked.
        fun park(distance: Float) {
            this.distance = distance
        }
    }
}
