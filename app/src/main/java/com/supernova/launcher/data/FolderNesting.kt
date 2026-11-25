package com.supernova.launcher.data

// A folder can move inside another folder, but never into itself or into a
// folder it already contains: either would make a loop of parents. Only a
// folder nests into a folder; apps and shortcuts keep their own rule.
internal object FolderNesting {

    fun canNest(model: LauncherModel, moving: LauncherItem, target: LauncherItem): Boolean {
        // Walk the target's ancestors: meeting the moving folder means the
        // target lives inside it, so nesting would close the loop.
        var cursor: LauncherItem? = target
        val seen = HashSet<String>()
        var nestable = moving.itemType == ItemType.FOLDER && target.itemType == ItemType.FOLDER &&
            moving.id != target.id
        while (nestable && cursor != null) {
            nestable = cursor.id != moving.id && seen.add(cursor.id)
            cursor = cursor.folderId?.let { id -> model.items.firstOrNull { it.id == id } }
        }
        return nestable
    }
}
