package com.supernova.launcher.data

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

// Glass as optics rather than as a stack of gradients.
//
// A pane is a rounded slab: a signed-distance field gives the edge, its
// gradient gives a bevel normal, and that normal does the three things glass
// actually does - bends what is behind it (refraction), catches a highlight
// (specular), and brightens at grazing angles (Fresnel). Colour comes from
// absorption through the slab, not from a flat tint, and the rim disperses
// slightly so the edge reads as a prism rather than a stroke.
//
// All of it is one AGSL pass (Android 13+) over a backdrop texture, a dozen
// samples per pixel. Below 13 the same look is approximated on the Canvas.
//
// The backdrop is the honest limit: Android 13 and up will not hand a normal
// app the wallpaper's pixels. Where the system's own blur-behind is available
// (a full-screen surface) the pane stays translucent and lets it through;
// otherwise the caller supplies a bitmap - the wallpaper where it can be read,
// or a field built from the wallpaper's colours, which the system does give.
object GlassOptics {

    class Look(
        val radiusPx: Float,
        // How far the bevel reaches into the pane, and how hard it bends.
        val thicknessPx: Float = 14f,
        val blurPx: Float = 9f,
        val tint: Int = Color.WHITE,
        // Absorption per unit of thickness: higher is a deeper colour.
        val density: Float = 0.5f,
        val lightX: Float = -0.45f,
        val lightY: Float = -0.75f,
        val fresnel: Float = 0.55f,
        val specular: Float = 0.4f,
        val dispersionPx: Float = 1.6f,
        // 1 = the backdrop texture is the pane's body; 0 = the pane is a
        // translucent sheet over whatever the system has already blurred.
        val backdropMix: Float = 1f,
        val alpha: Float = 1f
    )

    fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private const val SOURCE = """
uniform shader backdrop;
uniform float2 size;
uniform float radius;
uniform float thickness;
uniform float blur;
uniform float3 tint;
uniform float density;
uniform float2 light;
uniform float fresnel;
uniform float specular;
uniform float dispersion;
uniform float backdropMix;
uniform float alpha;

// 'half' is a type name in AGSL, so the half-extent is b.
float slab(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float3 tap(float2 uv) {
    return float3(backdrop.eval(clamp(uv, float2(0.5), size - 0.5)).rgb);
}

half4 main(float2 coord) {
    float2 b = size * 0.5;
    float2 p = coord - b;
    float d = slab(p, b, radius);
    if (d > 0.5) {
        return half4(0.0);
    }

    float e = 1.0;
    float2 grad = float2(
        slab(p + float2(e, 0.0), b, radius) - slab(p - float2(e, 0.0), b, radius),
        slab(p + float2(0.0, e), b, radius) - slab(p - float2(0.0, e), b, radius)
    );
    float2 n2 = grad / max(length(grad), 0.0001);
    float inside = clamp(-d / max(thickness, 1.0), 0.0, 1.0);
    float bend = 1.0 - inside;
    float3 N = normalize(float3(n2 * bend, 1.0));

    float2 uv = coord + N.xy * thickness * bend;
    float3 body = tap(uv) * 0.28;
    body += tap(uv + float2(blur, 0.0)) * 0.09;
    body += tap(uv + float2(-blur, 0.0)) * 0.09;
    body += tap(uv + float2(0.0, blur)) * 0.09;
    body += tap(uv + float2(0.0, -blur)) * 0.09;
    body += tap(uv + float2(blur, blur) * 0.7071) * 0.09;
    body += tap(uv + float2(-blur, blur) * 0.7071) * 0.09;
    body += tap(uv + float2(blur, -blur) * 0.7071) * 0.09;
    body += tap(uv + float2(-blur, -blur) * 0.7071) * 0.09;

    float2 ca = n2 * dispersion * bend;
    body.r = mix(body.r, tap(uv + ca).r, 0.65);
    body.b = mix(body.b, tap(uv - ca).b, 0.65);

    float3 absorb = exp(-density * (0.35 + bend) * (float3(1.0) - tint));
    float3 col = body * absorb;

    float f = pow(1.0 - N.z, 5.0) * fresnel;
    float3 L = normalize(float3(light, 0.55));
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(N, H), 0.0), 64.0) * specular;
    col += float3(f * 0.9 + spec);
    col += float3(smoothstep(1.5, 0.0, -d) * 0.35);

    float aa = clamp(0.5 - d, 0.0, 1.0);
    float a = mix(alpha * 0.55, alpha, backdropMix) * aa;
    return half4(half3(col * a), half(a));
}
"""

    // Draws the pane into width x height at the origin. `backdrop` may be
    // null, in which case the pane is a translucent sheet (backdropMix 0).
    fun draw(canvas: Canvas, width: Int, height: Int, look: Look, backdrop: Bitmap?) {
        if (width <= 0 || height <= 0) return
        if (!available() || backdrop == null && look.backdropMix > 0f) {
            CanvasGlass.draw(canvas, width, height, look, backdrop)
            return
        }
        val shader = runCatching { RuntimeShader(SOURCE) }
            .onFailure { android.util.Log.w("GlassOptics", "glass program rejected", it) }
            .getOrNull()
        if (shader == null) {
            CanvasGlass.draw(canvas, width, height, look, backdrop)
            return
        }
        val source = backdrop ?: neutral(width, height)
        // The shader samples in pane coordinates; the backdrop is usually a
        // small blurred or synthetic field, so map one onto the other.
        val texture = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(source.width / width.toFloat(), source.height / height.toFloat())
                }
            )
        }
        shader.setInputShader("backdrop", texture)
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("radius", min(look.radiusPx, min(width, height) / 2f))
        shader.setFloatUniform("thickness", max(1f, look.thicknessPx))
        shader.setFloatUniform("blur", max(0.5f, look.blurPx))
        shader.setFloatUniform(
            "tint",
            Color.red(look.tint) / 255f,
            Color.green(look.tint) / 255f,
            Color.blue(look.tint) / 255f
        )
        shader.setFloatUniform("density", look.density)
        shader.setFloatUniform("light", look.lightX, look.lightY)
        shader.setFloatUniform("fresnel", look.fresnel)
        shader.setFloatUniform("specular", look.specular)
        shader.setFloatUniform("dispersion", look.dispersionPx)
        shader.setFloatUniform("backdropMix", if (backdrop == null) 0f else look.backdropMix)
        shader.setFloatUniform("alpha", look.alpha.coerceIn(0f, 1f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        // A driver that rejects the program at draw time falls back too.
        val drawn = runCatching {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }.isSuccess
        if (!drawn) CanvasGlass.draw(canvas, width, height, look, backdrop)
    }

    private fun neutral(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }

    // Below Android 13, or with no backdrop: the same reading of glass built
    // from what the Canvas can do - a body, a bevel-lit rim, a highlight.
    private object CanvasGlass {
        fun draw(canvas: Canvas, width: Int, height: Int, look: Look, backdrop: Bitmap?) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = min(look.radiusPx, min(width, height) / 2f)
            val path = android.graphics.Path().apply {
                addRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    radius, radius, android.graphics.Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(path)
            if (backdrop != null) {
                canvas.drawBitmap(backdrop, null, android.graphics.Rect(0, 0, width, height), paint)
            }
            paint.shader = null
            paint.color = ColorUtils.setAlphaComponent(
                look.tint,
                (look.alpha * 0.32f * 255).toInt().coerceIn(0, 255)
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.BLACK
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, width * 0.4f, height * 0.5f,
                ColorUtils.setAlphaComponent(Color.WHITE, (look.specular * 160).toInt().coerceIn(0, 255)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, look.thicknessPx * 0.16f)
            paint.color = ColorUtils.setAlphaComponent(
                Color.WHITE,
                (look.fresnel * 190).toInt().coerceIn(0, 255)
            )
            canvas.drawRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint
            )
            canvas.restore()
        }
    }
}
