package com.supernova.launcher.data

import android.graphics.Color
import com.supernova.launcher.ui.GlassBackdrop
import com.supernova.launcher.ui.GlassTone
import kotlin.math.cos
import kotlin.math.sin

// The glass look, from the settings rather than constants. The dock's Liquid
// style and its preview both resolve it here, so they cannot drift apart.
object GlassPrefs {

    class Resolved(val look: GlassOptics.Look, val backdrop: GlassBackdrop)

    fun resolve(
        prefs: LauncherPrefs,
        night: Boolean,
        radiusPx: Float,
        density: Float
    ): Resolved {
        val tone = when (prefs.glassTone) {
            GlassTone.AUTO -> if (night) IconGlass.SMOKED else IconGlass.FROSTED
            GlassTone.CLEAR -> IconGlass.CLEAR
            GlassTone.FROSTED -> IconGlass.FROSTED
            GlassTone.SMOKED -> IconGlass.SMOKED
        }
        val smoked = tone == IconGlass.SMOKED
        val angle = Math.toRadians(prefs.glassLight.toDouble())
        val look = GlassOptics.Look(
            radiusPx = radiusPx,
            thicknessPx = prefs.glassThickness * density,
            blurPx = prefs.glassBlur * density,
            tint = if (smoked) Color.BLACK else Color.WHITE,
            density = prefs.glassDensity / 100f,
            lightX = (cos(angle) * 0.8).toFloat(),
            lightY = (sin(angle) * 0.8).toFloat(),
            fresnel = prefs.glassFresnel / 100f,
            specular = prefs.glassSpecular / 100f,
            dispersionPx = prefs.glassDispersion * density
        )
        return Resolved(look, prefs.glassBackdrop)
    }
}
