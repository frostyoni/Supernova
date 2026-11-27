package com.supernova.launcher.ui

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.WidgetStacks
import kotlin.math.abs

// A workspace widget stack: one cell that hosts app widgets, with a
// horizontal swipe switching the visible one. The primary widget (item's
// appWidgetId) is first; widgetStack members follow in order. Only the
// active member and its neighbours are inflated; hidden members are measured
// and laid out at the container's size once attached so providers see stable
// bounds.
class WidgetStackView(
    context: Context,
    private val controller: WidgetController,
    private var item: LauncherItem,
    private val onLongPress: (View, LauncherItem) -> Unit
) : FrameLayout(context) {

    private data class StackChild(
        val widgetId: Int,
        var hostView: AppWidgetHostView? = null
    )

    private val children = mutableListOf<StackChild>()
    private var frontIndex: Int = 0
    private var switchGeneration: Int = 0
    private var switching: Boolean = false

    val currentItem: LauncherItem get() = item

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    // A member that scrolls sideways itself asked for the stream: the stack
    // must not take it, or a calendar's week strip stops working.
    private var childClaimsStream = false
    // Whether the showing member scrolls sideways, asked once on the press:
    // the answer cannot change mid-gesture and the tree walk is not free.
    private var frontScrolls = false
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * resources.displayMetrics.density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = 0xFFFFFFFF.toInt()
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.a11y_stack_next)) { _, _ ->
            showNext()
            true
        }
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.a11y_stack_previous)) { _, _ ->
            showPrevious()
            true
        }
        updateMembers(item)
    }

    fun updateMembers(newItem: LauncherItem) {
        item = newItem
        switchGeneration++
        switching = false
        children.forEach { child ->
            child.hostView?.animate()?.cancel()
            child.hostView?.alpha = 1f
            child.hostView?.translationX = 0f
        }

        val desiredIds = buildList {
            item.appWidgetId?.let { add(it) }
            item.widgetStack.mapNotNullTo(this) { it.appWidgetId }
        }.distinct().filter(controller::hasWidgetProvider)

        val iter = children.iterator()
        while (iter.hasNext()) {
            val child = iter.next()
            if (child.widgetId !in desiredIds) {
                child.hostView?.let { hostView ->
                    hostView.animate().cancel()
                    hostView.alpha = 1f
                    hostView.translationX = 0f
                    (hostView as? com.supernova.launcher.widget.SupernovaWidgetHostView)?.updatesPaused = false
                    removeView(hostView)
                }
                iter.remove()
            }
        }

        val previousChildren = children.associateBy { it.widgetId }
        val reordered = desiredIds.map { widgetId ->
            StackChild(widgetId, previousChildren[widgetId]?.hostView)
        }
        children.clear()
        children.addAll(reordered)

        val activeId = WidgetStacks.activeWidgetId(item)
        frontIndex = children.indexOfFirst { it.widgetId == activeId }.takeIf { it >= 0 } ?: 0
        applyVisibility()
    }

    fun reportMemberSizes() {
        if (width <= 0 || height <= 0) return
        children.forEach { child ->
            child.hostView?.let { hostView ->
                controller.reportStackMemberSize(child.widgetId, hostView, width, height)
            }
        }
    }

    private fun applyVisibility(force: Boolean = false) {
        if (switching && !force) return
        if (children.isNotEmpty()) {
            ensureMemberHostView(frontIndex)
            if (children.size > 1) {
                ensureMemberHostView((frontIndex + 1) % children.size)
                ensureMemberHostView((frontIndex - 1 + children.size) % children.size)
            }
        }
        children.forEachIndexed { index, child ->
            val hostView = child.hostView ?: return@forEachIndexed
            hostView.animate().cancel()
            hostView.alpha = 1f
            hostView.translationX = 0f
            val visible = index == frontIndex
            hostView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            (hostView as? com.supernova.launcher.widget.SupernovaWidgetHostView)?.updatesPaused = !visible
        }
    }

    private fun ensureMemberHostView(index: Int): AppWidgetHostView? {
        val child = children.getOrNull(index) ?: return null
        val existing = child.hostView
        return existing ?: controller.stackMemberHostView(child.widgetId)?.also { hostView ->
            hostView.setOnLongClickListener {
                onLongPress(this@WidgetStackView, item)
                true
            }
            (hostView.parent as? ViewGroup)?.removeView(hostView)
            hostView.alpha = 1f
            hostView.translationX = 0f
            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(hostView, layoutParams)
            child.hostView = hostView
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
        val forward = newFront > frontIndex || (frontIndex == children.size - 1 && newFront == 0)
        val travel = width * SLIDE_FRACTION * (if (forward) -1f else 1f)
        frontIndex = newFront
        val newChild = ensureMemberHostView(newFront)

        oldChild?.animate()?.cancel()
        newChild?.animate()?.cancel()
        oldChild?.alpha = 1f
        oldChild?.translationX = 0f
        oldChild?.visibility = View.VISIBLE
        newChild?.alpha = 0f
        newChild?.translationX = -travel
        newChild?.visibility = View.VISIBLE

        val modelOrder = WidgetStacks.order(item)
        val activeWidgetId = children.getOrNull(frontIndex)?.widgetId
        val modelIndex = modelOrder.indexOfFirst { it.appWidgetId == activeWidgetId }
        val persistedIndex = if (modelIndex >= 0) modelIndex else frontIndex
        item = item.copy(widgetStackFront = persistedIndex)
        controller.onStackFrontChanged(item.id, persistedIndex)

        val duration = controller.animationDuration(SWITCH_ANIMATION_MS)
        val generation = ++switchGeneration
        if (duration <= 0L) {
            applyVisibility(force = true)
            invalidate()
            performStackHaptic()
            return
        }

        animateSwitch(oldChild, newChild, travel, duration, generation)
        invalidate()
        performStackHaptic()
    }

    private fun animateSwitch(
        oldChild: View?,
        newChild: View?,
        travel: Float,
        duration: Long,
        generation: Int
    ) {
        switching = true
        val interpolator = android.view.animation.DecelerateInterpolator()
        var pendingAnimations = 0
        if (oldChild != null) pendingAnimations++
        if (newChild != null) pendingAnimations++

        oldChild?.let { old ->
            old.animate()
                .alpha(0f)
                .translationX(travel)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction {
                    if (generation == switchGeneration) {
                        old.visibility = View.INVISIBLE
                        old.alpha = 1f
                        old.translationX = 0f
                        if (--pendingAnimations == 0) finishSwitch(generation)
                    }
                }
                .start()
        }
        newChild?.animate()
            ?.alpha(1f)
            ?.translationX(0f)
            ?.setDuration(duration)
            ?.setInterpolator(interpolator)
            ?.withEndAction {
                if (generation == switchGeneration && --pendingAnimations == 0) finishSwitch(generation)
            }
            ?.start()
    }

    private fun finishSwitch(generation: Int) {
        if (generation != switchGeneration) return
        switching = false
        applyVisibility(force = true)
    }

    private fun performStackHaptic() {
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    // Whether the showing member scrolls sideways itself. Asked on the press,
    // not on every move: the tree is small and the answer cannot change
    // mid-gesture.
    private fun frontScrollsHorizontally(): Boolean {
        val root = children.getOrNull(frontIndex)?.hostView ?: return false
        val pending = ArrayDeque<View>()
        pending += root
        while (pending.isNotEmpty()) {
            val view = pending.removeFirst()
            if (view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) pending += view.getChildAt(index)
            }
        }
        return false
    }

    // Which widget is showing, as dots at the foot of the cell. Drawn over
    // the widget: one dot is cheap, and a child view there would take the
    // widget's taps.
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val count = children.size
        if (count <= 1) return
        val density = resources.displayMetrics.density
        val radius = 2.2f * density
        val gap = 7f * density
        // Past a handful the dots stop being countable, so the tail becomes a
        // number: which one is showing still reads at a glance.
        val dots = minOf(count, MAX_DOTS)
        val overflow = count - dots
        val suffix = if (overflow > 0) "+$overflow" else ""
        val suffixWidth = if (suffix.isEmpty()) 0f else countPaint.measureText(suffix) + gap
        val total = dots * radius * 2 + (dots - 1) * gap + suffixWidth
        val left = width / 2f - total / 2f
        val centreY = height - 10f * density
        pillPaint.color = PILL_COLOR
        canvas.drawRoundRect(
            left - 7f * density, centreY - radius - 4f * density,
            left + total + 7f * density, centreY + radius + 4f * density,
            radius + 4f * density, radius + 4f * density, pillPaint
        )
        for (index in 0 until dots) {
            // With an overflow the last dot stands for the tail, and lights
            // when the front is out there.
            val active = if (overflow > 0 && index == dots - 1) frontIndex >= dots - 1 else index == frontIndex
            dotPaint.color = if (active) ACTIVE_DOT else INACTIVE_DOT
            canvas.drawCircle(
                left + radius + index * (radius * 2 + gap), centreY,
                if (active) radius * 1.25f else radius, dotPaint
            )
        }
        if (suffix.isNotEmpty()) {
            canvas.drawText(
                suffix,
                left + dots * (radius * 2 + gap),
                centreY + countPaint.textSize * 0.36f,
                countPaint
            )
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.onTouchEvent(ev)
                downX = ev.x
                downY = ev.y
                tracking = false
                // Claim the stream from the pager at once. Waiting for the
                // first move lost the race to ViewPager2 on ordinary swipes,
                // which turned the page instead of the stack.
                childClaimsStream = false
                frontScrolls = frontScrollsHorizontally()
                if (children.size > 1 && !frontScrolls) {
                    (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> if (!tracking && children.size > 1 && !childClaimsStream &&
                !frontScrolls
            ) {
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

    // A member asking for the stream gets it: the stack stops claiming
    // horizontal moves for this gesture, and the request goes on up so the
    // page pager leaves it alone too.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && frontScrolls) childClaimsStream = true
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
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
        if (changed) applyVisibility()
    }

    fun trimInactiveMembers() {
        children.forEachIndexed { index, child ->
            if (index == frontIndex) return@forEachIndexed
            child.hostView?.let { hostView ->
                hostView.animate().cancel()
                hostView.alpha = 1f
                hostView.translationX = 0f
                (hostView as? com.supernova.launcher.widget.SupernovaWidgetHostView)?.updatesPaused = false
                removeView(hostView)
            }
            child.hostView = null
        }
    }

    fun clearStack() {
        switchGeneration++
        switching = false
        children.forEach { child ->
            child.hostView?.let { hostView ->
                hostView.animate().cancel()
                hostView.alpha = 1f
                hostView.translationX = 0f
                (hostView as? com.supernova.launcher.widget.SupernovaWidgetHostView)?.updatesPaused = false
            }
        }
        children.clear()
        removeAllViews()
    }

    // Keep the visible child correct when the drag layer hides and shows us.
    override fun setVisibility(visibility: Int) {
        val changed = this.visibility != visibility
        super.setVisibility(visibility)
        if (changed && visibility == View.VISIBLE) {
            applyVisibility(force = true)
        }
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 500f
        private const val SWITCH_ANIMATION_MS = 150L
        private const val HORIZONTAL_DOMINANCE = 1.5f
        private const val SLIDE_FRACTION = 0.22f
        private const val MAX_DOTS = 7
        private const val PILL_COLOR = 0x33000000
        private const val ACTIVE_DOT = 0xFFFFFFFF.toInt()
        private const val INACTIVE_DOT = 0x66FFFFFF
    }
}
