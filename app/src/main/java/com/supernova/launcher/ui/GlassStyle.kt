package com.supernova.launcher.ui

import com.supernova.launcher.data.SettingEnumSerializer
import kotlinx.serialization.Serializable

// The glass surface's tone: Auto follows the theme, the others are explicit.
@Serializable(with = GlassTone.Names::class)
enum class GlassTone {
    AUTO, CLEAR, FROSTED, SMOKED;

    object Names : SettingEnumSerializer<GlassTone>("GlassTone", entries, AUTO)

    companion object {
        fun fromName(name: String): GlassTone = Names.parse(name)
    }
}

// What the glass is drawn over. Auto uses the wallpaper's pixels where they
// can be read (Android 12 and below) and a field of its colours above;
// Colours always paints the field; None leaves a plain painted pane.
@Serializable(with = GlassBackdrop.Names::class)
enum class GlassBackdrop {
    AUTO, COLOURS, NONE;

    object Names : SettingEnumSerializer<GlassBackdrop>("GlassBackdrop", entries, AUTO)

    companion object {
        fun fromName(name: String): GlassBackdrop = Names.parse(name)
    }
}
