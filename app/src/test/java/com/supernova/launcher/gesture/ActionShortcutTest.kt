package com.supernova.launcher.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionShortcutTest {

    @Test
    fun encodeDecodeRoundTripsAPayloadlessAction() {
        val uri = ActionShortcut.encode(GestureAction.OPEN_DRAWER)
        assertEquals("supernova://action/OPEN_DRAWER", uri)
        val decoded = ActionShortcut.decode(uri)
        assertEquals(GestureAction.OPEN_DRAWER, decoded?.action)
        assertNull(decoded?.payload)
    }

    @Test
    fun encodeDecodeCarriesAPayload() {
        val uri = ActionShortcut.encode(GestureAction.JUMP_TO_PAGE, "2")
        val decoded = ActionShortcut.decode(uri)
        assertEquals(GestureAction.JUMP_TO_PAGE, decoded?.action)
        assertEquals("2", decoded?.payload)
    }

    // The first item is position 0, and a payload of "0" is a payload.
    @Test
    fun folderItemCarriesItsPosition() {
        val decoded = ActionShortcut.decode(ActionShortcut.encode(GestureAction.LAUNCH_FOLDER_ITEM, "0"))
        assertEquals(GestureAction.LAUNCH_FOLDER_ITEM, decoded?.action)
        assertEquals("0", decoded?.payload)
    }

    // The folder is the icon the swipe is on, so neither the gesture pickers
    // nor the standalone action list can offer it where there is no folder.
    @Test
    fun folderItemIsOnlyOfferedOnAFolderIcon() {
        assert(GestureAction.LAUNCH_FOLDER_ITEM !in GestureDefaults.pickerActions)
        assert(GestureAction.LAUNCH_FOLDER_ITEM !in ActionShortcut.choices)
    }

    @Test
    fun decodeReturnsNullForForeignUris() {
        assertNull(ActionShortcut.decode(null))
        assertNull(ActionShortcut.decode("https://example.com"))
        assertNull(ActionShortcut.decode("intent:#Intent;action=android.intent.action.VIEW;end"))
    }

    @Test
    fun payloadActionWithoutPayloadDecodesToNull() {
        // A page-jump with no page index is meaningless; decode rejects it so
        // launchItem falls through rather than jumping nowhere.
        assertNull(ActionShortcut.decode("supernova://action/JUMP_TO_PAGE"))
    }

    @Test
    fun unknownActionNameDecodesToNoneBinding() {
        val decoded = ActionShortcut.decode("supernova://action/NOT_A_REAL_ACTION")
        assertEquals(GestureAction.NONE, decoded?.action)
    }

    @Test
    fun privateSpaceIsOfferedOnlyWithAPrivateSpace() {
        val withSpace = ActionShortcut.offered(ActionShortcut.choices, hasPrivateSpace = true)
        val without = ActionShortcut.offered(ActionShortcut.choices, hasPrivateSpace = false)
        assertEquals(ActionShortcut.choices, withSpace)
        assertEquals(ActionShortcut.choices - GestureAction.PRIVATE_SPACE, without)
        assert(GestureAction.PRIVATE_SPACE in GestureDefaults.pickerActions)
    }

    @Test
    fun choicesAreAllRenderableAndPayloadless() {
        // Everything offered as a standalone icon must be launchable with no
        // target; payload actions are added through their own target pickers.
        ActionShortcut.choices.forEach { action ->
            val uri = ActionShortcut.encode(action)
            assertEquals(action, ActionShortcut.decode(uri)?.action)
        }
    }
}
