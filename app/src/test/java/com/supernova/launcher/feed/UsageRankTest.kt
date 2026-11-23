package com.supernova.launcher.feed

import com.supernova.launcher.data.LaunchableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The usage card's ranking: which apps are counted, in what order, and how
// much of the bar each one gets.
class UsageRankTest {

    private fun app(label: String, cls: String = "Main", serial: Long = 0L) = LaunchableApp(
        packageName = "pkg.$label", className = cls, label = label, userSerial = serial
    )

    private val camera = app("Camera")
    private val maps = app("Maps")
    private val bank = app("Bank")

    private fun counts(vararg entries: Pair<LaunchableApp, Int>) =
        entries.associate { (app, count) -> UsageRank.key(app) to count }

    private fun labels(rows: List<UsageRank.Counted>) = rows.map { it.app.label }

    @Test
    fun topRanksByCountAndLeavesOutAppsNeverOpened() {
        val rows = UsageRank.top(counts(maps to 9, camera to 4, bank to 0), listOf(camera, maps, bank), 5)
        assertEquals(listOf("Maps", "Camera"), labels(rows))
        assertEquals(listOf(9, 4), rows.map { it.count })
    }

    @Test
    fun topTakesTheLimitAndLevelCountsKeepNameOrder() {
        // bank and camera level on 4: name order, whatever the input order.
        val rows = UsageRank.top(counts(camera to 4, bank to 4, maps to 9), listOf(camera, maps, bank), 2)
        assertEquals(listOf("Maps", "Bank"), labels(rows))
    }

    @Test
    fun aCountWhoseAppIsGoneIsLeftOut() {
        val gone = app("Gone")
        val rows = UsageRank.top(counts(gone to 7, camera to 2), listOf(camera), 5)
        assertEquals(listOf("Camera"), labels(rows))
    }

    @Test
    fun anAppWithSeveralActivitiesIsCountedPerActivity() {
        val main = app("Keep", cls = "Main")
        val notes = app("Keep", cls = "Notes")
        val rows = UsageRank.top(counts(main to 3, notes to 5), listOf(main, notes), 5)
        assertEquals(listOf(notes, main), rows.map { it.app })
    }

    @Test
    fun anEmptyHistoryRanksNothing() {
        assertEquals(emptyList<UsageRank.Counted>(), UsageRank.top(emptyMap(), listOf(camera), 5))
    }

    @Test
    fun aZeroLimitRanksNothing() {
        assertEquals(emptyList<UsageRank.Counted>(), UsageRank.top(counts(camera to 3), listOf(camera), 0))
    }

    @Test
    fun theBarIsTheShareOfTheTopCount() {
        val rows = UsageRank.top(counts(maps to 10, camera to 5, bank to 1), listOf(camera, maps, bank), 5)
        assertEquals(1f, rows[0].share)
        assertEquals(0.5f, rows[1].share)
        assertEquals(0.1f, rows[2].share)
    }

    @Test
    fun aShareStaysWithinTheBar() {
        assertEquals(0f, UsageRank.share(4, 0))
        assertEquals(0f, UsageRank.share(0, 4))
        assertEquals(1f, UsageRank.share(8, 4))
        assertTrue(UsageRank.share(1, 3) < UsageRank.share(2, 3))
    }

    @Test
    fun theKeyMatchesWhatALaunchIsCountedUnder() {
        assertEquals("3/pkg.Camera/Main", UsageRank.key(app("Camera", serial = 3)))
        assertEquals("0/pkg.Camera/Notes", UsageRank.key(app("Camera", cls = "Notes")))
    }
}
