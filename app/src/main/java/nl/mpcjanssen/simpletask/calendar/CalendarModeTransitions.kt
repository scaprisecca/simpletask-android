package nl.mpcjanssen.simpletask.calendar

data class CalendarExitResult(
    val nextState: CalendarModeState,
    val restoredListSnapshot: CalendarListSnapshot?
)

internal object CalendarModeTransitions {
    fun exit(state: CalendarModeState): CalendarExitResult {
        return CalendarExitResult(
            nextState = CalendarModeState(),
            restoredListSnapshot = state.listSnapshot
        )
    }
}
