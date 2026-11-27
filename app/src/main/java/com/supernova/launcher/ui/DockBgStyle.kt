package com.supernova.launcher.ui

import com.supernova.launcher.data.SettingEnumSerializer

@kotlinx.serialization.Serializable(with = DockBgStyle.Names::class)
// Liquid glass is the wallpaper's colours under the same pane and rim the
// icons get, because the system wallpaper itself cannot be read on Android 13
// and up. SQUARE is what a dock set before the switch keeps until its radius
// is changed, and draws at radius 0.
enum class DockBgStyle {
    NONE,
    ROUNDED,
    LIQUID,
    SQUARE;

    // Its name in the store and in a backup, read back leniently.
    // Pill was removed; a dock that had it keeps a background, rounded.
    object Names : SettingEnumSerializer<DockBgStyle>(
        "DockBgStyle", entries, NONE, aliases = mapOf("PILL" to ROUNDED)
    )

    companion object {
        fun fromName(name: String): DockBgStyle = Names.parse(name)
    }
}
