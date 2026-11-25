package com.supernova.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.supernova.launcher.data.GlassOptics
import com.supernova.launcher.data.WallpaperBlur
import kotlin.math.min
import kotlin.math.roundToInt

// A rounded pane drawn with GlassOptics. The optics, the backdrop choice and
// the corner radius all come from GlassPrefs, so the dock and the settings
// preview share one look. Where the wallpaper's pixels cannot be read, or the
// backdrop is off, the optics use their Canvas reading.
//
// The backdrop source is the honest limit: Android 13 and up will not hand a
// normal app the wallpaper, so Auto uses a soft field of the colours the
// system still reports; up to 12 it uses the wallpaper, blurred once.
class LiquidGlassDrawable(
    context: Context,
    private val look: GlassOptics.Look,
    private val backdrop: GlassBackdrop = GlassBackdrop.AUTO,
    // Low-spec: a plain painted pane, no shader and no wallpaper sampling.
    private val lowSpec: Boolean = false
) : Drawable() {

    private val application = context.applicationContext
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cache: Bitmap? = null
    private var cachedGeneration = -1

    // Blurring the wallpaper costs a frame's worth on the first draw; warm it
    // where the system still gives it and repaint once it lands.
    init {
        if (!lowSpec && backdrop == GlassBackdrop.AUTO) {
            WallpaperBlur.warm(application) { invalidateGlass() }
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val bitmap = cache?.takeIf {
            it.width == bounds.width() && it.height == bounds.height() &&
                cachedGeneration == WallpaperBlur.generation
        } ?: render(bounds.width(), bounds.height()).also {
            cache?.recycle()
            cache = it
        }
        canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), paint)
    }

    // A new wallpaper or a changed look: paint the surface again.
    fun invalidateGlass() {
        cache?.recycle()
        cache = null
        cachedGeneration = -1
        invalidateSelf()
    }

    private fun render(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val source = when (backdrop) {
            GlassBackdrop.NONE -> null
            GlassBackdrop.COLOURS -> colourField()
            GlassBackdrop.AUTO ->
                if (lowSpec) null else WallpaperBlur.get(application) ?: colourField()
        }
        val optics = look.copy(
            radiusPx = look.radiusPx.coerceIn(0f, min(width, height) / 2f)
        )
        GlassOptics.draw(canvas, width, height, optics, source)
        cachedGeneration = WallpaperBlur.generation
        return bitmap
    }

    // Android 13 and up will not hand over the wallpaper's pixels, but it does
    // report its colours. A soft field of them gives the optics something to
    // bend and blur, so the pane reads as glass over the wallpaper's own light
    // rather than as a flat rectangle.
    private fun colourField(): Bitmap {
        val colours = runCatching {
            WallpaperManager.getInstance(application).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()
        val primary = colours?.primaryColor?.toArgb() ?: FALLBACK_PRIMARY
        val secondary = colours?.secondaryColor?.toArgb() ?: primary
        val tertiary = colours?.tertiaryColor?.toArgb() ?: secondary
        val field = Bitmap.createBitmap(FIELD_SIZE, FIELD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(field)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, FIELD_SIZE.toFloat(), FIELD_SIZE.toFloat(),
            primary, secondary, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, FIELD_SIZE.toFloat(), FIELD_SIZE.toFloat(), paint)
        paint.shader = android.graphics.RadialGradient(
            FIELD_SIZE * 0.28f, FIELD_SIZE * 0.24f, FIELD_SIZE * 0.7f,
            ColorUtils.setAlphaComponent(tertiary, 200), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, FIELD_SIZE.toFloat(), FIELD_SIZE.toFloat(), paint)
        paint.shader = android.graphics.RadialGradient(
            FIELD_SIZE * 0.8f, FIELD_SIZE * 0.85f, FIELD_SIZE * 0.6f,
            ColorUtils.setAlphaComponent(secondary, 170), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, FIELD_SIZE.toFloat(), FIELD_SIZE.toFloat(), paint)
        return field
    }

    override fun setAlpha(alpha: Int) {
        if (paint.alpha == alpha) return
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val FIELD_SIZE = 64
        const val FALLBACK_PRIMARY = 0xFF4A5A6A.toInt()
        const val OPAQUE = 255
    }
}
