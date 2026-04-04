package nl.mpcjanssen.simpletask.calendar

import android.os.Bundle
import nl.mpcjanssen.simpletask.Query
import org.json.JSONObject

enum class ScheduledDateVisibility(val storageValue: String) {
    BOTH("both"),
    DUE("due"),
    THRESHOLD("threshold");

    fun showsDue() = this == BOTH || this == DUE

    fun showsThreshold() = this == BOTH || this == THRESHOLD

    companion object {
        fun fromStoredValue(value: String?): ScheduledDateVisibility {
            return values().firstOrNull { it.storageValue == value } ?: BOTH
        }
    }
}

data class CalendarListSnapshot(
    val mainQueryJson: String,
    val scrollPosition: Int,
    val scrollOffset: Int
) {
    fun restoreQuery(): Query = Query(JSONObject(mainQueryJson), luaModule = "mainui")
}

data class CalendarModeState(
    val active: Boolean = false,
    val selectedDate: String? = null,
    val visibleMonth: String? = null,
    val listSnapshot: CalendarListSnapshot? = null
) {
    fun toBundle(bundle: Bundle) {
        toMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
            }
        }
    }

    fun toMap(): Map<String, Any> {
        val result = linkedMapOf<String, Any>(
            KEY_ACTIVE to active
        )
        selectedDate?.let { result[KEY_SELECTED_DATE] = it }
        visibleMonth?.let { result[KEY_VISIBLE_MONTH] = it }
        listSnapshot?.let {
            result[KEY_QUERY_JSON] = it.mainQueryJson
            result[KEY_SCROLL_POSITION] = it.scrollPosition
            result[KEY_SCROLL_OFFSET] = it.scrollOffset
        }
        return result
    }

    companion object {
        private const val KEY_ACTIVE = "calendar_mode_active"
        private const val KEY_SELECTED_DATE = "calendar_selected_date"
        private const val KEY_VISIBLE_MONTH = "calendar_visible_month"
        private const val KEY_QUERY_JSON = "calendar_main_query_json"
        private const val KEY_SCROLL_POSITION = "calendar_scroll_position"
        private const val KEY_SCROLL_OFFSET = "calendar_scroll_offset"

        fun fromBundle(bundle: Bundle?): CalendarModeState? {
            if (bundle == null || !bundle.containsKey(KEY_ACTIVE)) {
                return null
            }
            return fromMap(
                linkedMapOf<String, Any?>(
                    KEY_ACTIVE to bundle.getBoolean(KEY_ACTIVE, false),
                    KEY_SELECTED_DATE to bundle.getString(KEY_SELECTED_DATE),
                    KEY_VISIBLE_MONTH to bundle.getString(KEY_VISIBLE_MONTH),
                    KEY_QUERY_JSON to bundle.getString(KEY_QUERY_JSON),
                    KEY_SCROLL_POSITION to bundle.getInt(KEY_SCROLL_POSITION, -1),
                    KEY_SCROLL_OFFSET to bundle.getInt(KEY_SCROLL_OFFSET, -1)
                )
            )
        }

        fun fromMap(map: Map<String, Any?>): CalendarModeState? {
            if (!map.containsKey(KEY_ACTIVE)) {
                return null
            }
            val queryJson = map[KEY_QUERY_JSON] as? String
            val listSnapshot = queryJson?.let {
                CalendarListSnapshot(
                    mainQueryJson = it,
                    scrollPosition = map[KEY_SCROLL_POSITION] as? Int ?: -1,
                    scrollOffset = map[KEY_SCROLL_OFFSET] as? Int ?: -1
                )
            }
            return CalendarModeState(
                active = map[KEY_ACTIVE] as? Boolean ?: false,
                selectedDate = map[KEY_SELECTED_DATE] as? String,
                visibleMonth = map[KEY_VISIBLE_MONTH] as? String,
                listSnapshot = listSnapshot
            )
        }
    }
}
