package com.supernova.launcher.feed

import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.notification.NotificationGlance
import com.supernova.launcher.notification.SupernovaNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow

// The feed's notification glance: the newest badged notifications, read-only.
// Nothing is read while the card is off screen, and nothing at all without
// notification access - the card then says so rather than pretending to be
// empty.
class NotificationsCardSource(
    private val apps: () -> List<LaunchableApp>,
    private val enabled: () -> Boolean,
    private val userHash: (LaunchableApp) -> Int
) : CardSource<NotificationsCardSource.NotificationsState> {

    data class Row(val app: LaunchableApp, val item: NotificationGlance)

    // access = false is "notification access is off", not "no notifications".
    data class NotificationsState(val rows: List<Row>, val access: Boolean)

    override val state = MutableStateFlow<NotificationsState?>(null)

    private var shown = false

    override fun onShown() {
        if (shown) return
        shown = true
        refresh()
    }

    override fun onHidden() {
        if (!shown) return
        shown = false
        state.value = null
    }

    fun refresh() {
        if (!shown) return
        if (!enabled()) {
            state.value = NotificationsState(emptyList(), access = false)
            return
        }
        val byKey = apps().associateBy { "${userHash(it)}/${it.packageName}" }
        val rows = SupernovaNotificationListener.glance().mapNotNull { item ->
            byKey[item.appKey]?.let { Row(it, item) }
        }
        state.value = NotificationsState(rows, access = true)
    }
}
