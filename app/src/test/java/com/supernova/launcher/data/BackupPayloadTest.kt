package com.supernova.launcher.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupPayloadTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun payload(spec: String?, presets: String? = null) = BackupPayload(
        exportedAtEpochMs = 1L,
        model = LauncherModel(),
        prefs = LauncherPrefs(),
        customTransition = spec,
        customTransitionPresets = presets
    )

    @Test
    fun theTransitionComesBackWithTheLayout() {
        val spec = CustomTransition.encode(TransitionPresets.pop)
        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload(spec)))
        assertEquals(spec, decoded.customTransition)
    }

    // A backup written before the feature has no field at all, and must not
    // wipe a transition the user has since made.
    @Test
    fun anOlderBackupLeavesTheTransitionAlone() {
        val decoded = json.decodeFromString<BackupPayload>(
            """{"exportedAtEpochMs":1,"model":{},"prefs":{}}"""
        )
        assertNull(decoded.customTransition)
    }

    @Test
    fun theSavedPresetsComeBackToo() {
        val presets = CustomTransition.encodeList(listOf(TransitionPresets.pop, TransitionPresets.spin))
        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload("", presets)))
        assertEquals(presets, decoded.customTransitionPresets)
    }

    @Test
    fun anOlderBackupLeavesThePresetsAlone() {
        val decoded = json.decodeFromString<BackupPayload>(
            """{"exportedAtEpochMs":1,"model":{},"prefs":{}}"""
        )
        assertNull(decoded.customTransitionPresets)
    }

    @Test
    fun blankMeansThereWasNoneToRestore() {
        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload("")))
        assertEquals("", decoded.customTransition)
    }
}
