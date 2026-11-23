package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransitionPresetsTest {

    @Test
    fun everyPresetIsValid() {
        TransitionPresets.all.forEach { preset ->
            assertNotNull(preset.validated(), "${preset.name} did not validate")
            assertTrue(preset.name.isNotBlank(), "a preset needs a name")
        }
    }

    // The rule the builder's hint states: a lane is identity at rest, so a
    // page never snaps as it becomes the one in view.
    @Test
    fun everyLaneIsIdentityAtRest() {
        TransitionPresets.all.forEach { preset ->
            listOf(true, false).forEach { incoming ->
                val frame = preset.sample(incomingLane = incoming, progress = 0f)
                val where = "${preset.name} ${if (incoming) "incoming" else "outgoing"}"
                assertEquals(0f, frame.moveX, 0.0001f, "$where moveX")
                assertEquals(0f, frame.moveY, 0.0001f, "$where moveY")
                assertEquals(1f, frame.scale, 0.0001f, "$where scale")
                assertEquals(1f, frame.alpha, 0.0001f, "$where alpha")
                assertEquals(0f, frame.roll, 0.0001f, "$where roll")
                assertEquals(0f, frame.pitch, 0.0001f, "$where pitch")
                assertEquals(0f, frame.yaw, 0.0001f, "$where yaw")
            }
        }
    }

    @Test
    fun everyOutgoingLaneIsGoneByTheNeighbourSlot() {
        TransitionPresets.all.forEach { preset ->
            assertEquals(0f, preset.sample(incomingLane = false, progress = 1f).alpha, 0.0001f, preset.name)
        }
    }

    @Test
    fun aSpecStillMatchesItsPresetUntilEdited() {
        val preset = TransitionPresets.pop
        assertEquals(preset, TransitionPresets.matching(preset))
        val edited = preset.copy(name = "Mine")
        assertEquals(null, TransitionPresets.matching(edited))
    }
}
