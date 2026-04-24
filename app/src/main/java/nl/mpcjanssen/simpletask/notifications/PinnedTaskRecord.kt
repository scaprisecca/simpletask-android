package nl.mpcjanssen.simpletask.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PinnedTaskRecord(
    @PrimaryKey val taskKey: String,
    @ColumnInfo(index = true) val todoFilePath: String,
    @ColumnInfo val taskText: String,
    @ColumnInfo val createdAt: Long,
    @ColumnInfo val lastKnownText: String = taskText,
    @ColumnInfo val triggerAtMillis: Long? = null,
    @ColumnInfo val triggerMode: String? = null
) {
    val notificationId: Int
        get() = taskKey.hashCode()
}
