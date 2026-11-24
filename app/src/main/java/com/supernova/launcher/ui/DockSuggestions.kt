package com.supernova.launcher.ui

import com.supernova.launcher.data.LaunchableApp

// Which apps an empty dock slot may suggest: the ones used recently, minus
// anything already in the dock, each app once. Pure, so the picking can be
// tested without the dock.
object DockSuggestions {

    fun key(app: LaunchableApp): String = "${app.userSerial}/${app.packageName}/${app.className}"

    fun fill(
        candidates: List<LaunchableApp>,
        placed: Set<String>,
        limit: Int
    ): List<LaunchableApp> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>(placed)
        val picked = ArrayList<LaunchableApp>(minOf(limit, candidates.size))
        for (app in candidates) {
            if (picked.size >= limit) break
            if (app.privateSpace) continue
            if (!seen.add(key(app))) continue
            picked += app
        }
        return picked
    }
}
