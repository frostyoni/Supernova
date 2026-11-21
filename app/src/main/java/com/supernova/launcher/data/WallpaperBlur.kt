package com.supernova.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.createBitmap
import com.supernova.launcher.runLogged
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// The wallpaper as a glass surface shows it: read once from WallpaperManager,
// downscaled to an eighth a side, blurred, and kept as one bitmap.
//
// This is an optical approximation, not a backdrop capture. The picture is the
// wallpaper itself, pre-blurred, so whatever is drawn over the wallpaper - an
// icon, a widget, a folder - is not in it, and a pane that moves across the
// wallpaper keeps showing the same picture. It stands in for frosted glass; it
// does not refract, bend or sample what passes under an edge. Blurring once at
// a small size is also what keeps a frame cheap: no pane blurs anything.
//
// A launcher can read the wallpaper up to Android 12. From 13 the read needs
// all-files access, so get() returns null there and a pane falls back to a
// plain tint (IconGlass) - the surface it had before liquid glass.
//
// The picture is dropped, not recycled, when the wallpaper changes: a pane may
// still be drawing it.
object WallpaperBlur {

    // The wallpaper, one eighth a side. A 1080x2400 wallpaper becomes 135x300:
    // 160 KB, and soft enough that a pane scaling it up reads as glass.
    private const val SCALE = 8
    // Blur radius as a share of the small picture's shorter side, and its
    // floor, so a tiny picture still softens.
    private const val BLUR_FRACTION = 0.03f
    private const val MIN_BLUR = 2
    // Box passes: three approximate a gaussian closely enough to hide.
    private const val PASSES = 3
    private const val CHANNEL = 0xFF

    @Volatile
    var generation = 0
        private set

    @Volatile
    private var blurred: Bitmap? = null
    private var blurredGeneration = -1
    private var listening = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { task ->
        Thread(task, "wallpaper-blur").apply { isDaemon = true }
    }

    // The blurred wallpaper, or null when there is none to read. The first
    // call after a wallpaper change decodes and blurs, so keep it off a frame:
    // warm() does, and a pane's own render caches what it gets.
    @Synchronized
    fun get(context: Context): Bitmap? {
        val application = context.applicationContext
        listen(application)
        if (blurredGeneration == generation) return blurred
        val picture = runLogged("blur wallpaper") { build(application) }.getOrNull()
        blurred = picture
        blurredGeneration = generation
        return picture
    }

    // The same, off the main thread and ahead of the first pane; onReady runs
    // on the main thread, and only when there is a picture to show.
    fun warm(context: Context, onReady: () -> Unit = {}) {
        val application = context.applicationContext
        loader.execute {
            if (get(application) != null) mainHandler.post(onReady)
        }
    }

    // A new wallpaper: the next get() reads it again.
    fun invalidate() {
        generation++
    }

    // Wallpaper colour changes are reported for every new wallpaper that has
    // colours; a live one that has none leaves the picture stale until a
    // caller invalidates.
    private fun listen(context: Context) {
        if (listening) return
        listening = true
        runCatching {
            WallpaperManager.getInstance(context)
                .addOnColorsChangedListener(colorsChanged, mainHandler)
        }
    }

    private val colorsChanged = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) invalidate()
    }

    // The wallpaper at an eighth a side, blurred. Null when the system has no
    // wallpaper to give us, or refuses the read.
    private fun build(context: Context): Bitmap? {
        val wallpaper = WallpaperManager.getInstance(context).drawable
        val width = (wallpaper?.intrinsicWidth ?: 0) / SCALE
        val height = (wallpaper?.intrinsicHeight ?: 0) / SCALE
        if (wallpaper == null || width < 1 || height < 1) return null
        // Drawn into a bitmap of ours rather than scaled by createScaledBitmap,
        // which hands back the wallpaper's own bitmap when the sizes match: it
        // lives in the framework's cache and must not be recycled here.
        val small = createBitmap(width, height)
        val bounds = Rect(wallpaper.bounds)
        try {
            wallpaper.bounds = Rect(0, 0, width, height)
            wallpaper.draw(Canvas(small))
        } finally {
            wallpaper.bounds = bounds
        }
        val softened = blur(small)
        if (softened !== small) small.recycle()
        return softened
    }

    // The box blur is the whole path: three passes at an eighth a side are
    // cheap enough on the frame the wallpaper first lands, and it behaves the
    // same on every API level.
    private fun blur(source: Bitmap): Bitmap = boxBlur(source, radiusFor(source.width, source.height))

    private fun radiusFor(width: Int, height: Int): Int =
        (minOf(width, height) * BLUR_FRACTION).roundToInt().coerceAtLeast(MIN_BLUR)

    // Three passes each way, a running sum per channel, O(pixels) whatever the
    // radius. The window clamps at an edge, so the border does not darken.
    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(pixels.size)
        repeat(PASSES) {
            for (y in 0 until height) blurLine(pixels, scratch, y * width, 1, width, radius)
            for (x in 0 until width) blurLine(scratch, pixels, x, width, height, radius)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    // One row or column: start is its first index, step walks along it.
    private fun blurLine(
        source: IntArray,
        target: IntArray,
        start: Int,
        step: Int,
        length: Int,
        radius: Int
    ) {
        val span = radius * 2 + 1
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = source[start + offset.coerceIn(0, length - 1) * step]
            alpha += pixel ushr 24
            red += (pixel shr 16) and CHANNEL
            green += (pixel shr 8) and CHANNEL
            blue += pixel and CHANNEL
        }
        for (index in 0 until length) {
            target[start + index * step] = ((alpha / span) shl 24) or
                ((red / span) shl 16) or ((green / span) shl 8) or (blue / span)
            val leaving = source[start + (index - radius).coerceIn(0, length - 1) * step]
            val entering = source[start + (index + radius + 1).coerceIn(0, length - 1) * step]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and CHANNEL) - ((leaving shr 16) and CHANNEL)
            green += ((entering shr 8) and CHANNEL) - ((leaving shr 8) and CHANNEL)
            blue += (entering and CHANNEL) - (leaving and CHANNEL)
        }
    }
}
