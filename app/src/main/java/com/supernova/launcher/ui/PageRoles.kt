package com.supernova.launcher.ui

// Which role a page is playing right now: arriving toward the viewport, or
// departing from it.
//
// Neither the page's position sign nor the pager's direction says this. A
// page on the right is arriving on a forward swipe and departing on a
// backward one, and the pager reports a backward drag as a *growing*
// positionOffset - so a direction flag looked forward for most of a
// backward drag and flipped at the settle, swapping the lanes mid-swipe.
//
// The page's own distance from the viewport does say it: arriving pages get
// closer, departing ones further. A page seen for the first time is far
// away and coming in.
object PageRoles {
    fun arriving(previousDistance: Float?, distance: Float): Boolean =
        previousDistance?.let { distance < it } ?: (distance > 0.5f)
}
