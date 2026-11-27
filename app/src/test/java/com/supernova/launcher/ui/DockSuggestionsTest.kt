package com.supernova.launcher.ui

import com.supernova.launcher.data.LaunchableApp
import kotlin.test.Test
import kotlin.test.assertEquals

class DockSuggestionsTest {

    private fun app(name: String, serial: Long = 0L) =
        LaunchableApp(packageName = name, className = "$name.Main", label = name, userSerial = serial)

    @Test
    fun appsAlreadyInTheDockAreNotSuggestedAgain() {
        val picked = DockSuggestions.fill(
            listOf(app("a"), app("b"), app("c")),
            placed = setOf(DockSuggestions.key(app("a"))),
            limit = 5
        )
        assertEquals(listOf("b", "c"), picked.map { it.packageName })
    }

    @Test
    fun anAppAppearsOnceEvenInTwoProfiles() {
        val picked = DockSuggestions.fill(
            listOf(app("a"), app("a"), app("b")),
            placed = emptySet(),
            limit = 5
        )
        assertEquals(listOf("a", "b"), picked.map { it.packageName })
    }

    @Test
    fun theLimitIsTheEmptySlotCount() {
        val picked = DockSuggestions.fill(
            listOf(app("a"), app("b"), app("c")),
            placed = emptySet(),
            limit = 2
        )
        assertEquals(listOf("a", "b"), picked.map { it.packageName })
        assertEquals(emptyList(), DockSuggestions.fill(listOf(app("a")), emptySet(), 0))
    }

    @Test
    fun privateSpaceAppsStayOut() {
        val private = app("secret").copy(privateSpace = true)
        val picked = DockSuggestions.fill(
            listOf(private, app("open")),
            placed = emptySet(),
            limit = 5
        )
        assertEquals(listOf("open"), picked.map { it.packageName })
    }
}
