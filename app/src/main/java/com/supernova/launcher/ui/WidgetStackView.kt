package com.supernova.launcher.ui

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.supernova.launcher.data.LauncherItem
import kotlin.math.abs

// A workspace widget stack: one cell that hosts two app widgets, with a
// horizontal swipe switching the visible one. The primary widget (item's
// appWidgetId) is first; widgetStack members follow in order. Only one
// child is visible at a time, but every child is measured and laid out at
// the container's size so the provider sees the same bounds.
class WidgetStackView(
    context: Context,
    private val controller: WidgetController,
    private var item: LauncherItem,
    private val onLongPress: (View) -> Unit
) : FrameLayout(context) {

    private data class StackChild(
        val widgetId: Int,
        val hostView: AppWidgetHostView
    )

    private val children = mutableListOf<StackChild>()
    private var frontIndex: Int = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || children.size <= 1) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy) && abs(velocityX) > MIN_FLING_VELOCITY) {
                if (dx < 0) showNext() else showPrevious()
                return true
            }
            return false
        }
    })

    init {
        isClickable = true
        isLongClickable = true
        updateMembers(item)
    }

    fun updateMembers(newItem: LauncherItem) {
        item = newItem
        frontIndex = item.widgetStackFront.coerceIn(0, totalCount() - 1)

        val desiredIds = mutableListOf<Int>()
        item.appWidgetId?.let { desiredIds += it }
        item.widgetStack.mapNotNullTo(desiredIds) { it.appWidgetId }

        // Remove ids that are no longer part of this stack.
        val iter = children.iterator()
        while (iter.hasNext()) {
            val child = iter.next()
            if (child.widgetId !in desiredIds) {
                removeView(child.hostView)
                iter.remove()
            }
        }

        // Rebuild in desired order, reusing existing host views.
        val reordered = mutableListOf<StackChild>()
        desiredIds.forEachIndexed { index, widgetId ->
            val existing = children.find { it.widgetId == widgetId }
            val hostView = if (existing != null) {
                existing.hostView
            } else {
                controller.stackMemberHostView(widgetId)?.also { hostView ->
                    hostView.setOnLongClickListener { v ->
                        onLongPress(v)
                        true
                    }
                    // A cached host view can still hang off the container it
                    // was built in before a recreate or a rebind.
                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                    addView(hostView, index, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            }
            if (hostView != null) {
                reordered += StackChild(widgetId, hostView)
            }
        }
        children.clear()
        children.addAll(reordered)

        applyVisibility()
    }

    fun reportMemberSizes() {
        if (width <= 0 || height <= 0) return
        children.forEach { child ->
            controller.reportStackMemberSize(child.widgetId, child.hostView, width, height)
        }
    }

    private fun totalCount(): Int = 1 + item.widgetStack.size

    private fun applyVisibility() {
        children.forEachIndexed { index, child ->
            child.hostView.visibility = if (index == frontIndex) View.VISIBLE else View.INVISIBLE
        }
    }

    fun showNext() {
        if (children.size <= 1) return
        switchTo((frontIndex + 1) % children.size)
    }

    fun showPrevious() {
        if (children.size <= 1) return
        switchTo((frontIndex - 1 + children.size) % children.size)
    }

    private fun switchTo(newFront: Int) {
        if (newFront == frontIndex || newFront !in children.indices) return
        val oldChild = children.getOrNull(frontIndex)?.hostView
        val newChild = children.getOrNull(newFront)?.hostView
        frontIndex = newFront

        oldChild?.animate()
            ?.alpha(0f)
            ?.setDuration(SWITCH_ANIMATION_MS)
            ?.withEndAction { oldChild.visibility = View.INVISIBLE }
            ?.start()

        newChild?.alpha = 0f
        newChild?.visibility = View.VISIBLE
        newChild?.animate()
            ?.alpha(1f)
            ?.setDuration(SWITCH_ANIMATION_MS)
            ?.start()

        controller.onStackFrontChanged(item.id, frontIndex)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gesture.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = false
            }
            MotionEvent.ACTION_MOVE -> if (!tracking && children.size > 1) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                // Claim clearly-horizontal drags. Steeper movement is left to
                // the widget's own scrolling or the page's vertical swipe
                // arbitration.
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * HORIZONTAL_DOMINANCE) {
                    tracking = true
                    // Stop the workspace pager from taking this stream.
                    (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return tracking
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = gesture.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = true
            }
            MotionEvent.ACTION_UP -> {
                if (tracking && !handled && children.size > 1) {
                    val dx = ev.x - downX
                    if (abs(dx) > width / 4) {
                        if (dx < 0) showNext() else showPrevious()
                    }
                }
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return handled || tracking || super.onTouchEvent(ev)
    }

    // Ignore disallow requests: the stack must keep the horizontal stream so it
    // can switch widgets, while the intercept rule above still lets vertical
    // scrolls pass because they don't meet the horizontal threshold. This is
    // the simplest robust choice for a two-widget MVP; nested horizontally
    // scrolling widgets inside a stack member will be interrupted.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Deliberately do not propagate: we own horizontal arbitration.
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        measureChildren(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        reportMemberSizes()
    }

    // Keep the visible child correct when the drag layer hides and shows us.
    override fun setVisibility(visibility: Int) {
        val changed = this.visibility != visibility
        super.setVisibility(visibility)
        if (changed && visibility == View.VISIBLE) {
            applyVisibility()
        }
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 500f
        private const val SWITCH_ANIMATION_MS = 150L
        private const val HORIZONTAL_DOMINANCE = 1.5f
    }
}
