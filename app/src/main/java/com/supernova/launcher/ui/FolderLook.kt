package com.supernova.launcher.ui

import com.supernova.launcher.data.LauncherPrefs

// A folder card's own shadow and border. The shadow is the card's elevation
// (its spread) tinted to a colour; the border is a stroke on the card.
data class FolderLook(
    val shadow: Boolean = true,
    val shadowSpreadDp: Int = 8,
    val shadowColor: String = "",
    val borderWidthDp: Int = 0,
    val borderColor: String = ""
)

// Used when no colour is stored: a soft black shadow and a faint white edge,
// which read on both the light and the dark folder surface.
const val FOLDER_SHADOW_COLOR_DEFAULT = 0x66000000
const val FOLDER_BORDER_COLOR_DEFAULT = 0x33FFFFFF

// What a folder's card looks like. A folder opened inside another folder can
// take its own look (the subfolder controls), so the two are told apart.
fun folderLookOf(prefs: LauncherPrefs, nested: Boolean): FolderLook {
    val distinct = nested && prefs.subfolderDistinct
    return FolderLook(
        shadow = prefs.folderShadow,
        shadowSpreadDp = if (distinct) prefs.subfolderShadowSpread else prefs.folderShadowSpread,
        shadowColor = if (distinct) prefs.subfolderShadowColor else prefs.folderShadowColor,
        borderWidthDp = if (distinct) prefs.subfolderBorderWidth else prefs.folderBorderWidth,
        borderColor = if (distinct) prefs.subfolderBorderColor else prefs.folderBorderColor
    )
}
