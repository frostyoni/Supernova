package com.supernova.launcher.data

object NativeWidgets {
    const val DEVICE_STATUS = "device_status"

    fun isNative(item: LauncherItem): Boolean = item.nativeWidgetKind.isNotEmpty()

    fun defaultSpanX(kind: String): Int = when (kind) {
        DEVICE_STATUS -> 2
        else -> 2
    }

    fun defaultSpanY(kind: String): Int = when (kind) {
        DEVICE_STATUS -> 2
        else -> 2
    }
}
