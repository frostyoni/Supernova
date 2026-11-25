package com.supernova.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.HorizontalScrollView
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.supernova.launcher.data.ChipEdit
import com.supernova.launcher.data.CustomTransition
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// The builder's timeline: incoming and outgoing lanes over the swipe's 0..1,
// each lane stacked into as many rows as its overlapping chips need. A chip's
// body drags in time (keeping its length); its ends resize it.
//
// It lives in a HorizontalScrollView and can be zoomed, so a chip can be made
// wide enough to grab precisely; the lane names stay pinned to the left edge
// while the time axis scrolls, and a playhead follows the preview.
@SuppressLint("ViewConstructor")
class TransitionTimelineView(context: Context) : View(context) {

    data class Selection(val incoming: Boolean, val index: Int)

    var transition: CustomTransition = CustomTransition()
        set(value) {
            field = value
            val current = selected
            if (current != null && current.index >= lane(current.incoming).size) selected = null
            requestLayout()
            invalidate()
        }

    var selected: Selection? = null
        set(value) {
            field = value
            invalidate()
            onSelected?.invoke()
        }

    // 1 = the whole swipe across the card; up to MAX_ZOOM for fine work.
    var zoom: Float = 1f
        set(value) {
            val clamped = value.coerceIn(1f, MAX_ZOOM)
            if (clamped == field) return
            field = clamped
            requestLayout()
            invalidate()
            onZoomChanged?.invoke(clamped)
        }

    // Where the preview is in its loop, 0..1.
    var playhead: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // The width the unzoomed timeline should fill; the card sets it.
    var baseWidthPx: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    var onChanged: (() -> Unit)? = null
    var onSelected: (() -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null
    // A chip was grabbed: one undo step for the whole drag, not one per frame.
    var onEditStart: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val gutter = 40f * density
    private val side = 10f * density
    private val rulerHeight = 26f * density
    private val chipHeight = 34f * density
    private val chipGap = 5f * density
    private val lanePad = 8f * density
    private val edgeGrab = 16f * density

    private val ink = MaterialColors.getColor(this, android.R.attr.textColorSecondary)
    private val accent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val laneWash = ColorUtils.setAlphaComponent(ink, 10)
    private val hairline = ColorUtils.setAlphaComponent(ink, 36)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = laneWash }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = hairline
    }
    private val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xCCFFFFFF.toInt()
    }
    private val selection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = accent
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = 0xFFFFFFFF.toInt()
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        color = ink
    }
    private val laneText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = ink
    }

    private val incomingRows = mutableListOf<MutableList<Pair<Int, CustomTransition.Chip>>>()
    private val outgoingRows = mutableListOf<MutableList<Pair<Int, CustomTransition.Chip>>>()
    private val laneTop = FloatArray(2)
    private val laneHeight = FloatArray(2)
    private var drag: Drag? = null
    private val rect = RectF()

    private val pinch = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                drag = null
                zoom *= detector.scaleFactor
                return true
            }
        }
    )

    private data class Drag(
        val incoming: Boolean,
        val index: Int,
        val edge: Int,
        val startTime: Float,
        val original: CustomTransition.Chip
    )

    private fun lane(incoming: Boolean): List<CustomTransition.Chip> =
        if (incoming) transition.incoming else transition.outgoing

    private fun packed(incoming: Boolean): MutableList<MutableList<Pair<Int, CustomTransition.Chip>>> {
        val rows = mutableListOf<MutableList<Pair<Int, CustomTransition.Chip>>>()
        lane(incoming).mapIndexed { index, chip -> index to chip }
            .sortedBy { it.second.at }
            .forEach { pair ->
                val row = rows.firstOrNull { row ->
                    (row.maxOfOrNull { it.second.to } ?: 0f) <= pair.second.at + 0.0001f
                } ?: mutableListOf<Pair<Int, CustomTransition.Chip>>().also { rows.add(it) }
                row.add(pair)
            }
        if (rows.isEmpty()) rows.add(mutableListOf())
        return rows
    }

    private fun rebuildRows() {
        incomingRows.clear()
        incomingRows.addAll(packed(true))
        outgoingRows.clear()
        outgoingRows.addAll(packed(false))
    }

    private fun unzoomedWidth(): Int =
        if (baseWidthPx > 0) baseWidthPx
        else (resources.displayMetrics.widthPixels - 96 * density).toInt()

    private fun timeArea(): Float = width - gutter - side * 2f

    private fun timeX(time: Float): Float = gutter + side + time.coerceIn(0f, 1f) * timeArea()

    private fun xTime(x: Float): Float =
        ((x - gutter - side) / timeArea().coerceAtLeast(1f)).coerceIn(0f, 1f)

    private fun scrollX(): Float = (parent as? HorizontalScrollView)?.scrollX?.toFloat() ?: 0f

    private fun laneAt(y: Float): Int? {
        for (lane in 0..1) {
            if (y >= laneTop[lane] && y <= laneTop[lane] + laneHeight[lane]) return lane
        }
        return null
    }

    private fun chipRect(incoming: Boolean, row: Int, chip: CustomTransition.Chip): RectF {
        val lane = if (incoming) 0 else 1
        val top = laneTop[lane] + lanePad + row * (chipHeight + chipGap)
        return RectF(
            timeX(chip.at), top,
            max(timeX(chip.to), timeX(chip.at) + 8f * density), top + chipHeight
        )
    }

    private fun rowOf(incoming: Boolean, index: Int): Int {
        val rows = if (incoming) incomingRows else outgoingRows
        rows.forEachIndexed { row, chips -> if (chips.any { it.first == index }) return row }
        return 0
    }

    private fun findChip(x: Float, y: Float): Triple<Boolean, Int, CustomTransition.Chip>? {
        val lane = laneAt(y) ?: return null
        val incoming = lane == 0
        val rows = if (incoming) incomingRows else outgoingRows
        rows.forEachIndexed { row, chips ->
            chips.forEach { (index, chip) ->
                if (chipRect(incoming, row, chip).contains(x, y)) return Triple(incoming, index, chip)
            }
        }
        return null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        rebuildRows()
        val width = max(1, (unzoomedWidth() * zoom).roundToInt())
        var top = rulerHeight
        for (lane in 0..1) {
            laneTop[lane] = top
            val rows = if (lane == 0) incomingRows.size else outgoingRows.size
            laneHeight[lane] = rows * (chipHeight + chipGap) - chipGap + lanePad * 2
            top += laneHeight[lane] + chipGap
        }
        setMeasuredDimension(width, resolveSize((top + side).roundToInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawRuler(canvas)
        drawLane(canvas, incoming = true, name = "IN")
        drawLane(canvas, incoming = false, name = "OUT")
        // The playhead rides over everything, so it is clear which chips are
        // acting at the moment the preview shows.
        val x = timeX(playhead)
        canvas.drawLine(x, rulerHeight - 6f * density, x, height - side, playheadPaint)
        fill.color = accent
        canvas.drawCircle(x, rulerHeight - 6f * density, 3f * density, fill)
    }

    private fun drawRuler(canvas: Canvas) {
        canvas.drawLine(gutter + side, rulerHeight - 1f, width - side, rulerHeight - 1f, line)
        val labelEvery = when {
            zoom >= 4f -> 1
            zoom >= 2f -> 2
            else -> 5
        }
        for (step in 0..10) {
            val time = step / 10f
            val x = timeX(time)
            val major = step % labelEvery == 0
            canvas.drawLine(x, rulerHeight - (if (major) 8f else 4f) * density, x, rulerHeight - 1f, line)
            if (major) {
                val text = String.format(Locale.US, "%.1f", time)
                val tx = (x - small.measureText(text) / 2f).coerceIn(0f, width - small.measureText(text))
                canvas.drawText(text, tx, rulerHeight - 12f * density, small)
            }
        }
    }

    private fun drawLane(canvas: Canvas, incoming: Boolean, name: String) {
        val lane = if (incoming) 0 else 1
        val top = laneTop[lane]
        val rows = if (incoming) incomingRows else outgoingRows
        // A wash behind the lane, so the two read as separate tracks.
        rect.set(gutter + side / 2f, top, width - side / 2f, top + laneHeight[lane])
        canvas.drawRoundRect(rect, 10f * density, 10f * density, wash)
        if (lane(incoming).isEmpty()) {
            canvas.drawText("empty", gutter + side + 6f * density, top + laneHeight[lane] / 2f + 4f * density, small)
        }
        rows.forEachIndexed { row, chips ->
            chips.forEach { (index, chip) ->
                val bounds = chipRect(incoming, row, chip)
                fill.color = colour(chip.channel)
                canvas.drawRoundRect(bounds, 9f * density, 9f * density, fill)
                val isSelected = selected == Selection(incoming, index)
                if (isSelected) {
                    canvas.drawRoundRect(bounds, 9f * density, 9f * density, selection)
                    // Grips at the ends: the resize affordance, made visible.
                    val inset = 5f * density
                    canvas.drawLine(
                        bounds.left + inset, bounds.centerY() - 6f * density,
                        bounds.left + inset, bounds.centerY() + 6f * density, grip
                    )
                    canvas.drawLine(
                        bounds.right - inset, bounds.centerY() - 6f * density,
                        bounds.right - inset, bounds.centerY() + 6f * density, grip
                    )
                }
                val title = label(chip, bounds.width())
                if (bounds.width() > 14f * density) {
                    canvas.save()
                    canvas.clipRect(bounds)
                    canvas.drawText(title, bounds.left + 8f * density, bounds.centerY() + 4f * density, chipText)
                    canvas.restore()
                }
            }
        }
        // Pinned to the visible left edge, over a pill so it stays readable
        // when the time axis has scrolled under it.
        val textWidth = laneText.measureText(name)
        val left = max(side, scrollX() + side)
        val centreY = top + laneHeight[lane] / 2f
        rect.set(left - 5f * density, centreY - 9f * density, left + textWidth + 5f * density, centreY + 9f * density)
        fill.color = laneWash
        canvas.drawRoundRect(rect, 8f * density, 8f * density, fill)
        canvas.drawText(name, left, centreY + 3.5f * density, laneText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        if (pinch.isInProgress) {
            drag = null
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findChip(event.x, event.y)
                if (hit == null) {
                    selected = null
                    return true
                }
                val (incoming, index, chip) = hit
                val bounds = chipRect(incoming, rowOf(incoming, index), chip)
                val edge = when {
                    abs(event.x - bounds.left) <= edgeGrab -> EDGE_START
                    abs(event.x - bounds.right) <= edgeGrab -> EDGE_END
                    else -> EDGE_BODY
                }
                onEditStart?.invoke()
                selected = Selection(incoming, index)
                drag = Drag(incoming, index, edge, xTime(event.x), chip)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = drag ?: return true
                val time = ChipEdit.snap(xTime(event.x))
                val edited = when (current.edge) {
                    EDGE_START -> ChipEdit.resizeStart(current.original, time)
                    EDGE_END -> ChipEdit.resizeEnd(current.original, time)
                    else -> ChipEdit.move(current.original, current.original.at + (time - current.startTime))
                }
                replace(current.incoming, current.index, edited)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun replace(incoming: Boolean, index: Int, chip: CustomTransition.Chip) {
        val chips = lane(incoming).toMutableList()
        if (index !in chips.indices) return
        chips[index] = chip
        transition = if (incoming) transition.copy(incoming = chips) else transition.copy(outgoing = chips)
        onChanged?.invoke()
    }

    private fun colour(channel: CustomTransition.Channel): Int = when (channel) {
        CustomTransition.Channel.MOVE_X, CustomTransition.Channel.MOVE_Y -> 0xFF3F72AF.toInt()
        CustomTransition.Channel.SCALE -> 0xFF2E7D5B.toInt()
        CustomTransition.Channel.ALPHA -> 0xFF7B4FA0.toInt()
        CustomTransition.Channel.ROLL, CustomTransition.Channel.PITCH,
        CustomTransition.Channel.YAW -> 0xFFA34E44.toInt()
    }

    private fun label(chip: CustomTransition.Chip, width: Float): String {
        val name = when (chip.channel) {
            CustomTransition.Channel.MOVE_X -> "X"
            CustomTransition.Channel.MOVE_Y -> "Y"
            CustomTransition.Channel.SCALE -> "scale"
            CustomTransition.Channel.ALPHA -> "fade"
            CustomTransition.Channel.ROLL -> "roll"
            CustomTransition.Channel.PITCH -> "pitch"
            CustomTransition.Channel.YAW -> "yaw"
        }
        // Narrow chips keep just the channel; the inspector has the numbers,
        // so a label is never cut mid-number.
        return if (width >= 92f * density) {
            String.format(Locale.US, "%s %.2f→%.2f", name, chip.from, chip.value)
        } else {
            name
        }
    }

    private companion object {
        const val EDGE_START = 0
        const val EDGE_END = 1
        const val EDGE_BODY = 2
        const val MAX_ZOOM = 6f
    }
}
