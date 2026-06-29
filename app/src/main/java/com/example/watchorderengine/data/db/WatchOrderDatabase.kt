package com.example.watchorderengine.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.watchorderengine.data.db.dao.*
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.db.entity.PendingSyncTaskEntity
import com.example.watchorderengine.data.db.dao.PendingSyncTaskDao

// ─── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString("|||")

    @TypeConverter
    fun toList(str: String): List<String> =
        if (str.isBlank()) emptyList() else str.split("|||")
}

// ─── Schema migrations ────────────────────────────────────────────────────────

/**
 * v4 → v5: Add the `watchProvidersJson` column to the `media` table.
 *
 * We use ALTER TABLE (not destructive migration) so the user's existing
 * watchlist data is preserved.  The new column defaults to `'[]'` —
 * the empty JSON array — which the repository's Moshi adapter deserialises
 * to an empty [List], resulting in the "Where to Watch" section simply being
 * hidden until the media entry is refreshed from TMDB.
 *
 * NOTE: `fallbackToDestructiveMigration()` is kept as a last-resort safety
 * net but should NOT fire for this particular version bump.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE media ADD COLUMN watchProvidersJson TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_sync_tasks` (
                `id`           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `taskType`     TEXT NOT NULL,
                `universeId`   TEXT NOT NULL,
                `nodeId`       TEXT,
                `completed`    INTEGER NOT NULL DEFAULT 1,
                `episodeId`    TEXT,
                `mediaId`      TEXT,
                `payload`      TEXT NOT NULL DEFAULT '{}',
                `createdAt`    INTEGER NOT NULL,
                `retryCount`   INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_tasks_taskType` ON `pending_sync_tasks` (`taskType`)")
    }
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        MediaEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        UserProgressEntity::class,
        EpisodeWatchedEntity::class,
        DiscoverySkippedEntity::class,
        PendingSyncTaskEntity::class
    ],
    version = 6,           // ← bumped from 5
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WatchOrderDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun episodeWatchedDao(): EpisodeWatchedDao
    abstract fun discoverySkippedDao(): DiscoverySkippedDao
    abstract fun pendingSyncTaskDao(): PendingSyncTaskDao

    companion object {
        @Volatile private var INSTANCE: WatchOrderDatabase? = null

        fun getInstance(context: Context): WatchOrderDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WatchOrderDatabase::class.java,
                    "watchorder.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
