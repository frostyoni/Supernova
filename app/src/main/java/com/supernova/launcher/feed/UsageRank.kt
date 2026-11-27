package com.supernova.launcher.feed

import com.supernova.launcher.data.LabelOrder
import com.supernova.launcher.data.LaunchableApp

// The usage card's ranking, kept off Android so it runs on the JVM. Counts
// come from the usage store (UsageStore), which keys an app the way
// LauncherViewModel.hiddenKey does: "userSerial/pkg/cls".
object UsageRank {

    // A row: the app, how often it was opened, and its share of the top
    // app's count (1f for the top row). The card draws that share as a bar.
    data class Counted(val app: LaunchableApp, val count: Int, val share: Float)

    fun key(app: LaunchableApp): String = "${app.userSerial}/${app.packageName}/${app.className}"

    // The `limit` most launched apps. Apps never opened are left out, and so
    // are counts whose app is gone. Apps level on the count keep name order,
    // so the card doesn't shuffle between opens.
    fun top(counts: Map<String, Int>, apps: List<LaunchableApp>, limit: Int): List<Counted> {
        if (limit <= 0) return emptyList()
        // Only the counted apps are sorted by name; counts go stale, apps
        // don't, so the list stays short.
        val counted = apps.mapNotNull { app ->
            val count = counts[key(app)] ?: return@mapNotNull null
            if (count > 0) app to count else null
        }
        val ranked = LabelOrder.sorted(counted) { it.first.label }
            .sortedByDescending { it.second }
            .take(limit)
        val best = ranked.firstOrNull()?.second ?: return emptyList()
        return ranked.map { (app, count) -> Counted(app, count, share(count, best)) }
    }

    // A count against the top one, 0..1.
    fun share(count: Int, top: Int): Float =
        if (top <= 0) 0f else (count.toFloat() / top).coerceIn(0f, 1f)
}
