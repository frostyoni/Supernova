package com.supernova.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.content.res.Configuration
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.supernova.launcher.data.GlassPainter
import com.supernova.launcher.data.IconGlass
import com.supernova.launcher.data.WallpaperBlur
import kotlin.math.roundToInt

// A liquid-glass surface: the blurred wallpaper in a rounded pane, a veil over
// it so a label stays readable, then the pane's sheen and glow and its lit
// rim - the same pane an icon gets, so a surface and an icon match.
//
// This is an optical approximation, not a backdrop capture. Where the system
// still hands the wallpaper over (Android 12 and below) it is blurred once at
// an eighth a side (WallpaperBlur) and scaled to fill the bounds; on 13 and up
// the wallpaper cannot be read, so the pane falls back to a soft gradient of
// the wallpaper's own colours, which keeps the surface tied to the wallpaper.
// Either way it is not the strip actually behind the pane, and anything drawn
// over the wallpaper (an icon, a widget, another page) is not in it. It reads
// as frosted glass; it does not refract or bend what passes under its edges.
//
// Painted once per size into a bitmap, as GlassPillDrawable is, so a frame
// draws one bitmap. One call: LiquidGlassDrawable(context, IconGlass.CLEAR,
// radius = 24f * density), then alpha, radius or invalidateGlass() as wanted.
class LiquidGlassDrawable @JvmOverloads constructor(
    context: Context,
    private val glass: IconGlass = IconGlass.CLEAR,
    radius: Float = 0f,
    private val veilColor: Int = Color.WHITE,
    private val veilAlpha: Float = DEFAULT_VEIL_ALPHA
) : Drawable() {

    private val application = context.applicationContext
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Sampling the wallpaper into the cache keeps its own paint: a colour
    // filter or alpha on the blit must not be applied again here.
    private val sample = Paint(Paint.FILTER_BITMAP_FLAG)
    private val night = (application.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // White over a light wallpaper, black over a dark one: a white veil
        // in dark mode made the pane a flat pale rectangle.
        val colour = if (night) Color.BLACK else veilColor
        val alpha = (if (night) NIGHT_VEIL_ALPHA else veilAlpha).coerceIn(0f, 1f)
        color = ColorUtils.setAlphaComponent(colour, (alpha * OPAQUE).roundToInt())
    }
    private var cache: Bitmap? = null
    private var cachedGeneration = -1

    // Blurring the wallpaper costs a frame's worth on the first draw; warm it
    // where the system still gives it and repaint once it lands. On 13 and up
    // this is a no-op and the colour wash draws at once.
    init {
        WallpaperBlur.warm(application) { invalidateGlass() }
    }

    // In px, as GlassPillDrawable's: a caller passes dp * density. Setting it
    // drops the painted bitmap, and the surface paints again at the next draw.
    var radius: Float = radius
        set(value) {
            if (field == value) return
            field = value
            invalidateGlass()
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

    // A new wallpaper: paint the surface again from it. A draw does this by
    // itself once WallpaperBlur reports a new one; this is for a caller that
    // wants the surface repainted now (a theme change, a test).
    fun invalidateGlass() {
        cache?.recycle()
        cache = null
        cachedGeneration = -1
        invalidateSelf()
    }

    private fun render(width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        // Read before the pane is painted, so the generation stamped below is
        // the one this picture came from.
        val wallpaper = WallpaperBlur.get(application)
        val density = application.resources.displayMetrics.density
        val smoked = night || glass == com.supernova.launcher.data.IconGlass.SMOKED
        com.supernova.launcher.data.GlassOptics.draw(
            canvas,
            width,
            height,
            com.supernova.launcher.data.GlassOptics.Look(
                radiusPx = cornerRadius(width, height),
                thicknessPx = 14f * density,
                blurPx = 9f * density,
                tint = if (smoked) Color.BLACK else Color.WHITE,
                density = if (smoked) 0.62f else 0.28f,
                fresnel = if (smoked) 0.4f else 0.6f,
                specular = if (smoked) 0.3f else 0.45f
            ),
            wallpaper ?: colourField()
        )
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
        val field = createBitmap(FIELD_SIZE, FIELD_SIZE)
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

    // The blurred wallpaper, centre-cropped to fill the pane. Without a
    // position there is no truer mapping: the sample is the middle of the
    // wallpaper, whatever the pane sits over.
    private fun drawWallpaper(canvas: Canvas, source: Bitmap, width: Int, height: Int) {
        val src = Rect(0, 0, source.width, source.height)
        if (source.width * height > width * source.height) {
            val kept = (source.height * width / height).coerceAtLeast(1)
            val inset = (source.width - kept) / 2
            src.left = inset
            src.right = inset + kept
        } else {
            val kept = (source.width * height / width).coerceAtLeast(1)
            val inset = (source.height - kept) / 2
            src.top = inset
            src.bottom = inset + kept
        }
        canvas.drawBitmap(source, src, Rect(0, 0, width, height), sample)
    }

    // Android 13 and up refuse the wallpaper's pixels, so the source becomes
    // the colours the system still reports for it: a quiet two-way wash rather
    // than a picture. Kept soft and low so the pane and rim still lead.
    private fun drawWallpaperColours(canvas: Canvas, width: Int, height: Int) {
        val colours = runCatching {
            WallpaperManager.getInstance(application).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()
        val primary = colours?.primaryColor?.toArgb() ?: FALLBACK_PRIMARY
        val secondary = colours?.secondaryColor?.toArgb() ?: primary
        val tertiary = colours?.tertiaryColor?.toArgb() ?: secondary
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(soft(primary), soft(secondary), soft(tertiary)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wash)
    }

    private fun soft(color: Int): Int =
        ColorUtils.setAlphaComponent(color, (COLOUR_WASH * OPAQUE).roundToInt())

    // One diagonal band of light across the pane: the bit the eye reads as a
    // glossy surface rather than a tinted rectangle.
    private fun drawSpecular(canvas: Canvas, width: Int, height: Int) {
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width * 0.85f, height * 0.6f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(Color.WHITE, (SPECULAR * OPAQUE).roundToInt()),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gloss)
    }

    private fun cornerRadius(width: Int, height: Int): Float =
        radius.coerceIn(0f, minOf(width, height) / 2f)

    // The surface's outline, for a ripple and the view's clip.
    override fun getOutline(outline: Outline) {
        outline.setRoundRect(
            bounds, radius.coerceIn(0f, minOf(bounds.width(), bounds.height()) / 2f)
        )
    }

    override fun setAlpha(alpha: Int) {
        if (paint.alpha == alpha) return
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        // Stateless apart from its grain tile, which every pane can share.
        val painter = GlassPainter()

        const val OPAQUE = 255

        // White at 20%: enough for a label to read on a bright wallpaper,
        // little enough that the blur still shows.
        const val DEFAULT_VEIL_ALPHA = 0.20f

        // A neutral wash when the system reports no wallpaper colours either.
        const val FALLBACK_PRIMARY = 0xFF4A5A6A.toInt()
        const val COLOUR_WASH = 0.62f
        const val FIELD_SIZE = 64
        const val NIGHT_VEIL_ALPHA = 0.30f
        const val SPECULAR = 0.09f
    }
}
