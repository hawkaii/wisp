package app.wispnotes.index

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NoteDao {
    @Query("SELECT * FROM notes WHERE status IN ('queued', 'in-progress') ORDER BY saved DESC, fileName DESC")
    abstract fun queue(): Flow<List<NoteEntry>>

    @Query("SELECT * FROM notes ORDER BY saved DESC, fileName DESC")
    abstract fun all(): Flow<List<NoteEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(entry: NoteEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putAll(entries: List<NoteEntry>)

    @Query("DELETE FROM notes")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(entries: List<NoteEntry>) {
        clear()
        putAll(entries)
    }
}

@Database(entities = [NoteEntry::class], version = 2, exportSchema = false)
abstract class WispDb : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        @Volatile private var instance: WispDb? = null

        fun get(context: Context): WispDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, WispDb::class.java, "wisp-index.db")
                .fallbackToDestructiveMigration() // an index is a cache: on any schema change, drop it and rescan
                .build()
                .also { instance = it }
        }
    }
}
