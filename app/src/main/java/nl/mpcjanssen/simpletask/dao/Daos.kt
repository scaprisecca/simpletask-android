package nl.mpcjanssen.simpletask.dao

import android.content.Context
import androidx.room.*
import nl.mpcjanssen.simpletask.notifications.PinnedTaskRecord

const val SCHEMA_VERSION=1014
const val DB_FILE="TodoFiles_v1.db"

@Entity
data class TodoFile(
        @PrimaryKey var contents: String,
        @ColumnInfo var name: String,
        @ColumnInfo var date: Long
)

@Dao
interface TodoFileDao {
    @Query("SELECT * FROM TodoFile")
    fun getAll(): List<TodoFile>


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(contents: TodoFile) : Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun update(contents: TodoFile)

    @Query ("DELETE from TodoFile where date < :timestamp")
    fun removeBefore(timestamp: Long)

    @Query ("DELETE from TodoFile")
    fun deleteAll()


}

@Dao
interface PinnedTaskRecordDao {
    @Query("SELECT * FROM PinnedTaskRecord")
    fun getAll(): List<PinnedTaskRecord>

    @Query("SELECT * FROM PinnedTaskRecord WHERE taskKey = :taskKey")
    fun get(taskKey: String): PinnedTaskRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(record: PinnedTaskRecord)

    @Query("DELETE FROM PinnedTaskRecord WHERE taskKey = :taskKey")
    fun deleteByTaskKey(taskKey: String)
}

@Database(entities = arrayOf(TodoFile::class, PinnedTaskRecord::class), version = SCHEMA_VERSION, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoFileDao(): TodoFileDao
    abstract fun pinnedTaskRecordDao(): PinnedTaskRecordDao
}




