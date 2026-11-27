package com.supernova.launcher.data

import kotlin.math.abs

// What a spec says a page should look like at one point in a swipe, with the
// two rules the pager itself cannot express:
//
//  * A lane belongs to a ROLE, not a side. The arriving page reads the
//    incoming lane and the departing one the outgoing lane, whichever way the
//    finger is going; picking by the sign of the position gave the departing
//    page the incoming lane on a backward swipe, so it never faded and was cut
//    off abruptly a page away.
//  * The departing page must be gone by the neighbour slot, where the pager
//    resets it, or the reset is visible as a pop.
//
// X is relative to the page's own side: negative pulls it toward the centre
// (which is how a crossfade cancels the slide), positive pushes it further
// out. Y is down-positive and is not mirrored.
object TransitionMath {

    class Applied(
        val moveX: Float,
        val moveY: Float,
        val scale: Float,
        val alpha: Float,
        val roll: Float,
        val pitch: Float,
        val yaw: Float
    )

    fun apply(spec: CustomTransition, position: Float, arriving: Boolean): Applied {
        val distance = abs(position).coerceIn(0f, 1f)
        val frame = spec.sample(incomingLane = arriving, progress = distance)
        val side = if (position < 0f) -1f else 1f
        val alpha = if (arriving) frame.alpha else frame.alpha * (1f - distance)
        return Applied(
            moveX = frame.moveX * side,
            moveY = frame.moveY,
            scale = frame.scale,
            alpha = alpha.coerceIn(0f, 1f),
            roll = frame.roll,
            pitch = frame.pitch,
            yaw = frame.yaw
        )
    }
}
