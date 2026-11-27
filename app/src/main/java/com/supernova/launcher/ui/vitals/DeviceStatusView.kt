@file:Suppress("MagicNumber", "TooManyFunctions", "MaxLineLength")

package com.supernova.launcher.ui.vitals

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import androidx.core.graphics.ColorUtils
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.data.VitalsHistory
import com.supernova.launcher.data.VitalsMath
import com.supernova.launcher.data.VitalsReader
import com.supernova.launcher.data.VitalsSnapshot
import com.supernova.launcher.data.VitalsState
import com.supernova.launcher.ui.Animations
import com.supernova.launcher.ui.theme.Styles
import com.supernova.launcher.ui.theme.SurfaceId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DeviceStatusView(
    context: Context,
    private var item: LauncherItem,
    private var prefs: LauncherPrefs,
    private val onLongPress: (View, LauncherItem) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val reader = VitalsReader(context)
    private val history = VitalsHistory(HISTORY_SIZE)
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var touchSlop = 0
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private var moved = false
    private val longPressCheck = Runnable {
        if (!moved) {
            longPressFired = true
            performLongClick()
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = sample()
    }

    private var snapshot = VitalsSnapshot()
    private var displayBattery = 0f
    private var displayMemory = 0f
    private var displayStorage = 0f
    private var ringRect = RectF()
    private var memoryRect = RectF()
    private var storageRect = RectF()
    private var expanded = false
    private var surface = Color.DKGRAY
    private var textColor = Color.WHITE
    private var mutedColor = Color.LTGRAY
    private var accent = Color.WHITE
    private var stateColor = Color.WHITE
    private var state = VitalsState.NORMAL

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private val path = Path()
    private val area = Path()
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        isClickable = true
        isLongClickable = true
        setOnLongClickListener {
            onLongPress(this, item)
            true
        }
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        loadHistory()
        refreshTheme()
        updateSummary()
    }

    fun update(newItem: LauncherItem, newPrefs: LauncherPrefs) {
        item = newItem
        prefs = newPrefs
        handler.removeCallbacks(sampleRunnable)
        refreshTheme()
        invalidate()
        if (isAttachedToWindow) handler.postDelayed(sampleRunnable, refreshMs())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sample()
        handler.postDelayed(sampleRunnable, refreshMs())
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(sampleRunnable)
        runCatching { context.unregisterReceiver(batteryReceiver) }
        saveHistory()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        expanded = item.spanX >= 4 || w.toFloat() / h.coerceAtLeast(1) >= 1.55f
        if (w > 0 && h > 0) post { sample() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val padding = dp(14f)
        val radius = dp((item.widgetCornerRadius ?: prefs.widgetCornerRadius).coerceAtLeast(0).toFloat())
        fill.color = surface
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fill)
        drawRing(canvas, padding, h)
        drawMetrics(canvas, padding, w, h)
    }

    private fun drawRing(canvas: Canvas, padding: Float, h: Float) {
        val size = if (expanded) min(h - padding * 2f, dp(126f)) else min(h * 0.58f, dp(92f))
        val left = padding
        val top = padding + (h - padding * 2f - size) / 2f
        ringRect.set(left, top, left + size, top + size)
        val strokeWidth = if (expanded) dp(9f) else dp(7f)
        stroke.strokeWidth = strokeWidth
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = ColorUtils.setAlphaComponent(stateColor, 36)
        canvas.drawArc(ringRect, -90f, 360f, false, stroke)
        stroke.color = stateColor
        canvas.drawArc(ringRect, -90f, 360f * displayBattery / 100f, false, stroke)

        boldPaint.color = textColor
        boldPaint.textAlign = Paint.Align.CENTER
        boldPaint.textSize = if (expanded) sp(25f) else sp(21f)
        val centerX = ringRect.centerX()
        val centerY = ringRect.centerY() + boldPaint.textSize * 0.34f
        canvas.drawText("${snapshot.batteryPercent}%", centerX, centerY, boldPaint)
        textPaint.color = mutedColor
        textPaint.textSize = sp(9f)
        textPaint.textAlign = Paint.Align.CENTER
        val time = if (snapshot.charging) {
            VitalsMath.formatChargeTime(snapshot.chargeTimeMs)?.let { context.getString(R.string.vitals_full_in, it) }
                ?: context.getString(R.string.vitals_charging)
        } else {
            context.getString(R.string.vitals_battery)
        }
        canvas.drawText(time, centerX, centerY + sp(16f), textPaint)
        memoryRect.set(ringRect.left, ringRect.bottom + dp(2f), ringRect.right, ringRect.bottom + dp(2f))
        storageRect.set(memoryRect)
    }

    private fun drawMetrics(canvas: Canvas, padding: Float, w: Float, h: Float) {
        val compact = !expanded
        val right = w - padding
        val left = if (compact) ringRect.right + dp(8f) else ringRect.right + dp(12f)
        val barsLeft = if (compact) padding else left
        val barsTop = if (compact) ringRect.bottom + dp(12f) else h - padding - dp(38f)
        val graphTop = padding + dp(1f)
        val graphBottom = if (compact) ringRect.bottom else barsTop - dp(10f)
        val graphHeight = max(dp(26f), graphBottom - graphTop)
        val tempValue = VitalsMath.formatTemp(snapshot.batteryTempC)
        if (expanded) {
            val half = (right - left - dp(8f)) / 2f
            drawSpark(
                canvas, RectF(left, graphTop, left + half, graphTop + graphHeight),
                history.temperature(), stateColor, tempValue, context.getString(R.string.vitals_temp)
            )
            if (prefs.vitalsCpu) {
                drawSpark(
                    canvas, RectF(left + half + dp(8f), graphTop, right, graphTop + graphHeight),
                    history.cpu(), accent, VitalsMath.formatPercent(snapshot.cpuPercent), context.getString(R.string.vitals_cpu_label)
                )
            } else {
                drawSpark(
                    canvas, RectF(left + half + dp(8f), graphTop, right, graphTop + graphHeight),
                    history.memory(), accent, VitalsMath.formatPercent(snapshot.memoryRatio * 100f), context.getString(R.string.vitals_memory)
                )
            }
        } else {
            drawSpark(
                canvas, RectF(left, graphTop, right, graphTop + graphHeight),
                history.temperature(), stateColor, tempValue, context.getString(R.string.vitals_temp)
            )
        }
        if (compact && h < dp(140f)) {
            memoryRect.setEmpty()
            storageRect.setEmpty()
            return
        }
        memoryRect.set(barsLeft, barsTop, right, barsTop + dp(13f))
        storageRect.set(barsLeft, barsTop + dp(21f), right, barsTop + dp(34f))
        drawBar(
            canvas, memoryRect, context.getString(R.string.vitals_memory),
            displayMemory / 100f, "${snapshot.memoryUsed / GB}/${snapshot.memoryTotal / GB} GB", accent
        )
        drawBar(
            canvas, storageRect, context.getString(R.string.vitals_storage),
            displayStorage / 100f, "${snapshot.storageUsed / GB}/${snapshot.storageTotal / GB} GB", accent
        )
    }

    @Suppress("LongParameterList")
    private fun drawSpark(
        canvas: Canvas,
        rect: RectF,
        values: FloatArray,
        color: Int,
        value: String,
        label: String
    ) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        fill.color = ColorUtils.setAlphaComponent(textColor, 14)
        canvas.drawRoundRect(
            rect.left - dp(6f), rect.top - dp(4f), rect.right + dp(2f), rect.bottom + dp(2f),
            dp(12f), dp(12f), fill
        )
        textPaint.color = mutedColor
        textPaint.textSize = sp(8f)
        textPaint.textAlign = Paint.Align.LEFT
        if (rect.width() >= dp(70f)) canvas.drawText(label, rect.left, rect.top + sp(9f), textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, rect.right, rect.top + sp(9f), textPaint)
        val graphTop = rect.top + sp(12f)
        val graphBottom = rect.bottom
        val rawLow = values.minOrNull() ?: 0f
        val rawHigh = values.maxOrNull() ?: 1f
        val padding = max((rawHigh - rawLow) * 0.15f, 1f)
        val low = rawLow - padding
        val high = rawHigh + padding
        val range = (high - low).coerceAtLeast(1f)
        path.reset()
        area.reset()
        values.forEachIndexed { index, value ->
            val x = rect.left + rect.width() * index / (values.size - 1).coerceAtLeast(1)
            val y = graphBottom - (value - low) / range * (graphBottom - graphTop)
            if (index == 0) {
                path.moveTo(x, y)
                area.moveTo(x, graphBottom)
                area.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(rect.right, graphBottom)
        area.close()
        areaPaint.shader = LinearGradient(
            0f, graphTop, 0f, graphBottom,
            ColorUtils.setAlphaComponent(color, 96), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawPath(area, areaPaint)
        areaPaint.shader = null
        stroke.strokeWidth = dp(1.5f)
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = color
        canvas.drawPath(path, stroke)
        if (values.isNotEmpty()) {
            val x = rect.right
            val y = graphBottom - (values.last() - low) / range * (graphBottom - graphTop)
            fill.color = color
            canvas.drawCircle(x, y, dp(2f), fill)
        }
    }

    @Suppress("LongParameterList")
    private fun drawBar(canvas: Canvas, rect: RectF, label: String, fraction: Float, value: String, color: Int) {
        textPaint.color = mutedColor
        textPaint.textSize = sp(9f)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, rect.left, rect.top, textPaint)
        textPaint.color = textColor
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, rect.right, rect.top, textPaint)
        fill.color = ColorUtils.setAlphaComponent(textColor, 30)
        canvas.drawRoundRect(rect.left, rect.top + dp(4f), rect.right, rect.top + dp(7f), dp(2f), dp(2f), fill)
        fill.color = color
        canvas.drawRoundRect(
            rect.left, rect.top + dp(4f), rect.left + rect.width() * fraction.coerceIn(0f, 1f),
            rect.top + dp(7f), dp(2f), dp(2f), fill
        )
    }

    private fun sample() {
        if (width == 0) return
        val next = reader.read(prefs.vitalsCpu)
        val animate = Animations.enabled(prefs)
        history.add(next)
        saveHistory()
        val oldBattery = displayBattery
        val oldMemory = displayMemory
        val oldStorage = displayStorage
        snapshot = next
        state = VitalsMath.state(next)
        stateColor = when (state) {
            VitalsState.NORMAL -> accent
            VitalsState.WARM -> WARM
            VitalsState.CRITICAL -> CRITICAL
        }
        displayBattery = next.batteryPercent.toFloat()
        displayMemory = next.memoryRatio * 100f
        displayStorage = next.storageRatio * 100f
        updateSummary()
        if (!animate) {
            invalidate()
        } else {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                addUpdateListener {
                    val p = it.animatedValue as Float
                    displayBattery = oldBattery + (next.batteryPercent - oldBattery) * p
                    displayMemory = oldMemory + (next.memoryRatio * 100f - oldMemory) * p
                    displayStorage = oldStorage + (next.storageRatio * 100f - oldStorage) * p
                    invalidate()
                }
                start()
            }
        }
    }

    private val sampleRunnable = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, refreshMs())
        }
    }

    private fun handleTap(x: Float, y: Float) {
        if (ringRect.contains(x, y)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
        } else if (memoryRect.contains(x, y) || storageRect.contains(x, y)) {
            runCatching { context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
        }
    }

    @Suppress("ReturnCount")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                longPressFired = false
                handler.postDelayed(longPressCheck, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPressCheck)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressCheck)
                if (!longPressFired && !moved) handleTap(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressCheck)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun refreshTheme() {
        val resolved = Styles.resolve(context, prefs).of(SurfaceId.CARDS)
        surface = ColorUtils.setAlphaComponent(resolved.surface, 255)
        textColor = resolved.text
        mutedColor = ColorUtils.setAlphaComponent(resolved.text, 150)
        accent = resolved.accent
        stateColor = when (state) {
            VitalsState.NORMAL -> accent
            VitalsState.WARM -> WARM
            VitalsState.CRITICAL -> CRITICAL
        }
    }

    private fun updateSummary() {
        contentDescription = "Battery ${snapshot.batteryPercent}%, ${VitalsMath.formatTemp(snapshot.batteryTempC)}, " +
            "memory ${snapshot.memoryUsed / GB} of ${snapshot.memoryTotal / GB} GB, " +
            "storage ${snapshot.storageUsed / GB} of ${snapshot.storageTotal / GB} GB"
    }

    private fun loadHistory() {
        val temp = store.getString(KEY_TEMP, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
        val cpu = store.getString(KEY_CPU, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
        val memory = store.getString(KEY_MEM, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
        if (temp != null && cpu != null && memory != null) {
            history.restore(temp, cpu, memory)
        }
    }

    private fun saveHistory() {
        store.edit()
            .putString(KEY_TEMP, history.temperature().joinToString(","))
            .putString(KEY_CPU, history.cpu().joinToString(","))
            .putString(KEY_MEM, history.memory().joinToString(","))
            .apply()
    }

    private fun refreshMs(): Long = prefs.vitalsRefreshSec.coerceIn(VitalsMath.REFRESH_MIN_SEC, VitalsMath.REFRESH_MAX_SEC) * 1000L

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        private const val HISTORY_SIZE = 60
        private const val GB = 1_073_741_824L
        private const val STORE = "supernova_vitals"
        private const val KEY_TEMP = "temperature"
        private const val KEY_CPU = "cpu"
        private const val KEY_MEM = "memory"
        private const val WARM = 0xFFFFB74D.toInt()
        private const val CRITICAL = 0xFFFF5252.toInt()
    }
}
