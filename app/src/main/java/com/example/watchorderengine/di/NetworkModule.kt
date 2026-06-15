package com.example.watchorderengine.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.watchorderengine.BuildConfig
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbAuthInterceptor
import com.example.watchorderengine.network.TmdbConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing the entire network stack as singletons.
 *
 * DEPENDENCY GRAPH:
 *   BuildConfig.TMDB_READ_ACCESS_TOKEN
 *       ↓
 *   TmdbAuthInterceptor
 *       ↓
 *   OkHttpClient  ←  HttpLoggingInterceptor  ←  BuildConfig.DEBUG
 *       ↓
 *   Retrofit  ←  Moshi
 *       ↓
 *   TmdbApiService
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * HTTP response cache stored on disk.
     * TMDB data doesn't change second-to-second, so 10MB of cache gives us
     * free offline resilience and massively reduces redundant API calls on
     * app restarts (TMDB returns Cache-Control headers we can honour).
     */
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024  // 10MB

    @Provides
    @Singleton
    fun provideTmdbAuthInterceptor(): TmdbAuthInterceptor {
        // Token sourced from BuildConfig (from local.properties — never hardcoded)
        val token = BuildConfig.TMDB_READ_ACCESS_TOKEN
        check(token.isNotBlank()) {
            "TMDB_READ_ACCESS_TOKEN is blank. Add it to local.properties: " +
            "TMDB_READ_ACCESS_TOKEN=your_token_here"
        }
        return TmdbAuthInterceptor(token)
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // BODY logging reveals request/response payloads — only in debug builds.
            // In release builds, NONE prevents sensitive data from appearing in logcat.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: TmdbAuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        // Auth interceptor FIRST: the logged request will show the Authorization header
        .addInterceptor(authInterceptor)
        // Logging interceptor LAST: logs the full request as it goes out
        .addNetworkInterceptor(loggingInterceptor)
        // Disk cache: respects TMDB's Cache-Control headers (typically 1 day)
        .cache(Cache(File(context.cacheDir, "tmdb_http_cache"), CACHE_SIZE_BYTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        /**
         * KotlinJsonAdapterFactory enables reflection-based serialization for
         * @JsonClass(generateAdapter = true) annotated classes.
         *
         * FOR PRODUCTION: Remove this and use Moshi's code generation exclusively:
         *   ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
         * This eliminates reflection overhead and catches JSON mapping errors at
         * compile time. Requires all serialized classes to have @JsonClass annotation.
         */
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(TmdbConfig.BASE_URL)  // "https://api.themoviedb.org/3/"
            .client(okHttpClient)
            // MoshiConverterFactory handles JSON ↔ data class serialization
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideTmdbApiService(retrofit: Retrofit): TmdbApiService =
        retrofit.create(TmdbApiService::class.java)
}
