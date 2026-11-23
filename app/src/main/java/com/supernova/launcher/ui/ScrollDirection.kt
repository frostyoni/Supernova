package com.supernova.launcher.ui

import androidx.viewpager2.widget.ViewPager2

// Which way a pager is moving, for effects that need to know.
//
// A page's position alone cannot say: the page on the right is the one
// arriving on a forward swipe and the one departing on a backward swipe, and
// a custom transition's lanes are roles, not sides. This watches the pager's
// scroll and keeps the last direction, so the transformer can ask.
class ScrollDirection(pager: ViewPager2) {

    // 1 = toward the next page, -1 = toward the previous one.
    var value: Int = 1
        private set

    private var lastPosition = pager.currentItem
    private var lastOffset = 0f

    init {
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Toward the next page the offset grows on the same position;
                // toward the previous one the position drops and the offset
                // falls from the far end.
                val delta = when {
                    position > lastPosition -> 1f
                    position < lastPosition -> -1f
                    else -> positionOffset - lastOffset
                }
                if (delta > 0.0005f) value = 1
                else if (delta < -0.0005f) value = -1
                lastPosition = position
                lastOffset = positionOffset
            }
        })
    }
}
