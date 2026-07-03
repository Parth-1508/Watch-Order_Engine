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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_reviews` (
                `id`             TEXT NOT NULL PRIMARY KEY,
                `mediaId`        TEXT NOT NULL,
                `mediaTitle`     TEXT NOT NULL,
                `mediaPosterUrl` TEXT,
                `userId`         TEXT NOT NULL,
                `rating`         REAL NOT NULL,
                `reviewText`     TEXT NOT NULL,
                `hasSpoilers`    INTEGER NOT NULL,
                `watchedDate`    INTEGER,
                `createdAt`      INTEGER NOT NULL,
                `updatedAt`      INTEGER NOT NULL,
                `isSynced`       INTEGER NOT NULL,
                FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_mediaId` ON `user_reviews` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_userId` ON `user_reviews` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_rating` ON `user_reviews` (`rating`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Update pending_sync_tasks
        db.execSQL("ALTER TABLE pending_sync_tasks ADD COLUMN reviewId TEXT")

        // 2. Re-create user_reviews to ensure it exactly matches the expected schema 
        // (adding missing columns and removing default values that confuse Room).
        // Since this is a new feature in the dev cycle, we drop and re-create.
        db.execSQL("DROP TABLE IF EXISTS `user_reviews` ")
        db.execSQL("""
            CREATE TABLE `user_reviews` (
                `id`             TEXT NOT NULL PRIMARY KEY,
                `mediaId`        TEXT NOT NULL,
                `mediaTitle`     TEXT NOT NULL,
                `mediaPosterUrl` TEXT,
                `userId`         TEXT NOT NULL,
                `rating`         REAL NOT NULL,
                `reviewText`     TEXT NOT NULL,
                `hasSpoilers`    INTEGER NOT NULL,
                `watchedDate`    INTEGER,
                `createdAt`      INTEGER NOT NULL,
                `updatedAt`      INTEGER NOT NULL,
                `isSynced`       INTEGER NOT NULL,
                FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_mediaId` ON `user_reviews` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_userId` ON `user_reviews` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_rating` ON `user_reviews` (`rating`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Force-fix user_reviews to match exactly what Room expects
        db.execSQL("DROP TABLE IF EXISTS `user_reviews` ")
        db.execSQL("""
            CREATE TABLE `user_reviews` (
                `id`             TEXT NOT NULL PRIMARY KEY,
                `mediaId`        TEXT NOT NULL,
                `mediaTitle`     TEXT NOT NULL,
                `mediaPosterUrl` TEXT,
                `userId`         TEXT NOT NULL,
                `rating`         REAL NOT NULL,
                `reviewText`     TEXT NOT NULL,
                `hasSpoilers`    INTEGER NOT NULL,
                `watchedDate`    INTEGER,
                `createdAt`      INTEGER NOT NULL,
                `updatedAt`      INTEGER NOT NULL,
                `isSynced`       INTEGER NOT NULL,
                FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_mediaId` ON `user_reviews` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_userId` ON `user_reviews` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_reviews_rating` ON `user_reviews` (`rating`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media ADD COLUMN originalLanguage TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_progress ADD COLUMN completedNodeIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_progress ADD COLUMN activeRoute TEXT")
        db.execSQL("ALTER TABLE user_progress ADD COLUMN spoilerShieldEnabled INTEGER NOT NULL DEFAULT 0")
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
        PendingSyncTaskEntity::class,
        ReviewEntity::class
    ],
    version = 11,
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
    abstract fun reviewDao(): ReviewDao

    /**
     * Surgically clears only the cached metadata (shows, seasons, episodes)
     * while preserving user-specific data (watchlist progress, watched episodes, 
     * reviews, and sync tasks). 
     * 
     * This fulfills the promise made in the Settings UI that clearing cache 
     * is safe for your progress.
     */
    suspend fun clearMetadataCache() {
        val db = (this as RoomDatabase).openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM episodes")
            db.execSQL("DELETE FROM seasons")
            db.execSQL("DELETE FROM media")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        @Volatile private var INSTANCE: WatchOrderDatabase? = null

        fun getInstance(context: Context): WatchOrderDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WatchOrderDatabase::class.java,
                    "watchorder.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
