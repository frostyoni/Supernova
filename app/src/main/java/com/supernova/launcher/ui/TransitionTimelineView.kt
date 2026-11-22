package com.supernova.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.supernova.launcher.data.ChipEdit
import com.supernova.launcher.data.CustomTransition
import java.util.Locale
import kotlin.math.abs

// The builder's timeline: incoming and outgoing lanes over the swipe's 0..1,
// each lane stacked into as many rows as its overlapping chips need. A chip's
// body drags in time (keeping its length); its ends resize it. The view owns
// no spec of its own beyond editing the one given to it.
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

    var onChanged: (() -> Unit)? = null
    var onSelected: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val labelWidth = 34f * density
    private val side = 8f * density
    private val rulerHeight = 20f * density
    private val chipHeight = 32f * density
    private val chipGap = 4f * density
    private val laneGap = 10f * density
    private val edgeGrab = 12f * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFFFFFFF.toInt()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        color = 0xFF9AA4B2.toInt()
    }
    private val ruler = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = 0x339AA4B2
    }

    private val incomingRows = mutableListOf<MutableList<Pair<Int, CustomTransition.Chip>>>()
    private val outgoingRows = mutableListOf<MutableList<Pair<Int, CustomTransition.Chip>>>()
    private var laneTop = FloatArray(2)
    private var laneHeight = FloatArray(2)
    private var drag: Drag? = null

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
                } ?: mutableListOf<Pair<Int, CustomTransition.Chip>>().also { rows += it }
                row += pair
            }
        return rows
    }

    private fun rebuildRows() {
        incomingRows.clear()
        incomingRows.addAll(packed(true))
        outgoingRows.clear()
        outgoingRows.addAll(packed(false))
        if (incomingRows.isEmpty()) incomingRows.add(mutableListOf())
        if (outgoingRows.isEmpty()) outgoingRows.add(mutableListOf())
    }

    private fun timeArea(): Float = width - labelWidth - side * 2f

    private fun timeX(time: Float): Float = labelWidth + side + time.coerceIn(0f, 1f) * timeArea()

    private fun xTime(x: Float): Float =
        ((x - labelWidth - side) / timeArea().coerceAtLeast(1f)).coerceIn(0f, 1f)

    private fun laneAt(y: Float): Int? {
        for (lane in 0..1) {
            val top = laneTop[lane]
            if (y >= top && y <= top + laneHeight[lane]) return lane
        }
        return null
    }

    private fun chipRect(incoming: Boolean, row: Int, chip: CustomTransition.Chip): RectF {
        val lane = if (incoming) 0 else 1
        val top = laneTop[lane] + row * (chipHeight + chipGap)
        return RectF(
            timeX(chip.at), top,
            timeX(chip.to).coerceAtLeast(timeX(chip.at) + 6f * density), top + chipHeight
        )
    }

    private fun findChip(x: Float, y: Float): Triple<Boolean, Int, CustomTransition.Chip>? {
        val lane = laneAt(y) ?: return null
        val incoming = lane == 0
        val rows = if (incoming) incomingRows else outgoingRows
        for (row in rows) {
            for ((index, chip) in row) {
                if (chipRect(incoming, rows.indexOf(row), chip).contains(x, y)) {
                    return Triple(incoming, index, chip)
                }
            }
        }
        return null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        rebuildRows()
        var top = rulerHeight
        for (lane in 0..1) {
            laneTop[lane] = top
            val rows = if (lane == 0) incomingRows.size else outgoingRows.size
            laneHeight[lane] = rows * (chipHeight + chipGap) + laneGap
            top += laneHeight[lane]
        }
        val height = resolveSize((top + side).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Ruler: quarters, with the ends labelled.
        for (i in 0..4) {
            val x = timeX(i / 4f)
            canvas.drawLine(x, rulerHeight - 5f * density, x, rulerHeight, ruler)
        }
        listOf(0f, 0.5f, 1f).forEach { at ->
            val textValue = String.format(Locale.US, "%.1f", at)
            val x = (timeX(at) - label.measureText(textValue) / 2f).coerceIn(0f, width - label.measureText(textValue))
            canvas.drawText(textValue, x, rulerHeight - 8f * density, label)
        }
        drawLane(canvas, incoming = true, laneTop[0], "In")
        drawLane(canvas, incoming = false, laneTop[1], "Out")
    }

    private fun drawLane(canvas: Canvas, incoming: Boolean, top: Float, name: String) {
        canvas.drawText(name, 0f, top + chipHeight / 2f + 4f * density, label)
        val rows = if (incoming) incomingRows else outgoingRows
        rows.forEachIndexed { row, chips ->
            chips.forEach { (index, chip) ->
                val rect = chipRect(incoming, row, chip)
                fill.color = colour(chip.channel)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, fill)
                if (selected == Selection(incoming, index)) {
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, stroke)
                }
                val name = title(chip)
                // Narrow chips show the channel only; the inspector has the
                // numbers, so the label never has to be cut mid-number.
                val title = if (rect.width() >= 86f * density) {
                    String.format(Locale.US, "%s %.2f→%.2f", name, chip.from, chip.value)
                } else {
                    name
                }
                val available = rect.width() - 10f * density
                if (available > 8f * density) {
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.drawText(title, rect.left + 5f * density, rect.centerY() + 4f * density, text)
                    canvas.restore()
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findChip(event.x, event.y)
                if (hit == null) {
                    selected = null
                    return true
                }
                val (incoming, index, chip) = hit
                val rect = chipRect(incoming, rowOf(incoming, index), chip)
                val edge = when {
                    abs(event.x - rect.left) <= edgeGrab -> EDGE_START
                    abs(event.x - rect.right) <= edgeGrab -> EDGE_END
                    else -> EDGE_BODY
                }
                selected = Selection(incoming, index)
                drag = Drag(incoming, index, edge, xTime(event.x), chip)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val drag = drag ?: return true
                val time = ChipEdit.snap(xTime(event.x))
                val edited = when (drag.edge) {
                    EDGE_START -> ChipEdit.resizeStart(drag.original, time)
                    EDGE_END -> ChipEdit.resizeEnd(drag.original, time)
                    else -> {
                        val delta = time - drag.startTime
                        ChipEdit.move(drag.original, drag.original.at + delta)
                    }
                }
                replace(drag.incoming, drag.index, edited)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun rowOf(incoming: Boolean, index: Int): Int {
        val rows = if (incoming) incomingRows else outgoingRows
        rows.forEachIndexed { row, chips ->
            if (chips.any { it.first == index }) return row
        }
        return 0
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

    private fun title(chip: CustomTransition.Chip): String {
        val name = when (chip.channel) {
            CustomTransition.Channel.MOVE_X -> "X"
            CustomTransition.Channel.MOVE_Y -> "Y"
            CustomTransition.Channel.SCALE -> "scale"
            CustomTransition.Channel.ALPHA -> "fade"
            CustomTransition.Channel.ROLL -> "roll"
            CustomTransition.Channel.PITCH -> "pitch"
            CustomTransition.Channel.YAW -> "yaw"
        }
        return name
    }

    private companion object {
        const val EDGE_START = 0
        const val EDGE_END = 1
        const val EDGE_BODY = 2
    }
}
