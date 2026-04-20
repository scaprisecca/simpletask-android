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

    fun testNormalizeLineIndexClampsPastEnd() {
        assertEquals(1, AddTaskSelection.normalizeLineIndex(4, 2))
    }
}
