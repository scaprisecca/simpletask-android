package nl.mpcjanssen.simpletask

internal data class SelectionSnapshot(val start: Int, val end: Int) {
    val isValid: Boolean
        get() = start >= 0 && end >= 0

    companion object {
        val Invalid = SelectionSnapshot(-1, -1)
    }
}

internal object AddTaskSelection {
    fun snapshot(start: Int, end: Int, textLength: Int): SelectionSnapshot {
        if (start < 0 || end < 0) {
            return SelectionSnapshot.Invalid
        }

        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(0, textLength)
        return SelectionSnapshot(minOf(safeStart, safeEnd), maxOf(safeStart, safeEnd))
    }

    fun currentLine(text: CharSequence, selection: SelectionSnapshot): Int {
        if (!selection.isValid) {
            return -1
        }

        val safeStart = selection.start.coerceIn(0, text.length)
        return (0 until safeStart).count { text[it] == '\n' }
    }

    fun normalizeLineIndex(lineIndex: Int, lineCount: Int): Int {
        if (lineIndex < 0 || lineCount <= 0) {
            return -1
        }
        return lineIndex.coerceAtMost(lineCount - 1)
    }

    fun restoredCursor(selection: SelectionSnapshot, oldLength: Int, newLength: Int, moveCursor: Boolean): Int {
        var newLocation = selection.start
        if (moveCursor) {
            newLocation += newLength - oldLength
        }

        return newLocation.coerceIn(0, newLength)
    }
}
