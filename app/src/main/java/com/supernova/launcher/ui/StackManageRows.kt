package com.supernova.launcher.ui

import com.supernova.launcher.data.WidgetStacks

// The Manage stack sheet's rows, as plain data: which member the cell is
// showing and which way it can move, so the sheet never works out an end by
// hand and the rules can be tested without a view.
internal object StackManageRows {

    // One member at `index` in the stack's order; front is the widget the
    // cell shows.
    data class Row(
        val index: Int,
        val front: Boolean,
        val canMoveUp: Boolean,
        val canMoveDown: Boolean
    )

    // A front left over from a longer stack clamps to the last row, so a
    // sheet opened on state a removal has already shortened still marks one.
    fun rows(count: Int, front: Int): List<Row> {
        if (count <= 0) return emptyList()
        val marked = front.coerceIn(0, count - 1)
        return (0 until count).map { index ->
            Row(index, front = index == marked, canMoveUp = index > 0, canMoveDown = index < count - 1)
        }
    }

    // Add widget stops at the bound the model's tail uses, so the sheet and
    // the long-press menu agree on when a stack is full.
    fun canAdd(count: Int): Boolean = count < WidgetStacks.MAX_MEMBERS
}
