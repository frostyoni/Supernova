package com.supernova.launcher.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

// Press feedback for a tappable icon: it settles a little under the finger and
// springs back on release. A state list animator rather than a touch listener,
// so it cannot race the drag controller, and nothing has to be undone by hand.
object ItemPress {

    private const val PRESSED = 0.96f
    private const val DOWN_MS = 90L
    private const val UP_MS = 160L

    // Call only while animations are on: the app's own speed setting is
    // honoured at the call site, not here.
    fun attach(view: View) {
        val down = settle(view, PRESSED, DOWN_MS, DecelerateInterpolator())
        val up = settle(view, 1f, UP_MS, OvershootInterpolator(1.1f))
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), down)
            addState(intArrayOf(), up)
        }
    }

    private fun settle(view: View, to: Float, durationMs: Long, easing: android.animation.TimeInterpolator): ObjectAnimator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, view.scaleX, to),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, view.scaleY, to)
        ).apply {
            duration = durationMs
            interpolator = easing
        }
}
