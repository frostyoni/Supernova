package com.supernova.launcher.data

// A widget stack's order, as pure list surgery the repository and the
// management sheet both use. The primary widget is just the first member:
// keeping one ordered list here means reordering and removing never have to
// know that the model stores the head separately from the tail.
object WidgetStacks {

    // Widgets in one stack, the primary included. A bound, not a limit the
    // user meets: a cell holding more than this is a folder of widgets, and
    // the indicator stops being readable long before it.
    const val MAX_MEMBERS = 12

    fun order(item: LauncherItem): List<WidgetStackMember> =
        listOf(WidgetStackMember(item.appWidgetId, item.widgetProvider.orEmpty(), item.userSerial)) +
            item.widgetStack

    fun move(order: List<WidgetStackMember>, from: Int, to: Int): List<WidgetStackMember> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        val next = order.toMutableList()
        next.add(to, next.removeAt(from))
        return next
    }

    fun remove(order: List<WidgetStackMember>, index: Int): List<WidgetStackMember> =
        if (index !in order.indices) order else order.filterIndexed { i, _ -> i != index }

    // Writes an order back onto the item: head to appWidgetId, tail to
    // widgetStack, and the front kept on the same widget where it still is.
    fun withOrder(item: LauncherItem, order: List<WidgetStackMember>, front: Int): LauncherItem {
        if (order.isEmpty()) {
            return item.copy(appWidgetId = null, widgetProvider = null, widgetStack = emptyList(), widgetStackFront = 0)
        }
        val head = order.first()
        return item.copy(
            appWidgetId = head.appWidgetId,
            widgetProvider = head.provider.ifBlank { null },
            userSerial = head.userSerial,
            widgetStack = order.drop(1),
            widgetStackFront = front.coerceIn(0, order.size - 1)
        )
    }

    // Where the front lands after a move: it stays on the same widget, so a
    // reorder never silently changes what the cell is showing.
    fun frontAfterMove(from: Int, to: Int, front: Int): Int = when {
        front == from -> to
        from < front && to >= front -> front - 1
        from > front && to <= front -> front + 1
        else -> front
    }

    // Where the front lands after a removal: the same widget if it survived,
    // otherwise the one that took its place, never past the end.
    fun frontAfterRemove(order: List<WidgetStackMember>, removed: Int, front: Int): Int = when {
        front < removed -> front
        front > removed -> front - 1
        else -> front.coerceAtMost(order.size - 2).coerceAtLeast(0)
    }
}
