package com.example.watchorderengine.data.db

import android.content.Context
import androidx.room.*
import com.example.watchorderengine.data.db.dao.*
import com.example.watchorderengine.data.db.entity.*

class Converters {
    @TypeConverter fun fromList(list: List<String>): String = list.joinToString("|||")
    @TypeConverter fun toList(str: String): List<String> =
        if (str.isBlank()) emptyList() else str.split("|||")
}

@Database(
    entities = [
        MediaEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        UserProgressEntity::class,
        EpisodeWatchedEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WatchOrderDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun episodeWatchedDao(): EpisodeWatchedDao

    companion object {
        @Volatile private var INSTANCE: WatchOrderDatabase? = null

        fun getInstance(context: Context): WatchOrderDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WatchOrderDatabase::class.java,
                    "watchorder.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
