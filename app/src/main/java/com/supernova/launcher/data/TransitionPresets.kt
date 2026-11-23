package com.supernova.launcher.data

// Transitions to start from, each one a spec a person can read and edit.
//
// The convention every preset follows, and the one the builder's hint states:
// a lane is an EXTRA on top of the pager's own slide, measured by distance
// from rest (0 = the page in view, 1 = a whole page away). So a lane is
// identity at 0, and whatever it does when far away is at 1. A chip that
// starts at 0 with a non-identity value snaps the page as it arrives.
object TransitionPresets {

    val nod: CustomTransition = CustomTransition.example

    // Both pages hold still and dissolve into one another: the move cancels
    // the pager's slide, so only the fade is left.
    val crossfade = CustomTransition(
        name = "Crossfade",
        incoming = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_X, 0f, -1f),
            CustomTransition.Chip(0f, 0.6f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_X, 0f, 1f),
            CustomTransition.Chip(0f, 0.7f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN)
        )
    )

    // Two planes at different rates: the arriving page leads a little, the
    // leaving one trails and shrinks away.
    val drift = CustomTransition(
        name = "Drift",
        incoming = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_X, 0f, -0.15f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.SCALE, 1f, 0.98f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_X, 0f, 0.3f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.9f, CustomTransition.Channel.SCALE, 1f, 0.97f, CustomTransition.Ease.EASE_IN),
            CustomTransition.Chip(0.2f, 1f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN)
        )
    )

    // Arrives with a spring in its scale, from slightly small and transparent.
    val pop = CustomTransition(
        name = "Pop",
        incoming = listOf(
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.SCALE, 1f, 0.92f, CustomTransition.Ease.OVERSHOOT),
            CustomTransition.Chip(0f, 0.45f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 0.7f, CustomTransition.Channel.SCALE, 1f, 0.96f, CustomTransition.Ease.EASE_IN),
            CustomTransition.Chip(0f, 0.6f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN)
        )
    )

    // Rises into place from below, as a sheet would.
    val lift = CustomTransition(
        name = "Lift",
        incoming = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_Y, 0f, 0.08f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.5f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.SCALE, 1f, 0.98f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.MOVE_Y, 0f, -0.03f, CustomTransition.Ease.EASE_IN),
            CustomTransition.Chip(0f, 0.7f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN)
        )
    )

    // A small turn that unwinds as the page lands.
    val spin = CustomTransition(
        name = "Spin",
        incoming = listOf(
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.ROLL, 0f, -8f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.4f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.ROLL, 0f, 4f, CustomTransition.Ease.EASE_IN),
            CustomTransition.Chip(0f, 0.7f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN)
        )
    )

    // Drops in from above like a curtain, the old page dimming behind it.
    val curtain = CustomTransition(
        name = "Curtain",
        incoming = listOf(
            CustomTransition.Chip(0f, 1f, CustomTransition.Channel.MOVE_Y, 0f, -0.15f, CustomTransition.Ease.EASE_OUT),
            CustomTransition.Chip(0f, 0.45f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_OUT)
        ),
        outgoing = listOf(
            CustomTransition.Chip(0f, 0.6f, CustomTransition.Channel.ALPHA, 1f, 0f, CustomTransition.Ease.EASE_IN),
            CustomTransition.Chip(0f, 0.8f, CustomTransition.Channel.SCALE, 1f, 0.98f, CustomTransition.Ease.EASE_IN)
        )
    )

    val all: List<CustomTransition> = listOf(nod, crossfade, drift, pop, lift, spin, curtain)

    // The preset a spec still is, if it has not been edited away from one.
    fun matching(spec: CustomTransition): CustomTransition? = all.firstOrNull { it == spec }
}
