package com.farhat.smartaccess.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "access_logs")
data class AccessLog(
    @PrimaryKey val eventId: String,
    val uid: String,
    val granted: Boolean,
    val timestamp: Long,
    val source: String
)
@Dao
interface AccessDao {
    @Query("SELECT * FROM access_logs ORDER BY timestamp DESC") fun observe(): Flow<List<AccessLog>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(logs: List<AccessLog>)
    @Query("DELETE FROM access_logs") suspend fun clear()
}
@Database(entities = [AccessLog::class], version = 1, exportSchema = true)
abstract class AccessDatabase : RoomDatabase() { abstract fun logs(): AccessDao }
