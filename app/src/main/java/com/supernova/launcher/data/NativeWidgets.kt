package com.supernova.launcher.data

object NativeWidgets {
    const val DEVICE_STATUS = "device_status"

    fun isNative(item: LauncherItem): Boolean = item.nativeWidgetKind.isNotEmpty()

    private const val WIDE = 4
    private const val TALL = 2
    private const val SINGLE = 1

    fun defaultSpanX(kind: String): Int = if (kind == DEVICE_STATUS) WIDE else WIDE
    fun defaultSpanY(kind: String): Int = if (kind == DEVICE_STATUS) TALL else TALL
    fun minSpanX(kind: String): Int = if (kind == DEVICE_STATUS) WIDE else SINGLE
    fun minSpanY(kind: String): Int = if (kind == DEVICE_STATUS) TALL else SINGLE
}
