package com.supernova.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.supernova.launcher.R

// The drawer's alphabet index: a column of letters, each jumping the list to
// the first app under it. A letter with no apps stays dimmed and does
// nothing. It takes the fast scrollbar's edge while on, so the two are never
// drawn over each other.
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // The list's letter sections, asked for as needed: the order moves with
    // the sort, a search, a tab.
    internal var sectionProvider: (() -> List<FastScrollMath.Section>)? = null
    internal var onSection: ((FastScrollMath.Section) -> Unit)? = null

    // The letter of the section at the top of the list.
    var currentLetter: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val letters: List<String> = ('A'..'Z').map(Char::toString) + "#"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val density = resources.displayMetrics.density
    private var textColor = 0xFFB3B3B3.toInt()
    private var accentColor = 0xFFFFFFFF.toInt()
    private var pressed: String? = null
    private var lastJumped: String? = null

    init {
        isClickable = true
        contentDescription = context.getString(R.string.alphabet_index)
    }

    fun setColors(text: Int, accent: Int) {
        textColor = text
        accentColor = accent
        invalidate()
    }

    fun refresh() = invalidate()

    override fun onDraw(canvas: Canvas) {
        val available = sectionProvider?.invoke().orEmpty().associateBy { it.label }
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (bottom <= top) return
        val step = (bottom - top) / letters.size
        paint.textSize = (step * 0.58f).coerceIn(8f * density, 11f * density)
        val x = width / 2f
        letters.forEachIndexed { index, letter ->
            paint.color = when {
                letter == pressed -> accentColor
                letter == currentLetter -> accentColor
                available.containsKey(letter) -> textColor
                else -> (textColor and 0x00FFFFFF) or 0x33000000
            }
            val baseline = top + step * (index + 0.5f) + paint.textSize * 0.36f
            canvas.drawText(letter, x, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val letter = letterAt(event.y)
                val section = sectionProvider?.invoke()?.firstOrNull { it.label == letter }
                if (section != null) {
                    // One jump per letter: dragging across the column scrubs
                    // the list without repeating the same section.
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || letter != lastJumped) {
                        lastJumped = letter
                        pressed = letter
                        onSection?.invoke(section)
                    }
                } else {
                    pressed = null
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed = null
                lastJumped = null
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun letterAt(y: Float): String {
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat().coerceAtLeast(top + 1f)
        val step = (bottom - top) / letters.size
        val index = ((y - top) / step).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }
}
