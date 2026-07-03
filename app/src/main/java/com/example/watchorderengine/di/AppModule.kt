package com.example.watchorderengine.di

import android.content.Context
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        return firestore
    }

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WatchOrderDatabase =
        WatchOrderDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository =
        UserPreferencesRepository(context)

    @Provides
    fun provideReviewDao(db: WatchOrderDatabase): com.example.watchorderengine.data.db.dao.ReviewDao =
        db.reviewDao()

    @Provides
    fun provideMediaDao(db: WatchOrderDatabase): com.example.watchorderengine.data.db.dao.MediaDao =
        db.mediaDao()

    @Provides
    @Singleton
    fun provideAnimeListImportRepository(
        anilistApi: com.example.watchorderengine.network.AnilistApiService,
        tmdbApi: com.example.watchorderengine.network.TmdbApiService,
        db: WatchOrderDatabase
    ): com.example.watchorderengine.data.import_list.AnimeListImportRepository =
        com.example.watchorderengine.data.import_list.AnimeListImportRepository(anilistApi, tmdbApi, db)
}
