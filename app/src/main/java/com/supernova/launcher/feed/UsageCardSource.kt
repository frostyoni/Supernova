package com.supernova.launcher.feed

import com.supernova.launcher.data.LaunchableApp
import kotlinx.coroutines.flow.MutableStateFlow

// Apps-opened card: the top few by the launch counts the launcher already
// keeps (no UsageStatsManager, no permission). The counts are a small map
// held in memory, so there is nothing to observe: the card is rebuilt when
// the pane opens and when a launch lands, and dormant in between.
class UsageCardSource(
    private val counts: () -> Map<String, Int>,
    private val apps: () -> List<LaunchableApp>
) : CardSource<UsageCardSource.UsageState> {

    // An empty list is "nothing counted yet": the card says so instead of
    // vanishing.
    data class UsageState(val rows: List<UsageRank.Counted>)

    override val state = MutableStateFlow<UsageState?>(null)

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
        state.value = UsageState(UsageRank.top(counts(), apps(), MAX_ROWS))
    }

    private companion object {
        const val MAX_ROWS = 5
    }
}
