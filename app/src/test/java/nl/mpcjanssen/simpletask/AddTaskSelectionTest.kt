package nl.mpcjanssen.simpletask

import junit.framework.TestCase

class AddTaskSelectionTest : TestCase() {
    fun testCurrentLineUsesSavedSelectionOnLaterLine() {
        val text = "first line\nsecond line\nthird line"
        val selection = SelectionSnapshot(start = 15, end = 15)

        assertEquals(1, AddTaskSelection.currentLine(text, selection))
    }

    fun testRestoreCursorKeepsAbsolutePositionForTagInsertions() {
        val selection = SelectionSnapshot(start = 18, end = 18)

        assertEquals(18, AddTaskSelection.restoredCursor(selection, oldLength = 22, newLength = 27, moveCursor = false))
    }

    fun testRestoreCursorMovesWithPriorityLengthDelta() {
        val selection = SelectionSnapshot(start = 8, end = 8)

        assertEquals(12, AddTaskSelection.restoredCursor(selection, oldLength = 10, newLength = 14, moveCursor = true))
    }

    fun testRestoreCursorMovesToEndOfCurrentLineAfterTagInsertion() {
        val updatedText = "first +tag\nsecond line"
        val selection = SelectionSnapshot(start = 2, end = 2)

        assertEquals(10, AddTaskSelection.restoredCursorToLineEnd(updatedText, selection))
    }

    fun testRestoreCursorMovesToEndOfLaterLineAfterDateInsertion() {
        val updatedText = "first line\nsecond due:2026-04-21"
        val selection = SelectionSnapshot(start = 15, end = 15)

        assertEquals(updatedText.length, AddTaskSelection.restoredCursorToLineEnd(updatedText, selection))
    }

    fun testNormalizeLineIndexClampsPastEnd() {
        assertEquals(1, AddTaskSelection.normalizeLineIndex(4, 2))
    }

    fun testTrackerKeepsLastValidSelectionAcrossFocusLoss() {
        val tracker = SelectionSnapshotTracker()

        tracker.remember(SelectionSnapshot(start = 18, end = 18))
        tracker.remember(SelectionSnapshot(start = 0, end = 0), fromFocusedEditor = false)

        assertEquals(SelectionSnapshot(start = 18, end = 18), tracker.current())
    }

    fun testTrackerUpdatesWhenUserMovesCursor() {
        val tracker = SelectionSnapshotTracker()

        tracker.remember(SelectionSnapshot(start = 4, end = 4))
        tracker.remember(SelectionSnapshot(start = 22, end = 22))

        assertEquals(SelectionSnapshot(start = 22, end = 22), tracker.current())
    }
}
