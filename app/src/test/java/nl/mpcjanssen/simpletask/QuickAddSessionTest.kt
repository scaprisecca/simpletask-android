package nl.mpcjanssen.simpletask

import junit.framework.TestCase
import java.io.File

class QuickAddSessionTest : TestCase() {
    fun testBlocksExplicitQuickAddWhenAnotherAddTaskEditorIsOpen() {
        assertTrue(QuickAddSession.shouldBlockLaunch(hasExplicitTarget = true, activeEditorCount = 1))
    }

    fun testDoesNotBlockNormalAddTaskLaunches() {
        assertFalse(QuickAddSession.shouldBlockLaunch(hasExplicitTarget = false, activeEditorCount = 1))
    }

    fun testUsesIsolatedMetadataForDifferentExplicitTarget() {
        val resolution = QuickAddTargetResolution(File("/tmp/other.txt"), hasExplicitTarget = true)

        assertTrue(QuickAddSession.usesIsolatedMetadata(resolution, File("/tmp/active.txt")))
    }

    fun testDoesNotUseIsolatedMetadataForSameCanonicalFile() {
        val resolution = QuickAddTargetResolution(File("/tmp/favorites/../favorites/todo.txt"), hasExplicitTarget = true)

        assertFalse(QuickAddSession.usesIsolatedMetadata(resolution, File("/tmp/favorites/todo.txt")))
    }
}
