package com.supernova.launcher.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetStacksTest {

    private fun member(id: Int) = WidgetStackMember(id, "p/$id", 0L)

    private fun item(vararg ids: Int) = LauncherItem(
        id = "w",
        itemType = ItemType.WIDGET,
        appWidgetId = ids.firstOrNull(),
        widgetProvider = ids.firstOrNull()?.let { "p/$it" },
        widgetStack = ids.drop(1).map { member(it) }
    )

    @Test
    fun thePrimaryIsJustTheFirstMember() {
        val order = WidgetStacks.order(item(1, 2, 3))
        assertEquals(listOf(1, 2, 3), order.map { it.appWidgetId })
    }

    @Test
    fun movingCarriesTheWidgetAndTheFrontFollowsIt() {
        val order = WidgetStacks.order(item(1, 2, 3))
        val moved = WidgetStacks.move(order, 2, 0)
        assertEquals(listOf(3, 1, 2), moved.map { it.appWidgetId })
        // The front was on widget 3 (index 2); after the move it is index 0,
        // and the cell still shows widget 3.
        val front = WidgetStacks.frontAfterMove(from = 2, to = 0, front = 2)
        val back = WidgetStacks.withOrder(item(1, 2, 3), moved, front)
        assertEquals(3, back.appWidgetId)
        assertEquals(listOf(1, 2), back.widgetStack.map { it.appWidgetId })
        assertEquals(0, back.widgetStackFront)
    }

    @Test
    fun removingTheHeadPromotesTheNext() {
        val order = WidgetStacks.order(item(1, 2, 3))
        val next = WidgetStacks.remove(order, 0)
        val written = WidgetStacks.withOrder(item(1, 2, 3), next, front = 0)
        assertEquals(2, written.appWidgetId)
        assertEquals(listOf(3), written.widgetStack.map { it.appWidgetId })
    }

    @Test
    fun removingTheLastLeavesAnEmptyWidgetRatherThanACrash() {
        val order = WidgetStacks.order(item(1))
        val written = WidgetStacks.withOrder(item(1), WidgetStacks.remove(order, 0), front = 0)
        assertEquals(null, written.appWidgetId)
        assertTrue(written.widgetStack.isEmpty())
    }

    @Test
    fun theFrontStaysOnItsOwnWidgetWhenAnotherIsRemoved() {
        val order = WidgetStacks.order(item(1, 2, 3, 4))
        // Front on 4 (index 3), removing index 1 -> front becomes 2.
        assertEquals(2, WidgetStacks.frontAfterRemove(order, removed = 1, front = 3))
        // Front before the removal does not move.
        assertEquals(0, WidgetStacks.frontAfterRemove(order, removed = 2, front = 0))
        // Removing the front itself lands on what took its place.
        assertEquals(1, WidgetStacks.frontAfterRemove(order, removed = 1, front = 1))
    }

    @Test
    fun theFrontFollowsItsWidgetThroughAMove() {
        assertEquals(0, WidgetStacks.frontAfterMove(from = 2, to = 0, front = 2))
        assertEquals(0, WidgetStacks.frontAfterMove(from = 0, to = 2, front = 1))
        assertEquals(0, WidgetStacks.frontAfterMove(from = 1, to = 2, front = 0))
        assertEquals(1, WidgetStacks.frontAfterMove(from = 0, to = 2, front = 2))
    }

    @Test
    fun anUnboundHeadIsNotAMember() {
        val item = LauncherItem(
            id = "w",
            itemType = ItemType.WIDGET,
            appWidgetId = null,
            widgetProvider = null,
            widgetStack = listOf(member(2), member(3))
        )
        assertEquals(listOf(2, 3), WidgetStacks.order(item).map { it.appWidgetId })
        val none = LauncherItem(id = "w", itemType = ItemType.WIDGET)
        assertTrue(WidgetStacks.order(none).isEmpty())
    }

    @Test
    fun outOfRangeMovesAndRemovalsAreNoOps() {
        val order = WidgetStacks.order(item(1, 2))
        assertEquals(order, WidgetStacks.move(order, 5, 0))
        assertEquals(order, WidgetStacks.remove(order, -1))
    }
}
