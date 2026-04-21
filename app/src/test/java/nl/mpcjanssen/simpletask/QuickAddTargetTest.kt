package nl.mpcjanssen.simpletask

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.fileswitch.FavoriteTodoFile
import java.io.File

class QuickAddTargetTest : TestCase() {
    fun testResolveFallsBackToActiveFileWhenNoExplicitTarget() {
        val fallback = File("/tmp/active.txt")

        val result = QuickAddTarget.resolve(null, fallback)

        assertEquals(fallback, result.targetFile)
        assertFalse(result.hasExplicitTarget)
    }

    fun testResolveUsesExplicitTargetWhenPresent() {
        val explicitFile = File(createTempFile("shortcut-target", ".txt").absolutePath)
        val fallback = File("/tmp/active.txt")

        val result = QuickAddTarget.resolve(explicitFile.absolutePath, fallback)

        assertEquals(explicitFile.canonicalFile, result.targetFile)
        assertTrue(result.hasExplicitTarget)
        explicitFile.delete()
    }

    fun testResolveUsesMissingExplicitTargetPathWithoutFallingBack() {
        val missing = File("/tmp/does-not-exist-shortcut-target.txt")
        val fallback = File("/tmp/active.txt")

        val result = QuickAddTarget.resolve(missing.absolutePath, fallback)

        assertEquals(missing.absoluteFile, result.targetFile)
        assertTrue(result.hasExplicitTarget)
    }

    fun testMergeAppendsAtEndWhenConfigured() {
        val result = QuickAddTarget.mergeExistingAndNewLines(
            existingLines = listOf("existing one", "existing two"),
            newLines = listOf("new one"),
            appendAtEnd = true
        )

        assertEquals(listOf("existing one", "existing two", "new one"), result)
    }

    fun testMergePrependsWhenAppendDisabled() {
        val result = QuickAddTarget.mergeExistingAndNewLines(
            existingLines = listOf("existing one", "existing two"),
            newLines = listOf("new one"),
            appendAtEnd = false
        )

        assertEquals(listOf("new one", "existing one", "existing two"), result)
    }

    fun testExplicitTargetMustBeInFavorites() {
        val allowedTarget = File("/lists/home/inbox.txt")
        val disallowedTarget = File("/lists/private/secrets.txt")
        val favorites = listOf(
            FavoriteTodoFile("/lists/home/inbox.txt"),
            FavoriteTodoFile("/lists/work/inbox.txt")
        )

        assertTrue(QuickAddTarget.isExplicitTargetAllowed(allowedTarget, favorites))
        assertFalse(QuickAddTarget.isExplicitTargetAllowed(disallowedTarget, favorites))
    }
}
