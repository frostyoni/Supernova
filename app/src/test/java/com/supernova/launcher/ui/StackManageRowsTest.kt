package com.supernova.launcher.ui

import com.supernova.launcher.data.WidgetStacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StackManageRowsTest {

    @Test
    fun everyMemberGetsARowInTheOrderGiven() {
        assertEquals(listOf(0, 1, 2), StackManageRows.rows(count = 3, front = 0).map { it.index })
    }

    @Test
    fun onlyOneRowIsTheFrontOne() {
        val rows = StackManageRows.rows(count = 4, front = 2)
        assertEquals(listOf(2), rows.filter { it.front }.map { it.index })
    }

    @Test
    fun aStaleFrontClampsToTheLastRow() {
        // A removal shortened the stack behind the sheet's back.
        assertEquals(listOf(2), StackManageRows.rows(count = 3, front = 9).filter { it.front }.map { it.index })
        assertEquals(listOf(0), StackManageRows.rows(count = 3, front = -1).filter { it.front }.map { it.index })
    }

    @Test
    fun theEndsCannotMoveOutward() {
        val rows = StackManageRows.rows(count = 3, front = 0)
        assertFalse(rows.first().canMoveUp)
        assertTrue(rows.first().canMoveDown)
        assertFalse(rows.last().canMoveDown)
        assertTrue(rows.last().canMoveUp)
        assertTrue(rows[1].canMoveUp)
        assertTrue(rows[1].canMoveDown)
    }

    @Test
    fun aSingleMemberCannotMoveEitherWay() {
        val only = StackManageRows.rows(count = 1, front = 0).single()
        assertFalse(only.canMoveUp)
        assertFalse(only.canMoveDown)
    }

    @Test
    fun anEmptyStackHasNoRows() {
        assertTrue(StackManageRows.rows(count = 0, front = 0).isEmpty())
        assertTrue(StackManageRows.rows(count = -1, front = 0).isEmpty())
    }

    @Test
    fun addingStopsAtTheStackBound() {
        assertTrue(StackManageRows.canAdd(0))
        assertTrue(StackManageRows.canAdd(WidgetStacks.MAX_MEMBERS - 1))
        assertFalse(StackManageRows.canAdd(WidgetStacks.MAX_MEMBERS))
        assertFalse(StackManageRows.canAdd(WidgetStacks.MAX_MEMBERS + 1))
    }
}
