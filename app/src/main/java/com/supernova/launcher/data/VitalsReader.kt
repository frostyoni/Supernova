@file:Suppress("MagicNumber")

package com.supernova.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock

class VitalsReader(private val context: Context) {
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    fun read(cpuEnabled: Boolean): VitalsSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val percent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: battery?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level * 100 / scale
            } ?: 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)
        val storage = StatFs(Environment.getDataDirectory().path)
        val storageTotal = storage.totalBytes
        val storageFree = storage.availableBytes
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        } else {
            0
        }
        return VitalsSnapshot(
            batteryPercent = percent,
            charging = charging,
            batteryTempC = temp,
            chargeTimeMs = batteryManager?.computeChargeTimeRemaining() ?: -1L,
            memoryUsed = (memoryInfo.totalMem - memoryInfo.availMem).coerceAtLeast(0L),
            memoryTotal = memoryInfo.totalMem.coerceAtLeast(1L),
            storageUsed = (storageTotal - storageFree).coerceAtLeast(0L),
            storageTotal = storageTotal.coerceAtLeast(1L),
            uptimeMs = SystemClock.elapsedRealtime(),
            cpuPercent = if (cpuEnabled) readCpu() else 0f,
            thermalStatus = thermal
        )
    }

    @Suppress("ReturnCount")
    private fun readCpu(): Float {
        val line = runCatching {
            java.io.File("/proc/stat").useLines { it.firstOrNull() }
        }.getOrNull() ?: return 0f
        val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return 0f
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        val total = parts.sum()
        val totalDelta = total - lastCpuTotal
        val idleDelta = idle - lastCpuIdle
        lastCpuTotal = total
        lastCpuIdle = idle
        if (totalDelta <= 0L) return 0f
        return ((totalDelta - idleDelta).toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
    }
}
