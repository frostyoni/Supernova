package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VitalsTest {
    @Test
    fun stateUsesTemperatureBatteryAndThermal() {
        assertEquals(VitalsState.NORMAL, VitalsMath.state(VitalsSnapshot(batteryPercent = 80, batteryTempC = 33f)))
        assertEquals(VitalsState.WARM, VitalsMath.state(VitalsSnapshot(batteryPercent = 80, batteryTempC = 41f)))
        assertEquals(VitalsState.CRITICAL, VitalsMath.state(VitalsSnapshot(batteryPercent = 80, batteryTempC = 46f)))
        assertEquals(VitalsState.CRITICAL, VitalsMath.state(VitalsSnapshot(batteryPercent = 8)))
        assertEquals(VitalsState.WARM, VitalsMath.state(VitalsSnapshot(batteryPercent = 80, cpuPercent = 92f)))
    }

    @Test
    fun formatsStayShort() {
        assertEquals("0m", VitalsMath.formatUptime(0))
        assertEquals("1h 1m", VitalsMath.formatUptime(61 * 60_000L))
        assertEquals("2d 3h", VitalsMath.formatUptime((2 * 24 + 3) * 60 * 60_000L))
        assertEquals("24m", VitalsMath.formatChargeTime(24 * 60_000L))
        assertEquals("1h 3m", VitalsMath.formatChargeTime(63 * 60_000L))
    }

    @Test
    fun historyKeepsTheLatestSamples() {
        val history = VitalsHistory(capacity = 3)
        history.add(VitalsSnapshot(batteryTempC = 30f, cpuPercent = 10f, memoryUsed = 10, memoryTotal = 100))
        history.add(VitalsSnapshot(batteryTempC = 31f, cpuPercent = 20f, memoryUsed = 20, memoryTotal = 100))
        history.add(VitalsSnapshot(batteryTempC = 32f, cpuPercent = 30f, memoryUsed = 30, memoryTotal = 100))
        history.add(VitalsSnapshot(batteryTempC = 33f, cpuPercent = 40f, memoryUsed = 40, memoryTotal = 100))
        assertEquals(listOf(31f, 32f, 33f), history.temperature().toList())
        assertEquals(3, history.samples())
        assertTrue(history.cpu().last() == 40f)
    }
}
