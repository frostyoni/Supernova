@file:Suppress("MagicNumber")

package com.supernova.launcher.data

import kotlin.math.roundToInt

enum class VitalsState { NORMAL, WARM, CRITICAL }

data class VitalsSnapshot(
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val batteryTempC: Float = 0f,
    val chargeTimeMs: Long = -1L,
    val memoryUsed: Long = 0L,
    val memoryTotal: Long = 1L,
    val storageUsed: Long = 0L,
    val storageTotal: Long = 1L,
    val uptimeMs: Long = 0L,
    val cpuPercent: Float = 0f,
    val thermalStatus: Int = 0
) {
    val memoryRatio: Float get() = memoryUsed.toFloat() / memoryTotal.coerceAtLeast(1L)
    val storageRatio: Float get() = storageUsed.toFloat() / storageTotal.coerceAtLeast(1L)
}

object VitalsMath {
    const val REFRESH_MIN_SEC = 5
    const val REFRESH_MAX_SEC = 300

    fun state(snapshot: VitalsSnapshot): VitalsState = when {
        snapshot.batteryPercent in 1..10 -> VitalsState.CRITICAL
        snapshot.batteryTempC >= 45f -> VitalsState.CRITICAL
        snapshot.thermalStatus >= THERMAL_SEVERE -> VitalsState.CRITICAL
        snapshot.batteryTempC >= 40f -> VitalsState.WARM
        snapshot.thermalStatus >= THERMAL_MODERATE -> VitalsState.WARM
        snapshot.cpuPercent >= 90f -> VitalsState.WARM
        snapshot.memoryRatio >= 0.9f -> VitalsState.WARM
        snapshot.storageRatio >= 0.95f -> VitalsState.WARM
        else -> VitalsState.NORMAL
    }

    fun formatUptime(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L)
        val days = minutes / (24 * 60)
        val hours = (minutes / 60) % 24
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 10) "${gb.roundToInt()} GB" else String.format(java.util.Locale.ROOT, "%.1f GB", gb)
    }

    fun formatTemp(celsius: Float): String = String.format(java.util.Locale.ROOT, "%.1f °C", celsius)

    fun formatPercent(value: Float): String = "${value.roundToInt()}%"

    fun formatChargeTime(ms: Long): String? {
        if (ms <= 0L) return null
        val minutes = (ms / 60_000L).coerceAtLeast(1L)
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    fun memoryRatio(snapshot: VitalsSnapshot): Float = snapshot.memoryRatio.coerceIn(0f, 1f)

    fun storageRatio(snapshot: VitalsSnapshot): Float = snapshot.storageRatio.coerceIn(0f, 1f)

    const val THERMAL_MODERATE = 3
    const val THERMAL_SEVERE = 4
}

class VitalsHistory(private val capacity: Int = 60) {
    private val temperature = FloatArray(capacity)
    private val cpu = FloatArray(capacity)
    private val memory = FloatArray(capacity)
    private var filled = 0

    fun add(snapshot: VitalsSnapshot) {
        if (filled == 0) {
            temperature.fill(snapshot.batteryTempC)
            cpu.fill(snapshot.cpuPercent)
            memory.fill(snapshot.memoryRatio * 100f)
        } else {
            shift(temperature, snapshot.batteryTempC)
            shift(cpu, snapshot.cpuPercent)
            shift(memory, snapshot.memoryRatio * 100f)
        }
        if (filled < capacity) filled++
    }

    fun restore(temperature: FloatArray, cpu: FloatArray, memory: FloatArray): Boolean {
        if (temperature.size != capacity || cpu.size != capacity || memory.size != capacity) return false
        temperature.copyInto(this.temperature, 0, 0, capacity)
        cpu.copyInto(this.cpu, 0, 0, capacity)
        memory.copyInto(this.memory, 0, 0, capacity)
        filled = capacity
        return true
    }

    fun temperature(): FloatArray = series(temperature)
    fun cpu(): FloatArray = series(cpu)
    fun memory(): FloatArray = series(memory)
    fun samples(): Int = filled

    private fun shift(values: FloatArray, value: Float) {
        for (i in 0 until capacity - 1) values[i] = values[i + 1]
        values[capacity - 1] = value
    }

    private fun series(values: FloatArray): FloatArray {
        if (filled == 0) return FloatArray(capacity) { values[capacity - 1] }
        return values.copyOf()
    }
}
