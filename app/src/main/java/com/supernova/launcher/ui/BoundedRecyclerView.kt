package com.supernova.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// RecyclerView with a runtime height cap: content-sized until the cap, then it
// scrolls. RecyclerView has no maxHeight of its own, and sizing a grid from an
// estimated row height left an empty band under short grids. The folder popup
// and the floating search window both size a card this way.
class BoundedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    // -1 = no cap. Zero is a real cap: a card with no room gets no list.
    var maxHeight: Int = -1
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var spec = heightSpec
        val mode = View.MeasureSpec.getMode(heightSpec)
        // An exact height (the horizontal paging viewport) is never capped.
        if (maxHeight >= 0 && mode != View.MeasureSpec.EXACTLY) {
            val size = View.MeasureSpec.getSize(heightSpec)
            val capped = if (mode == View.MeasureSpec.UNSPECIFIED) maxHeight else minOf(size, maxHeight)
            spec = View.MeasureSpec.makeMeasureSpec(capped, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthSpec, spec)
    }
}
