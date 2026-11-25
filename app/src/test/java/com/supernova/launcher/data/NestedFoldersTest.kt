package com.supernova.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// A folder inside a folder: the rules that keep the parent chain a tree
// (no self, no descendants), remove a folder's whole subtree, and let a
// nested folder's last member take its place in the parent.
class NestedFoldersTest {

    private fun folder(
        id: String,
        container: ContainerType = ContainerType.WORKSPACE,
        folderId: String? = null,
        rank: Int = 0
    ) = LauncherItem(
        id = id, itemType = ItemType.FOLDER, container = container, folderId = folderId,
        label = id, rank = rank
    )

    private fun app(id: String, folderId: String, rank: Int = 0) = LauncherItem(
        id = id, itemType = ItemType.APP, container = ContainerType.FOLDER, folderId = folderId,
        label = id, packageName = "pkg", className = "cls.$id", rank = rank
    )

    private fun model(vararg items: LauncherItem) = LauncherModel(items = items.toList())

    @Test
    fun aFolderCanNestInAnUnrelatedFolderButNeverInItselfOrItsChildren() {
        val outer = folder("outer")
        val inner = folder("inner", container = ContainerType.FOLDER, folderId = "outer")
        val deeper = folder("deeper", container = ContainerType.FOLDER, folderId = "inner")
        val other = folder("other")
        val m = model(outer, inner, deeper, other)
        assertTrue(FolderNesting.canNest(m, other, outer))
        assertFalse(FolderNesting.canNest(m, outer, outer))
        assertFalse(FolderNesting.canNest(m, outer, inner))
        assertFalse(FolderNesting.canNest(m, outer, deeper))
        // Only a folder nests: an app has its own rule.
        assertFalse(FolderNesting.canNest(m, app("app", "inner"), other))
    }

    @Test
    fun removingANestedFolderRemovesEverythingInsideIt() {
        val outer = folder("outer")
        val inner = folder("inner", container = ContainerType.FOLDER, folderId = "outer", rank = 0)
        val child = app("child", "inner")
        val grandchild = folder("grandchild", container = ContainerType.FOLDER, folderId = "inner")
        val leaf = app("leaf", "grandchild")
        val removed = ItemRemoval.removed(model(outer, inner, child, grandchild, leaf), "inner")
        assertTrue(removed.items.isEmpty())
    }

    @Test
    fun aNestedFolderLeftWithOneMemberGivesWayToIt() {
        val outer = folder("outer")
        val inner = folder("inner", container = ContainerType.FOLDER, folderId = "outer")
        val a = app("a", "inner", rank = 0)
        val b = app("b", "inner", rank = 1)
        val sibling = app("sibling", "outer", rank = 1)
        val removed = ItemRemoval.removed(model(outer, inner, a, b, sibling), "a")
        // The two-app rule still applies inside a nested folder: b takes
        // inner's place in outer, and inner goes.
        val ids = removed.items.map { it.id }.toSet()
        assertEquals(setOf("outer", "b", "sibling"), ids)
        val moved = removed.items.first { it.id == "b" }
        assertEquals(ContainerType.FOLDER, moved.container)
        assertEquals("outer", moved.folderId)
    }

    @Test
    fun aNestedFoldersLastMemberJoinsItsParent() {
        val outer = folder("outer")
        val inner = folder("inner", container = ContainerType.FOLDER, folderId = "outer")
        val last = app("last", "inner")
        val sibling = app("sibling", "outer", rank = 1)
        val next = LauncherRepository.withSparseFolderDissolved(model(outer, inner, last, sibling), "inner")
        val ids = next.items.map { it.id }.toSet()
        assertFalse("inner" in ids)
        val promoted = next.items.first { it.id == "last" }
        assertEquals(ContainerType.FOLDER, promoted.container)
        assertEquals("outer", promoted.folderId)
    }

    @Test
    fun aNestedFoldersDissolveRecursesUpWhenTheParentIsLeftSparse() {
        val outer = folder("outer")
        val inner = folder("inner", container = ContainerType.FOLDER, folderId = "outer")
        val last = app("last", "inner")
        val next = LauncherRepository.withSparseFolderDissolved(model(outer, inner, last), "inner")
        // inner's last member joins outer, which is then left with only it,
        // so outer gives way to it and it lands on the home screen.
        assertEquals(listOf("last"), next.items.map { it.id })
        val promoted = next.items.single()
        assertEquals(ContainerType.WORKSPACE, promoted.container)
        assertNull(promoted.folderId)
    }

    @Test
    fun aFolderDroppedOnADockFolderNestsInIt() {
        val dockFolder = folder("dock", container = ContainerType.DOCK)
        val moving = folder("moving")
        val child = app("child", "moving")
        val next = DockFolders.merged(
            model(dockFolder, moving, child),
            model(dockFolder, moving, child).items.first { it.id == "moving" },
            rank = 0,
            folderId = "new"
        )
        val moved = next.items.first { it.id == "moving" }
        assertEquals(ContainerType.FOLDER, moved.container)
        assertEquals("dock", moved.folderId)
        // Its own child stays inside it.
        assertEquals("moving", next.items.first { it.id == "child" }.folderId)
    }

    @Test
    fun aFolderDroppedOnADockAppDoesNotMakeAFolder() {
        val dockApp = LauncherItem(
            id = "dockApp", itemType = ItemType.APP, container = ContainerType.DOCK,
            label = "app", packageName = "pkg", className = "cls"
        )
        val moving = folder("moving")
        val current = model(dockApp, moving)
        assertNull(DockFolders.target(current, current.items.first { it.id == "moving" }, rank = 0))
    }
}
