package com.supernova.launcher.ui.settings.preview

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.supernova.launcher.data.CustomTransition
import com.supernova.launcher.data.CustomTransitionStore
import com.supernova.launcher.ui.PageTransformers
import com.supernova.launcher.ui.ScrollEffect

// The Page scroll effect picker's preview (#260): two mini home pages in a
// real ViewPager2 with the real PageTransformers, swiped back and forth on
// a loop by a fake drag, so each effect looks as it does on the home screen.
// Built in code with its icons, never from a layout.
@android.annotation.SuppressLint("ViewConstructor")
class PageEffectPreviewView(
    context: Context,
    icons: List<Drawable>,
    columns: Int
) : FrameLayout(context) {

    private val pager = ViewPager2(context).apply {
        isUserInputEnabled = false
        offscreenPageLimit = 1
        // The see-through pages must not draw past the box's rounded edge.
        clipChildren = true
    }

    private val direction = com.supernova.launcher.ui.ScrollDirection(pager)
    private var effect: ScrollEffect = ScrollEffect.NONE
    private var custom: CustomTransition? = null

    // Where the loop has got to, 0..1: the builder's timeline playhead.
    var onProgress: ((Float) -> Unit)? = null
    private val ease = AccelerateDecelerateInterpolator()
    // How far the fake drag has gone, in pixels, toward the second page.
    private var dragged = 0f
    private val clock = PreviewClock(this, ::onFrame)

    init {
        // Most of the second page empty, as a newer page usually is.
        val second = (icons.size * SECOND_PAGE_SHARE).toInt().coerceAtLeast(1)
        val pages = listOf(icons, icons.takeLast(second))
        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = pages.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(FrameLayout(parent.context).apply {
                    val match = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams = ViewGroup.LayoutParams(match, match)
                }) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val frame = holder.itemView as FrameLayout
                frame.removeAllViews()
                // Each page gets icons of its own: one drawable can't sit in
                // two views.
                val own = pages[position].map { it.constantState?.newDrawable(context.resources)?.mutate() ?: it }
                frame.addView(MotionPreviewParts.page(context, own, columns))
            }
        }
        // The sheet scrolls the first nested-scrolling view it finds, and
        // the pager's list comes before the sheet's options: left on, the
        // options never scrolled and the last ones stayed below the screen.
        pager.getChildAt(0)?.isNestedScrollingEnabled = false
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(effect: ScrollEffect) {
        show(effect, custom = null, replay = true)
    }

    // The builder's live preview: a spec that is not saved yet, and edits
    // that should not restart the loop on every drag.
    fun show(effect: ScrollEffect, custom: CustomTransition?, replay: Boolean) {
        val spec = custom ?: if (effect == ScrollEffect.CUSTOM) CustomTransitionStore.load(context) else null
        if (effect != this.effect || spec != this.custom) {
            this.effect = effect
            this.custom = spec
            (pager.getChildAt(0) as? RecyclerView)?.let { recycler ->
                for (index in 0 until recycler.childCount) PageTransformers.resetTransforms(recycler.getChildAt(index))
            }
            pager.setPageTransformer(PageTransformers.forEffect(effect, spec, direction))
        }
        if (replay) replay()
    }

    // From the first page again.
    fun replay() {
        if (pager.isFakeDragging) pager.endFakeDrag()
        dragged = 0f
        pager.setCurrentItem(0, false)
        if (isAttachedToWindow) clock.restart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clock.restart()
    }

    override fun onDetachedFromWindow() {
        clock.stop()
        super.onDetachedFromWindow()
    }

    // One loop: rest on the first page, swipe to the second, rest, swipe back.
    private fun onFrame(elapsed: Long) {
        val width = pager.width.toFloat()
        val t = elapsed % LOOP_MS
        val target = when {
            t < REST_MS -> 0f
            t < REST_MS + SWIPE_MS -> ease.getInterpolation((t - REST_MS) / SWIPE_MS.toFloat())
            t < 2 * REST_MS + SWIPE_MS -> 1f
            else -> 1f - ease.getInterpolation((t - 2 * REST_MS - SWIPE_MS) / SWIPE_MS.toFloat())
        } * width
        if (width > 0f) onProgress?.invoke(target / width)
        val moves = width > 0f && target != dragged && (pager.isFakeDragging || pager.beginFakeDrag())
        if (moves) {
            // A drag toward the next page moves the finger left: negative.
            pager.fakeDragBy(dragged - target)
            dragged = target
        }
        // Let go at either page, as a finger would.
        if (pager.isFakeDragging && (target == 0f || target == width)) pager.endFakeDrag()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { replay() }
    }

    private companion object {
        const val REST_MS = 700L
        const val SWIPE_MS = 650L
        const val LOOP_MS = 2 * (REST_MS + SWIPE_MS)
        const val SECOND_PAGE_SHARE = 0.4f
    }
}
